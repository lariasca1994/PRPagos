package com.prpagos.conexion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.sun.jna.platform.win32.Crypt32Util;

/**
 * Protege los secretos de config.properties con DPAPI de Windows (el mismo
 * mecanismo con el que Chrome y Edge guardan las contraseñas): quedan
 * cifrados con una clave atada a tu usuario de Windows en este equipo. Si
 * alguien copia el archivo a otra máquina, o lo pega en un chat o una IA, solo
 * ve "dpapi:..." sin forma de descifrarlo.
 *
 * La primera vez que la aplicación lee el archivo cifra los valores que estén
 * en texto plano y lo reescribe (los comentarios se conservan). En otros
 * sistemas operativos los valores se dejan como están.
 */
final class SecretosProtegidos {

    static final Set<String> SECRETOS = Set.of("db.password", "totp.clave", "brevo.api.key");
    private static final String PREFIJO = "dpapi:";

    private SecretosProtegidos() {
    }

    static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /** Descifra en memoria los valores "dpapi:..." y cifra en el archivo los que estén en claro. */
    static void proteger(Path archivo, Properties props) throws IOException {
        boolean hayEnClaro = false;
        for (String clave : SECRETOS) {
            String valor = props.getProperty(clave);
            if (valor == null || valor.isBlank()) {
                continue;
            }
            if (valor.startsWith(PREFIJO)) {
                props.setProperty(clave, descifrar(valor));
            } else {
                hayEnClaro = true;
            }
        }
        if (hayEnClaro && esWindows()) {
            reescribirCifrado(archivo, props);
        }
    }

    private static String descifrar(String valor) throws IOException {
        if (!esWindows()) {
            throw new IOException("config.properties tiene secretos protegidos con DPAPI: solo se pueden "
                    + "leer en el equipo y el usuario de Windows que los cifró.");
        }
        try {
            byte[] datos = Base64.getDecoder().decode(valor.substring(PREFIJO.length()));
            return new String(Crypt32Util.cryptUnprotectData(datos), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IOException("No se pudieron descifrar los secretos de config.properties (¿se copió desde otro "
                    + "equipo o usuario?). Vuelve a escribir los valores en claro y la aplicación los protegerá.", e);
        }
    }

    private static String cifrar(String texto) {
        return PREFIJO + Base64.getEncoder().encodeToString(
                Crypt32Util.cryptProtectData(texto.getBytes(StandardCharsets.UTF_8)));
    }

    /** Reemplaza solo las líneas clave=valor de los secretos en claro; el resto del archivo queda igual. */
    private static void reescribirCifrado(Path archivo, Properties props) throws IOException {
        List<String> salida = new ArrayList<>();
        for (String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            String limpia = linea.strip();
            int igual = limpia.indexOf('=');
            String clave = igual > 0 ? limpia.substring(0, igual).strip() : "";
            String valor = igual > 0 ? limpia.substring(igual + 1).strip() : "";
            if (!limpia.startsWith("#") && SECRETOS.contains(clave) && !valor.isEmpty() && !valor.startsWith(PREFIJO)) {
                salida.add(clave + "=" + cifrar(props.getProperty(clave)));
            } else {
                salida.add(linea);
            }
        }
        Files.write(archivo, salida, StandardCharsets.UTF_8);
    }
}
