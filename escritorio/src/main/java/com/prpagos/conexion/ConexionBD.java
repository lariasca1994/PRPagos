package com.prpagos.conexion;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Punto único de conexión a la base de datos.
 *
 * CORRECCIÓN respecto a la versión anterior: las 6 clases de la interfaz
 * (VentanaLogin, VentanaCrearUsuario, VentanaConsultaPagos, etc.) tenían
 * cada una su propia copia de la cadena de conexión, con el usuario y la
 * contraseña de la base escritos directamente en el código. Ahora hay un
 * solo lugar (esta clase), que lee la configuración de config.properties
 * (un archivo que nunca se sube a git) y se conecta usando el Wallet de
 * Oracle Cloud en vez de "localhost".
 */
public final class ConexionBD {

    private static Properties config;

    static {
        // CORRECCIÓN: fuerza el registro del driver de Oracle ante
        // DriverManager. Normalmente esto pasa solo (JDBC 4+ lo detecta
        // automáticamente vía META-INF/services), pero si el classpath no
        // quedó bien armado en el IDE, sale "No suitable driver found"
        // aunque el .jar sí esté ahí. Cargarlo a mano aquí lo garantiza.
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                "No se encontró oracle.jdbc.OracleDriver. Verifica que ojdbc17.jar "
                + "esté agregado como Referenced Library en VS Code.");
        }
    }

    private ConexionBD() {
    }

    /**
     * Dónde está config.properties: primero en la carpeta de trabajo (al correr
     * desde el código, en escritorio/) y luego en %APPDATA%\PRPagos\ (la
     * aplicación instalada). Con -Dprpagos.config=ruta se puede indicar otro.
     */
    static Path ubicarConfig() {
        List<Path> candidatos = new ArrayList<>();
        String indicado = System.getProperty("prpagos.config");
        if (indicado != null && !indicado.isBlank()) {
            candidatos.add(Path.of(indicado));
        }
        candidatos.add(Path.of("config.properties").toAbsolutePath());
        String appData = System.getenv("APPDATA");
        candidatos.add(appData != null
                ? Path.of(appData, "PRPagos", "config.properties")
                : Path.of(System.getProperty("user.home"), ".prpagos", "config.properties"));
        for (Path p : candidatos) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return candidatos.get(candidatos.size() - 1);
    }

    private static synchronized Properties cargarConfig() throws IOException {
        if (config != null) {
            return config;
        }
        Path archivo = ubicarConfig();
        Properties props = new Properties();
        try (Reader in = new java.io.StringReader(
                // El Bloc de notas puede agregar una marca BOM al inicio: se ignora.
                Files.readString(archivo, StandardCharsets.UTF_8).replace("\uFEFF", ""))) {
            props.load(in);
        } catch (IOException e) {
            throw new IOException(
                "No se encontró la configuración. Copia config.properties y la carpeta wallet en "
                + archivo.getParent() + " (ver el manual del escritorio, sección Instalar).", e);
        }
        // Secretos cifrados con DPAPI (Windows): se descifran en memoria y, si estaban en claro,
        // se cifran en el archivo.
        SecretosProtegidos.proteger(archivo, props);
        props.setProperty("wallet.path", ubicarWallet(archivo, props.getProperty("wallet.path")).toString());
        config = props;
        return config;
    }

    /**
     * Carpeta del Wallet de Oracle: wallet.path (absoluto o relativo a la carpeta
     * de config.properties) o, si no existe, la carpeta "wallet" junto al archivo
     * (así basta copiar las dos cosas juntas a %APPDATA%\PRPagos en otro equipo).
     */
    static Path ubicarWallet(Path archivo, String walletPath) throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        List<Path> candidatos = new ArrayList<>();
        if (walletPath != null && !walletPath.isBlank()) {
            Path indicado = Path.of(walletPath.trim());
            candidatos.add(indicado.isAbsolute() ? indicado : carpeta.resolve(indicado).normalize());
        }
        candidatos.add(carpeta.resolve("wallet"));
        for (Path p : candidatos) {
            if (Files.isRegularFile(p.resolve("tnsnames.ora"))) {
                return p;
            }
        }
        throw new IOException("No se encontró la billetera de Oracle (la carpeta wallet, con tnsnames.ora). "
                + "Cópiala junto a config.properties, en " + carpeta.resolve("wallet") + ".");
    }

    /** Cualquier valor de config.properties (por ejemplo totp.clave). */
    public static String propiedad(String nombre) throws IOException {
        return cargarConfig().getProperty(nombre);
    }

    public static Connection obtenerConexion() throws SQLException {
        try {
            Properties props = cargarConfig();
            String walletPath = props.getProperty("wallet.path");
            String dbAlias = props.getProperty("db.alias");
            String usuario = props.getProperty("db.usuario");
            String password = props.getProperty("db.password");

            // Le indica al driver JDBC dónde está el Wallet (certificados
            // de conexión segura) descargado desde Oracle Cloud.
            System.setProperty("oracle.net.tns_admin", walletPath);

            String url = "jdbc:oracle:thin:@" + dbAlias;
            return DriverManager.getConnection(url, usuario, password);
        } catch (IOException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }
}
