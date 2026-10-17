package com.prpagos.compartido;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Acceso a compartido/: las consultas SQL y las plantillas de correo que usan
 * IGUAL la web (web/app/compartido.py) y el escritorio. Una sola fuente: los
 * mismos números y los mismos correos.
 *
 * En el ejecutable empaquetado la carpeta viaja dentro del .jar (recurso
 * /compartido/...); al correr desde el código se busca hacia arriba desde la
 * carpeta de trabajo (escritorio/ -> ../compartido).
 */
public final class Compartido {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{([a-z_]+)\\}\\}");

    private Compartido() {
    }

    /** SQL de compartido/consultas/&lt;nombre&gt;.sql sin los comentarios de línea. */
    public static String consulta(String nombre) {
        return CACHE.computeIfAbsent("consultas/" + nombre + ".sql", ruta -> {
            StringBuilder sb = new StringBuilder();
            for (String linea : leer(ruta).split("\n")) {
                if (!linea.stripLeading().startsWith("--")) {
                    sb.append(linea).append('\n');
                }
            }
            return sb.toString().strip();
        });
    }

    public static String plantilla(String nombre) {
        return CACHE.computeIfAbsent("correo/" + nombre, Compartido::leer);
    }

    /**
     * Reemplaza {{clave}}. Los valores se escapan como HTML salvo las claves de
     * crudos (filas y bloques ya armados). Una clave sin valor es un error.
     */
    public static String rellenar(String texto, Map<String, String> valores, Set<String> crudos) {
        Matcher m = MARCADOR.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String clave = m.group(1);
            if (!valores.containsKey(clave)) {
                throw new IllegalArgumentException("Falta el valor de {{" + clave + "}}");
            }
            String valor = valores.get(clave);
            m.appendReplacement(sb, Matcher.quoteReplacement(crudos.contains(clave) ? valor : escapar(valor)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Igual que html.escape(texto, quote=True) de Python. */
    public static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#x27;");
    }

    private static String leer(String ruta) {
        try (InputStream in = Compartido.class.getResourceAsStream("/compartido/" + ruta)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Path carpeta = Path.of("").toAbsolutePath();
        while (carpeta != null) {
            Path candidato = carpeta.resolve("compartido").resolve(ruta);
            if (Files.isRegularFile(candidato)) {
                try {
                    return Files.readString(candidato, StandardCharsets.UTF_8).replace("\r\n", "\n");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            carpeta = carpeta.getParent();
        }
        throw new IllegalStateException("No se encontró compartido/" + ruta);
    }
}
