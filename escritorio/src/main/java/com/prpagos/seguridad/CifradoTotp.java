package com.prpagos.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.prpagos.conexion.ConexionBD;

/**
 * Cifra el secreto TOTP antes de guardarlo en la base (AES-256-GCM).
 * Formato idéntico al de la web: "v1:" + base64(nonce 12 bytes + texto
 * cifrado + etiqueta 16 bytes). La clave es totp.clave de config.properties
 * y debe ser la MISMA que TOTP_CLAVE de la web.
 */
public final class CifradoTotp {

    private static final String PREFIJO = "v1:";
    private static final int BYTES_NONCE = 12;
    private static final int BITS_ETIQUETA = 128;

    private CifradoTotp() {
    }

    public static String cifrar(String secreto) {
        byte[] nonce = new byte[BYTES_NONCE];
        new SecureRandom().nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave(), new GCMParameterSpec(BITS_ETIQUETA, nonce));
            byte[] cifrado = cipher.doFinal(secreto.getBytes(StandardCharsets.US_ASCII));
            byte[] todo = Arrays.copyOf(nonce, nonce.length + cifrado.length);
            System.arraycopy(cifrado, 0, todo, nonce.length, cifrado.length);
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el secreto TOTP", e);
        }
    }

    /** Null si el valor está vacío, alterado o cifrado con otra clave. */
    public static String descifrarONull(String valor) {
        if (valor == null || !valor.startsWith(PREFIJO)) {
            return null;
        }
        try {
            byte[] crudo = Base64.getDecoder().decode(valor.substring(PREFIJO.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave(),
                    new GCMParameterSpec(BITS_ETIQUETA, crudo, 0, BYTES_NONCE));
            byte[] plano = cipher.doFinal(crudo, BYTES_NONCE, crudo.length - BYTES_NONCE);
            return new String(plano, StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private static SecretKeySpec clave() {
        String base64;
        try {
            base64 = ConexionBD.propiedad("totp.clave");
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        byte[] bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64.trim());
        if (bytes.length != 32) {
            throw new IllegalStateException(
                "Falta totp.clave en config.properties (32 bytes en base64, la misma TOTP_CLAVE de la web).");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
