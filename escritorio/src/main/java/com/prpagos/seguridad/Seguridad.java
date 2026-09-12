package com.prpagos.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * CORRECCIÓN de seguridad respecto a la versión anterior: las contraseñas
 * se guardaban y se comparaban en texto plano (tanto en el procedimiento
 * validar_login como en crear/recuperar contraseña). Aquí se cifran con
 * SHA-256 antes de guardarlas o compararlas — nunca viaja ni se guarda la
 * contraseña real.
 *
 * Nota honesta: SHA-256 simple es mejor que texto plano, pero para un
 * sistema en producción real lo correcto sería bcrypt o PBKDF2 (con "salt"
 * y factor de costo ajustable). Se usa SHA-256 aquí para no depender de
 * una librería externa adicional en un proyecto de escritorio con Swing;
 * queda documentado como una simplificación consciente, no un descuido.
 */
public final class Seguridad {

    private Seguridad() {
    }

    public static String hashPassword(String textoPlano) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(textoPlano.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en cualquier JVM estándar.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
