package com.prpagos.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Contraseñas y validación de correo. Gemelo exacto de web/app/seguridad.py:
 * una cuenta creada o una contraseña cambiada en una interfaz tiene que
 * funcionar en la otra, así que el formato no puede diferir.
 *
 * Contraseñas: PBKDF2-HMAC-SHA256, sal aleatoria de 16 bytes y 600.000
 * iteraciones (recomendación de OWASP). Se guarda como
 *     pbkdf2_sha256$&lt;iteraciones&gt;$&lt;sal base64&gt;$&lt;hash base64&gt;
 * Viene incluido en la JVM: no hace falta ninguna librería externa.
 *
 * Las cuentas anteriores tienen un SHA-256 simple (64 caracteres hex). Se
 * aceptan una sola vez: al ingresar con la contraseña correcta se vuelven a
 * guardar con PBKDF2 (ver necesitaRehash).
 */
public final class Seguridad {

    private static final String ALGORITMO = "pbkdf2_sha256";
    public static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BYTES_HASH = 32;
    public static final int LONGITUD_MINIMA = 8;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Seguridad() {
    }

    public static String hashPassword(String textoPlano) {
        return hashPassword(textoPlano, ITERACIONES);
    }

    static String hashPassword(String textoPlano, int iteraciones) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] derivado = pbkdf2(textoPlano, sal, iteraciones, BYTES_HASH);
        Base64.Encoder b64 = Base64.getEncoder();
        return ALGORITMO + "$" + iteraciones + "$" + b64.encodeToString(sal) + "$"
                + b64.encodeToString(derivado);
    }

    public static boolean verificarPassword(String textoPlano, String guardado) {
        if (guardado == null || guardado.isEmpty()) {
            return false;
        }
        if (guardado.startsWith(ALGORITMO + "$")) {
            String[] partes = guardado.split("\\$");
            if (partes.length != 4) {
                return false;
            }
            try {
                int iteraciones = Integer.parseInt(partes[1]);
                byte[] sal = Base64.getDecoder().decode(partes[2]);
                byte[] esperado = Base64.getDecoder().decode(partes[3]);
                byte[] derivado = pbkdf2(textoPlano, sal, iteraciones, esperado.length);
                return MessageDigest.isEqual(derivado, esperado);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (esSha256Heredado(guardado)) {
            byte[] heredado = sha256Hex(textoPlano).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(heredado, guardado.getBytes(StandardCharsets.US_ASCII));
        }
        return false;
    }

    /** True si es el SHA-256 de antes o PBKDF2 con menos iteraciones que las actuales. */
    public static boolean necesitaRehash(String guardado) {
        if (guardado == null || !guardado.startsWith(ALGORITMO + "$")) {
            return true;
        }
        try {
            return Integer.parseInt(guardado.split("\\$")[1]) < ITERACIONES;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Motivo del rechazo de una contraseña nueva, o null si es aceptable. */
    public static String problemaContrasena(String textoPlano) {
        if (textoPlano == null || textoPlano.length() < LONGITUD_MINIMA) {
            return "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres";
        }
        return null;
    }

    /**
     * Cualquier correo real (las notificaciones y la recuperación llegan ahí).
     * Valida la forma algo@dominio.tld, no que exista.
     */
    public static boolean correoValido(String correo) {
        if (correo == null || correo.isEmpty() || correo.length() > 100) {
            return false;
        }
        for (int i = 0; i < correo.length(); i++) {
            if (Character.isWhitespace(correo.charAt(i))) {
                return false;
            }
        }
        int arroba = correo.lastIndexOf('@');
        if (arroba <= 0) {
            return false;
        }
        String local = correo.substring(0, arroba);
        String dominio = correo.substring(arroba + 1);
        if (local.indexOf('@') >= 0) {
            return false;
        }
        String[] partes = dominio.split("\\.", -1);
        if (partes.length < 2) {
            return false;
        }
        for (String parte : partes) {
            if (parte.isEmpty()) {
                return false;
            }
        }
        return partes[partes.length - 1].length() >= 2;
    }

    private static boolean esSha256Heredado(String guardado) {
        return guardado.length() == 64 && guardado.chars().allMatch(c -> Character.digit(c, 16) >= 0
                && !Character.isUpperCase(c));
    }

    private static byte[] pbkdf2(String textoPlano, byte[] sal, int iteraciones, int bytes) {
        PBEKeySpec spec = new PBEKeySpec(textoPlano.toCharArray(), sal, iteraciones, bytes * 8);
        try {
            // SunJCE codifica la contraseña en UTF-8, igual que hashlib en la web.
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 no disponible en esta JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String sha256Hex(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
