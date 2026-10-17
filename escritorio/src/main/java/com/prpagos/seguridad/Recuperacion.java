package com.prpagos.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recuperación si se pierde el teléfono: códigos de respaldo de un solo uso y
 * el código de 6 dígitos que se envía al correo. Gemelo de la sección
 * "Recuperación" de web/app/seguridad.py (mismo alfabeto, formato y hash).
 */
public final class Recuperacion {

    public static final int CANTIDAD_RESPALDO = 10;
    public static final int MINUTOS_CODIGO_CORREO = 15;
    /** Sin 0/O ni 1/I/L para que no se confundan al copiarlos a mano. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Recuperacion() {
    }

    /** 10 códigos con forma ABCD-EFGH. */
    public static List<String> generarCodigosRespaldo() {
        List<String> codigos = new ArrayList<>();
        for (int n = 0; n < CANTIDAD_RESPALDO; n++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i == 4) {
                    sb.append('-');
                }
                sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
            codigos.add(sb.toString());
        }
        return codigos;
    }

    /** "abcd efgh" o "ABCD-EFGH" -> "ABCDEFGH"; null si no tiene esa forma (un TOTP de 6 dígitos no la tiene). */
    public static String normalizarRespaldo(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder limpio = new StringBuilder();
        for (char c : texto.toUpperCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                limpio.append(c);
            }
        }
        if (limpio.length() != 8) {
            return null;
        }
        for (int i = 0; i < 8; i++) {
            if (ALFABETO.indexOf(limpio.charAt(i)) < 0) {
                return null;
            }
        }
        return limpio.toString();
    }

    /** SHA-256 en hex de un código de un solo uso (igual que hash_corto en la web). */
    public static String hashCorto(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generarCodigoCorreo() {
        return String.format("%06d", ALEATORIO.nextInt(1_000_000));
    }
}
