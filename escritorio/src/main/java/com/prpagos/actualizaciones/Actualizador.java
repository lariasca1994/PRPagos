package com.prpagos.actualizaciones;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.prpagos.conexion.ConexionBD;

/**
 * Actualización automática de la aplicación de escritorio, con las mismas
 * reglas que los agentes de SysPulse:
 * <ol>
 * <li>lee en la base (TBVersionesEscritorio) la última versión publicada: URL
 * del Release de GitHub y huella SHA-256. La huella viene de la base, no del
 * mismo sitio de la descarga;</li>
 * <li>si es más nueva que la instalada, descarga el paquete solo por HTTPS y
 * solo desde GitHub (también el destino final tras las redirecciones), con
 * tamaño máximo, y verifica la huella y que el .jar diga la misma versión;</li>
 * <li>al cerrar la aplicación (o al reiniciarla desde el aviso) un script
 * reemplaza la carpeta instalada y deja la anterior de respaldo hasta que la
 * nueva queda en su lugar.</li>
 * </ol>
 * Si algo falla, la versión instalada sigue funcionando y se reintenta en la
 * próxima comprobación. Solo aplica a la aplicación empaquetada (PRPagos.exe);
 * al correr desde el código no hace nada.
 */
public final class Actualizador {

    public static final long TAMANO_MAXIMO = 250L * 1024 * 1024;
    private static final long MAX_LOG = 1024 * 1024;
    private static final Set<String> HOSTS_PERMITIDOS = Set.of(
            "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final String JAR = "prpagos-escritorio.jar";
    private static final String EXE = "PRPagos.exe";

    /** Versión publicada en la base. */
    public record Paquete(String version, String url, String sha256) {
    }

    private Actualizador() {
    }

    // ───────────────────────── Reglas (probadas en PruebaActualizador) ─────────────────────────

    /** ¿La versión publicada es más nueva que la instalada? Ante un formato desconocido, no actualiza. */
    public static boolean esMasNueva(String disponible, String instalada) {
        int[] nueva = partes(disponible);
        if (nueva == null) {
            return false;
        }
        int[] actual = partes(instalada);
        if (actual == null) {
            return true;
        }
        for (int i = 0; i < 3; i++) {
            if (nueva[i] != actual[i]) {
                return nueva[i] > actual[i];
            }
        }
        return false;
    }

    private static int[] partes(String version) {
        if (version == null || !version.strip().matches("\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}")) {
            return null;
        }
        String[] p = version.strip().split("\\.");
        return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }

    public static boolean urlPermitida(URI url) {
        return url != null && "https".equals(url.getScheme()) && url.getHost() != null
                && HOSTS_PERMITIDOS.contains(url.getHost().toLowerCase());
    }

    public static String huella(Path archivo) throws IOException {
        try (InputStream in = Files.newInputStream(archivo)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[81920];
            int leidos;
            while ((leidos = in.read(buffer)) > 0) {
                sha.update(buffer, 0, leidos);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ───────────────────────── Estado de la instalación ─────────────────────────

    /** Versión de este .jar (Implementation-Version del manifiesto); "0.0.0" desde el código. */
    public static String versionInstalada() {
        String v = Actualizador.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.0.0";
    }

    /** Carpeta de PRPagos.exe, o null si no se está ejecutando la aplicación empaquetada. */
    public static Path carpetaInstalada() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe == null || exe.isBlank()) {
            return null;
        }
        Path carpeta = Path.of(exe).toAbsolutePath().getParent();
        return Files.isRegularFile(carpeta.resolve(EXE)) ? carpeta : null;
    }

    private static Path carpetaTrabajo() {
        return Path.of(System.getProperty("java.io.tmpdir"), "PRPagos-actualizacion");
    }

    // ───────────────────────── Comprobar, descargar y verificar ─────────────────────────

    public static Paquete ultimaPublicada() throws SQLException {
        String sql = "SELECT version, url, sha256 FROM TBVersionesEscritorio "
                + "ORDER BY publicada DESC FETCH FIRST 1 ROWS ONLY";
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? new Paquete(rs.getString(1), rs.getString(2), rs.getString(3)) : null;
        }
    }

    /**
     * Si hay una versión nueva, la descarga y verifica, y devuelve la carpeta
     * lista para instalar (con PRPagos.exe). Null si no hay nada que hacer.
     */
    public static Path prepararSiHayNueva() {
        if (carpetaInstalada() == null) {
            return null;
        }
        String instalada = versionInstalada();
        try {
            Paquete paquete = ultimaPublicada();
            if (paquete == null || !esMasNueva(paquete.version(), instalada)) {
                return null;
            }
            registrar("Nueva versión " + paquete.version() + " (instalada " + instalada + "): descargando.");
            Path trabajo = carpetaTrabajo();
            borrar(trabajo);
            Path zip = trabajo.resolve("paquete.zip");
            descargar(paquete, zip);
            Path destino = trabajo.resolve("nueva");
            extraer(zip, destino);
            Path carpeta = destino.resolve("PRPagos");
            if (!Files.isRegularFile(carpeta.resolve(EXE))) {
                throw new IOException("El paquete no contiene " + EXE + ": se descarta.");
            }
            String versionPaquete;
            try (JarFile jar = new JarFile(carpeta.resolve("app").resolve(JAR).toFile())) {
                versionPaquete = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
            }
            if (!paquete.version().equals(versionPaquete)) {
                throw new IOException("El paquete dice " + versionPaquete + " pero se publicó como "
                        + paquete.version() + ": se descarta.");
            }
            Files.deleteIfExists(zip);
            registrar("Versión " + paquete.version() + " descargada y verificada: se instala al cerrar.");
            return carpeta;
        } catch (IOException | SQLException | RuntimeException e) {
            registrar("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " (se reintenta en la próxima comprobación).");
            borrar(carpetaTrabajo());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Descarga el paquete y verifica origen, tamaño y huella. Lanza si algo falla (y borra el archivo). */
    public static void descargar(Paquete paquete, Path destino) throws IOException, InterruptedException {
        URI url = URI.create(paquete.url());
        if (!urlPermitida(url)) {
            throw new IOException("La URL de la actualización no es de GitHub Releases por HTTPS.");
        }
        Files.createDirectories(destino.getParent());
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest pedido = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(15)).GET().build();
        try {
            HttpResponse<InputStream> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = respuesta.body()) {
                if (respuesta.statusCode() != 200) {
                    throw new IOException("La descarga respondió " + respuesta.statusCode() + ".");
                }
                if (!urlPermitida(respuesta.uri())) {
                    throw new IOException("La descarga terminó en un sitio que no es de GitHub.");
                }
                long declarado = respuesta.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declarado > TAMANO_MAXIMO) {
                    throw new IOException("El paquete supera el tamaño máximo permitido.");
                }
                try (OutputStream out = Files.newOutputStream(destino)) {
                    byte[] buffer = new byte[81920];
                    long total = 0;
                    int leidos;
                    while ((leidos = in.read(buffer)) > 0) {
                        total += leidos;
                        if (total > TAMANO_MAXIMO) {
                            throw new IOException("El paquete supera el tamaño máximo permitido.");
                        }
                        out.write(buffer, 0, leidos);
                    }
                }
            }
            if (!huella(destino).equalsIgnoreCase(paquete.sha256().strip())) {
                throw new IOException("La huella SHA-256 no coincide: el archivo descargado no es el publicado.");
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(destino);
            throw e;
        }
    }

    /** Descomprime sin permitir rutas que salgan de la carpeta destino (zip slip). */
    static void extraer(Path zip, Path destino) throws IOException {
        Path base = destino.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entrada;
            while ((entrada = in.getNextEntry()) != null) {
                Path ruta = base.resolve(entrada.getName().replace('\\', '/')).normalize();
                if (!ruta.startsWith(base)) {
                    throw new IOException("Ruta no permitida en el paquete: " + entrada.getName());
                }
                if (entrada.isDirectory()) {
                    Files.createDirectories(ruta);
                } else {
                    Files.createDirectories(ruta.getParent());
                    Files.copy(in, ruta);
                }
            }
        }
    }

    // ───────────────────────── Instalar ─────────────────────────

    /**
     * Lanza el script que espera a que esta aplicación se cierre, reemplaza la
     * carpeta instalada por {@code nueva} y, si {@code abrir}, vuelve a abrirla.
     * Quien llama debe cerrar la aplicación después.
     */
    public static void instalarAlCerrar(Path nueva, boolean abrir) throws IOException {
        Path instalada = carpetaInstalada();
        if (instalada == null) {
            return;
        }
        Path script = carpetaTrabajo().resolve("instalar.ps1");
        try (InputStream in = Actualizador.class.getResourceAsStream("instalar.ps1")) {
            if (in == null) {
                throw new IOException("Falta el script instalar.ps1 en el paquete.");
            }
            Files.copy(in, script, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.toString(),
                "-Proceso", String.valueOf(ProcessHandle.current().pid()),
                "-Instalada", instalada.toString(),
                "-Nueva", nueva.toString(),
                "-Log", archivoLog().toString(),
                "-Abrir", abrir ? "1" : "0")
                // Fuera de la carpeta instalada: si el script corriera ahí, Windows no dejaría renombrarla.
                .directory(carpetaTrabajo().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        registrar("Instalación programada al cerrar (" + instalada + ").");
    }

    // ───────────────────────── Registro ─────────────────────────

    private static Path archivoLog() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null ? Path.of(appData, "PRPagos") : Path.of(System.getProperty("user.home"), ".prpagos");
        return base.resolve("logs").resolve("actualizador.log");
    }

    /** Escribe en el log (con un tope de 1 MB); sin log la actualización no se detiene. */
    static void registrar(String mensaje) {
        String linea = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + " " + mensaje + System.lineSeparator();
        System.err.print(linea);
        try {
            Path log = archivoLog();
            Files.createDirectories(log.getParent());
            if (Files.exists(log) && Files.size(log) > MAX_LOG) {
                Files.delete(log);
            }
            Files.writeString(log, linea, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Sin log no se detiene la actualización.
        }
    }

    private static void borrar(Path carpeta) {
        if (!Files.exists(carpeta)) {
            return;
        }
        try (var rutas = Files.walk(carpeta)) {
            rutas.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            // Se intenta en la próxima comprobación.
        }
    }
}
