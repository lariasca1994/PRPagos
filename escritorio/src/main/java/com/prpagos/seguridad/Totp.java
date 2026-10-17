package com.prpagos.seguridad;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verificación en dos pasos (RFC 6238): HMAC-SHA1, 6 dígitos, pasos de 30
 * segundos y ±1 paso de tolerancia. Gemelo de la parte TOTP de
 * web/app/seguridad.py. Un código ya usado no vale de nuevo: solo se acepta
 * un paso posterior al último guardado en la base.
 */
public final class Totp {

    public static final String EMISOR = "PRPagos";
    private static final int DIGITOS = 6;
    private static final int PERIODO = 30;
    private static final int VENTANA = 1;
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** 160 bits aleatorios en base32 sin relleno (32 caracteres). */
    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return base32(bytes);
    }

    public static long pasoActual() {
        return System.currentTimeMillis() / 1000L / PERIODO;
    }

    public static String codigo(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(desdeBase32(secreto), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int desplazamiento = digest[digest.length - 1] & 0x0F;
            int valor = ((digest[desplazamiento] & 0x7F) << 24)
                    | ((digest[desplazamiento + 1] & 0xFF) << 16)
                    | ((digest[desplazamiento + 2] & 0xFF) << 8)
                    | (digest[desplazamiento + 3] & 0xFF);
            return String.format("%0" + DIGITOS + "d", valor % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible en esta JVM", e);
        }
    }

    /**
     * Devuelve el paso que coincidió (para guardarlo como último usado) o
     * null si el código no es válido o ya se usó.
     */
    public static Long verificar(String secreto, String codigo, long ultimoPaso) {
        if (secreto == null || codigo == null) {
            return null;
        }
        String limpio = codigo.trim().replace(" ", "");
        if (limpio.length() != DIGITOS || !limpio.chars().allMatch(Character::isDigit)) {
            return null;
        }
        long actual = pasoActual();
        for (long paso = actual - VENTANA; paso <= actual + VENTANA; paso++) {
            if (paso <= ultimoPaso) {
                continue;
            }
            byte[] esperado = codigo(secreto, paso).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(esperado, limpio.getBytes(StandardCharsets.US_ASCII))) {
                return paso;
            }
        }
        return null;
    }

    public static String uri(String secreto, String cuenta) {
        String etiqueta = codificar(EMISOR + ":" + cuenta);
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto + "&issuer=" + codificar(EMISOR)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO;
    }

    /** El secreto en grupos de 4 para copiarlo a mano. */
    public static String legible(String secreto) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secreto.length(); i += 4) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(secreto, i, Math.min(i + 4, secreto.length()));
        }
        return sb.toString();
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALFABETO.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALFABETO.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    static byte[] desdeBase32(String texto) {
        String limpio = texto.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer salida = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(c);
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter base32 inválido: " + c);
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return salida.array();
    }
}
