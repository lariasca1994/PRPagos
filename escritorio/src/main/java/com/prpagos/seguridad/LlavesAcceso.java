package com.prpagos.seguridad;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import com.prpagos.finanzas.Resumenes;

/**
 * Llaves de acceso (passkeys / WebAuthn): gemelo de web/app/llaves.py. El
 * escritorio no tiene servidor, así que verifica aquí lo que en la web hace la
 * librería webauthn: que el autenticador firmó el reto con la llave guardada,
 * para este sitio y con verificación del usuario (Windows Hello: PIN, huella
 * o rostro).
 *
 * Mismo identificador de sitio (el dominio de la web), mismo id de usuario y
 * mismos algoritmos (ES256 y RS256) que la web: una llave creada en Edge sirve
 * aquí y al revés, en el mismo Windows. En la base solo se guarda la clave
 * pública COSE (base64url) y el contador de firmas.
 */
public final class LlavesAcceso {

    public static final String NOMBRE_SITIO = "PRPagos";
    public static final int TIEMPO_MS = 60_000;
    public static final long ES256 = -7;
    public static final long RS256 = -257;
    public static final int LONGITUD_NOMBRE = 60;

    // Banderas de authenticatorData (WebAuthn §6.1).
    private static final int PRESENCIA = 0x01;
    private static final int VERIFICADO = 0x04;
    private static final int DATOS_CREDENCIAL = 0x40;

    private static final SecureRandom AZAR = new SecureRandom();

    /** Mensaje para la persona (los mismos textos que la web). */
    public static class ErrorLlave extends Exception {
        private static final long serialVersionUID = 1L;

        public ErrorLlave(String mensaje) {
            super(mensaje);
        }
    }

    /** Llave verificada al registrarla. */
    public record Registrada(String credencialId, String clavePublica, long contador) {
    }

    private LlavesAcceso() {
    }

    public static String origen() {
        String url = Resumenes.urlPublica();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String idSitio() {
        String host = URI.create(origen()).getHost();
        return host == null ? "localhost" : host;
    }

    public static byte[] idUsuario(long usuarioId) {
        return String.valueOf(usuarioId).getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] reto() {
        byte[] b = new byte[32];
        AZAR.nextBytes(b);
        return b;
    }

    public static String b64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }

    public static byte[] deB64(String texto) {
        return Base64.getUrlDecoder().decode(texto);
    }

    /** "Nombre del dispositivo" sin espacios de más; null si queda vacío. */
    public static String nombreValido(String texto) {
        String limpio = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (limpio.length() > LONGITUD_NOMBRE) {
            limpio = limpio.substring(0, LONGITUD_NOMBRE);
        }
        return limpio.isEmpty() ? null : limpio;
    }

    /** clientDataJSON como lo arma un navegador (tipo webauthn.create o webauthn.get). */
    public static byte[] datosCliente(String tipo, byte[] reto) {
        String json = "{\"type\":\"" + tipo + "\",\"challenge\":\"" + b64(reto) + "\",\"origin\":\"" + origen()
                + "\",\"crossOrigin\":false}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    // ───────────────────────── Registrar ─────────────────────────

    /** Revisa authenticatorData de una llave nueva y extrae su id, su clave y su contador. */
    public static Registrada verificarRegistro(byte[] datosAutenticador) throws ErrorLlave {
        try {
            int banderas = revisarComun(datosAutenticador);
            if ((banderas & DATOS_CREDENCIAL) == 0 || datosAutenticador.length < 55) {
                throw new ErrorLlave("No se pudo verificar la llave de acceso; inténtalo de nuevo.");
            }
            long contador = contador(datosAutenticador);
            int pos = 37 + 16; // aaguid
            int largo = ((datosAutenticador[pos] & 0xFF) << 8) | (datosAutenticador[pos + 1] & 0xFF);
            pos += 2;
            byte[] credencial = Arrays.copyOfRange(datosAutenticador, pos, pos + largo);
            pos += largo;
            int[] fin = new int[1];
            Cbor.leer(datosAutenticador, pos, fin);
            byte[] cose = Arrays.copyOfRange(datosAutenticador, pos, fin[0]);
            clavePublica(cose); // solo ES256 o RS256
            return new Registrada(b64(credencial), b64(cose), contador);
        } catch (RuntimeException | GeneralSecurityException e) {
            throw new ErrorLlave("No se pudo verificar la llave de acceso; inténtalo de nuevo.");
        }
    }

    // ───────────────────────── Ingresar ─────────────────────────

    /** Verifica la firma de un ingreso; devuelve el contador nuevo. */
    public static long verificarIngreso(byte[] datosAutenticador, byte[] datosClienteJson, byte[] firma,
                                        String clavePublica, long contadorActual) throws ErrorLlave {
        try {
            revisarComun(datosAutenticador);
            byte[] firmado = concatenar(datosAutenticador, sha256(datosClienteJson));
            PublicKey clave = clavePublica(deB64(clavePublica));
            Signature verificador = Signature.getInstance(
                    "EC".equals(clave.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA");
            verificador.initVerify(clave);
            verificador.update(firmado);
            if (!verificador.verify(firma)) {
                throw new ErrorLlave("La llave de acceso no es válida.");
            }
            long nuevo = contador(datosAutenticador);
            // Un contador que no avanza indica una llave clonada (si el autenticador lo usa).
            if ((nuevo > 0 || contadorActual > 0) && nuevo <= contadorActual) {
                throw new ErrorLlave("La llave de acceso no es válida.");
            }
            return nuevo;
        } catch (RuntimeException | GeneralSecurityException e) {
            throw new ErrorLlave("La llave de acceso no es válida.");
        }
    }

    /** Sitio correcto, presencia y verificación del usuario. Devuelve las banderas. */
    private static int revisarComun(byte[] datos) throws ErrorLlave, GeneralSecurityException {
        if (datos == null || datos.length < 37) {
            throw new ErrorLlave("La llave de acceso no es válida.");
        }
        byte[] sitio = sha256(idSitio().getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(sitio, Arrays.copyOfRange(datos, 0, 32))) {
            throw new ErrorLlave("La llave de acceso no es válida.");
        }
        int banderas = datos[32] & 0xFF;
        if ((banderas & PRESENCIA) == 0 || (banderas & VERIFICADO) == 0) {
            throw new ErrorLlave("La llave de acceso no es válida.");
        }
        return banderas;
    }

    private static long contador(byte[] datos) {
        return ((long) (datos[33] & 0xFF) << 24) | ((datos[34] & 0xFF) << 16) | ((datos[35] & 0xFF) << 8)
                | (datos[36] & 0xFF);
    }

    /** Clave pública desde COSE: EC2 P-256 (ES256) o RSA (RS256). */
    static PublicKey clavePublica(byte[] cose) throws GeneralSecurityException {
        Object leido = Cbor.leer(cose, 0, null);
        if (!(leido instanceof Map<?, ?> mapa)) {
            throw new GeneralSecurityException("COSE inválido");
        }
        Object alg = mapa.get(3L);
        Object kty = mapa.get(1L);
        if (Long.valueOf(2).equals(kty) && Long.valueOf(ES256).equals(alg) && Long.valueOf(1).equals(mapa.get(-1L))) {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            ECPoint punto = new ECPoint(new BigInteger(1, (byte[]) mapa.get(-2L)), new BigInteger(1, (byte[]) mapa.get(-3L)));
            return KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(punto, p.getParameterSpec(ECParameterSpec.class)));
        }
        if (Long.valueOf(3).equals(kty) && Long.valueOf(RS256).equals(alg)) {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, (byte[]) mapa.get(-1L)), new BigInteger(1, (byte[]) mapa.get(-2L))));
        }
        throw new GeneralSecurityException("Algoritmo de llave no admitido");
    }

    private static byte[] sha256(byte[] datos) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(datos);
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
