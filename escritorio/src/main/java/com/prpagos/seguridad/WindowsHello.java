package com.prpagos.seguridad;

import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * Windows Hello desde Java: llama a webauthn.dll, la misma API de Windows que
 * usan Edge y Chrome para las llaves de acceso. Windows muestra su ventana
 * (PIN, huella o rostro) y devuelve los datos firmados; aquí solo se arman
 * como los arma un navegador y el SERVIDOR los verifica (la misma lógica que
 * para la web). Solo Windows 10 (1903) u 11 con Windows Hello configurado.
 *
 * Estructuras en su versión 1 (webauthn.h): Windows las acepta en todas las
 * versiones posteriores.
 */
public final class WindowsHello {

    private static final int PLATAFORMA = 1;                 // WEBAUTHN_AUTHENTICATOR_ATTACHMENT_PLATFORM
    private static final int VERIFICACION_REQUERIDA = 1;     // WEBAUTHN_USER_VERIFICATION_REQUIREMENT_REQUIRED
    private static final int SIN_ATESTACION = 1;             // WEBAUTHN_ATTESTATION_CONVEYANCE_PREFERENCE_NONE
    private static final int CANCELADO = 0x80090036;         // NTE_USER_CANCELLED
    private static final int YA_EXISTE = 0x8009000F;         // NTE_EXISTS
    private static final int NO_ENCONTRADA = 0x80090011;     // NTE_NOT_FOUND
    private static final int TIEMPO_AGOTADO = 0x800705B4;    // HRESULT_FROM_WIN32(ERROR_TIMEOUT)

    /** Lo que devuelve Windows al ingresar con una llave. */
    public record Asercion(byte[] credencialId, byte[] datosAutenticador, byte[] firma, byte[] idUsuario) {
    }

    /** Lo que devuelve Windows al crear una llave. */
    public record Creada(byte[] credencialId, byte[] objetoAtestacion) {
    }

    /** Mensaje para la persona (los mismos textos que la web). */
    public static class ErrorLlave extends Exception {
        private static final long serialVersionUID = 1L;

        public ErrorLlave(String mensaje) {
            super(mensaje);
        }
    }

    private static final int TIEMPO_MS = 60_000;
    private static final int ES256 = -7;
    private static final int RS256 = -257;

    private WindowsHello() {
    }

    // ───────────────────────── API nativa ─────────────────────────

    interface Api extends Library {
        int WebAuthNGetApiVersionNumber();

        int WebAuthNIsUserVerifyingPlatformAuthenticatorAvailable(IntByReference disponible);

        int WebAuthNAuthenticatorMakeCredential(HWND ventana, Sitio sitio, Usuario usuario, ListaAlgoritmos algoritmos,
                                                DatosCliente datos, OpcionesCrear opciones, PointerByReference resultado);

        int WebAuthNAuthenticatorGetAssertion(HWND ventana, WString sitio, DatosCliente datos, OpcionesObtener opciones,
                                              PointerByReference resultado);

        void WebAuthNFreeCredentialAttestation(Pointer atestacion);

        void WebAuthNFreeAssertion(Pointer asercion);
    }

    private static Api api;
    private static boolean cargada;

    private static synchronized Api api() {
        if (!cargada) {
            cargada = true;
            try {
                if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
                    api = Native.load("webauthn", Api.class);
                }
            } catch (UnsatisfiedLinkError e) {
                api = null;
            }
        }
        return api;
    }

    /** ¿Hay Windows Hello listo para usar en este equipo? */
    public static boolean disponible() {
        Api a = api();
        if (a == null) {
            return false;
        }
        try {
            IntByReference disponible = new IntByReference();
            return a.WebAuthNIsUserVerifyingPlatformAuthenticatorAvailable(disponible) == 0 && disponible.getValue() != 0;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    // ───────────────────────── Estructuras (webauthn.h, versión 1) ─────────────────────────

    @Structure.FieldOrder({"dwVersion", "pwszId", "pwszName", "pwszIcon"})
    public static class Sitio extends Structure {
        public int dwVersion = 1;
        public WString pwszId;
        public WString pwszName;
        public WString pwszIcon;
    }

    @Structure.FieldOrder({"dwVersion", "cbId", "pbId", "pwszName", "pwszIcon", "pwszDisplayName"})
    public static class Usuario extends Structure {
        public int dwVersion = 1;
        public int cbId;
        public Pointer pbId;
        public WString pwszName;
        public WString pwszIcon;
        public WString pwszDisplayName;
    }

    @Structure.FieldOrder({"dwVersion", "pwszCredentialType", "lAlg"})
    public static class Algoritmo extends Structure {
        public int dwVersion = 1;
        public WString pwszCredentialType = new WString("public-key");
        public int lAlg;
    }

    @Structure.FieldOrder({"cCredentialParameters", "pCredentialParameters"})
    public static class ListaAlgoritmos extends Structure {
        public int cCredentialParameters;
        public Pointer pCredentialParameters;
    }

    @Structure.FieldOrder({"dwVersion", "cbClientDataJSON", "pbClientDataJSON", "pwszHashAlgId"})
    public static class DatosCliente extends Structure {
        public int dwVersion = 1;
        public int cbClientDataJSON;
        public Pointer pbClientDataJSON;
        public WString pwszHashAlgId = new WString("SHA-256");
    }

    @Structure.FieldOrder({"dwVersion", "cbId", "pbId", "pwszCredentialType"})
    public static class Credencial extends Structure {
        public int dwVersion = 1;
        public int cbId;
        public Pointer pbId;
        public WString pwszCredentialType = new WString("public-key");

        public Credencial() {
        }

        Credencial(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"cCredentials", "pCredentials"})
    public static class ListaCredenciales extends Structure {
        public int cCredentials;
        public Pointer pCredentials;
    }

    @Structure.FieldOrder({"cExtensions", "pExtensions"})
    public static class Extensiones extends Structure {
        public int cExtensions;
        public Pointer pExtensions;
    }

    @Structure.FieldOrder({"dwVersion", "dwTimeoutMilliseconds", "CredentialList", "Extensions",
        "dwAuthenticatorAttachment", "bRequireResidentKey", "dwUserVerificationRequirement",
        "dwAttestationConveyancePreference", "dwFlags"})
    public static class OpcionesCrear extends Structure {
        public int dwVersion = 1;
        public int dwTimeoutMilliseconds = TIEMPO_MS;
        public ListaCredenciales CredentialList = new ListaCredenciales();
        public Extensiones Extensions = new Extensiones();
        public int dwAuthenticatorAttachment = PLATAFORMA;
        public int bRequireResidentKey = 1;
        public int dwUserVerificationRequirement = VERIFICACION_REQUERIDA;
        public int dwAttestationConveyancePreference = SIN_ATESTACION;
        public int dwFlags;
    }

    @Structure.FieldOrder({"dwVersion", "dwTimeoutMilliseconds", "CredentialList", "Extensions",
        "dwAuthenticatorAttachment", "dwUserVerificationRequirement", "dwFlags"})
    public static class OpcionesObtener extends Structure {
        public int dwVersion = 1;
        public int dwTimeoutMilliseconds = TIEMPO_MS;
        public ListaCredenciales CredentialList = new ListaCredenciales();
        public Extensiones Extensions = new Extensiones();
        public int dwAuthenticatorAttachment = PLATAFORMA;
        public int dwUserVerificationRequirement = VERIFICACION_REQUERIDA;
        public int dwFlags;
    }

    /** Primeros campos de WEBAUTHN_CREDENTIAL_ATTESTATION (solo se leen). */
    @Structure.FieldOrder({"dwVersion", "pwszFormatType", "cbAuthenticatorData", "pbAuthenticatorData",
        "cbAttestation", "pbAttestation", "dwAttestationDecodeType", "pvAttestationDecode",
        "cbAttestationObject", "pbAttestationObject", "cbCredentialId", "pbCredentialId"})
    public static class Atestacion extends Structure {
        public int dwVersion;
        public Pointer pwszFormatType;
        public int cbAuthenticatorData;
        public Pointer pbAuthenticatorData;
        public int cbAttestation;
        public Pointer pbAttestation;
        public int dwAttestationDecodeType;
        public Pointer pvAttestationDecode;
        public int cbAttestationObject;
        public Pointer pbAttestationObject;
        public int cbCredentialId;
        public Pointer pbCredentialId;

        Atestacion(Pointer p) {
            super(p);
            read();
        }
    }

    /** Primeros campos de WEBAUTHN_ASSERTION (solo se leen). */
    @Structure.FieldOrder({"dwVersion", "cbAuthenticatorData", "pbAuthenticatorData", "cbSignature", "pbSignature",
        "Credential", "cbUserId", "pbUserId"})
    public static class AsercionNativa extends Structure {
        public int dwVersion;
        public int cbAuthenticatorData;
        public Pointer pbAuthenticatorData;
        public int cbSignature;
        public Pointer pbSignature;
        public Credencial Credential;
        public int cbUserId;
        public Pointer pbUserId;

        AsercionNativa(Pointer p) {
            super(p);
            read();
        }
    }

    // ───────────────────────── Operaciones ─────────────────────────

    private static Memory memoria(byte[] datos) {
        Memory m = new Memory(Math.max(1, datos.length));
        m.write(0, datos, 0, datos.length);
        return m;
    }

    private static byte[] leer(Pointer p, int largo) {
        return p == null || largo <= 0 ? new byte[0] : p.getByteArray(0, largo);
    }

    private static HWND ventana(Window w) {
        Pointer p = w == null ? null : Native.getWindowPointer(w);
        return p == null ? null : new HWND(p);
    }

    private static DatosCliente datosCliente(byte[] json, Memory memoria) {
        DatosCliente d = new DatosCliente();
        d.cbClientDataJSON = json.length;
        d.pbClientDataJSON = memoria;
        return d;
    }

    private static Api exigirApi() throws ErrorLlave {
        Api a = api();
        if (a == null || !disponible()) {
            throw new ErrorLlave("Este equipo no tiene Windows Hello configurado.");
        }
        return a;
    }

    private static ErrorLlave error(int hr, boolean creando) {
        if (hr == YA_EXISTE && creando) {
            return new ErrorLlave("Este dispositivo ya tiene una llave de acceso de PRPagos para tu cuenta.");
        }
        if (hr == CANCELADO || hr == NO_ENCONTRADA || hr == TIEMPO_AGOTADO) {
            return new ErrorLlave("Se canceló o se agotó el tiempo de la verificación.");
        }
        return new ErrorLlave("No se pudo usar la llave de acceso; inténtalo de nuevo.");
    }

    /**
     * Crea una llave en este equipo (Windows pide el PIN, la huella o el rostro).
     * El servidor verifica el resultado al registrarla.
     */
    public static Creada crear(Window w, String idSitio, String nombreSitio, byte[] idUsuario, String correo,
                               String nombre, byte[] datosClienteJson, List<byte[]> excluidas) throws ErrorLlave {
        Api a = exigirApi();
        Sitio sitio = new Sitio();
        sitio.pwszId = new WString(idSitio);
        sitio.pwszName = new WString(nombreSitio);

        Memory id = memoria(idUsuario);
        Usuario usuario = new Usuario();
        usuario.cbId = idUsuario.length;
        usuario.pbId = id;
        usuario.pwszName = new WString(correo);
        usuario.pwszDisplayName = new WString(nombre);

        Algoritmo[] algs = (Algoritmo[]) new Algoritmo().toArray(2);
        algs[0].lAlg = ES256;
        algs[1].lAlg = RS256;
        for (Algoritmo alg : algs) {
            alg.write();
        }
        ListaAlgoritmos lista = new ListaAlgoritmos();
        lista.cCredentialParameters = 2;
        lista.pCredentialParameters = algs[0].getPointer();

        Memory json = memoria(datosClienteJson);
        DatosCliente datos = datosCliente(datosClienteJson, json);

        OpcionesCrear opciones = new OpcionesCrear();
        Memory[] ids = new Memory[excluidas.size()];
        if (!excluidas.isEmpty()) {
            Credencial[] creds = (Credencial[]) new Credencial().toArray(excluidas.size());
            for (int i = 0; i < creds.length; i++) {
                ids[i] = memoria(excluidas.get(i));
                creds[i].cbId = excluidas.get(i).length;
                creds[i].pbId = ids[i];
                creds[i].write();
            }
            opciones.CredentialList.cCredentials = creds.length;
            opciones.CredentialList.pCredentials = creds[0].getPointer();
        }

        PointerByReference resultado = new PointerByReference();
        int hr = a.WebAuthNAuthenticatorMakeCredential(ventana(w), sitio, usuario, lista, datos, opciones, resultado);
        if (hr != 0 || resultado.getValue() == null) {
            throw error(hr, true);
        }
        try {
            Atestacion at = new Atestacion(resultado.getValue());
            return new Creada(leer(at.pbCredentialId, at.cbCredentialId),
                    leer(at.pbAttestationObject, at.cbAttestationObject));
        } finally {
            a.WebAuthNFreeCredentialAttestation(resultado.getValue());
        }
    }

    /** Ingresa con una llave de este equipo: Windows muestra las de PRPagos que tenga. */
    public static Asercion obtener(Window w, String idSitio, byte[] datosClienteJson) throws ErrorLlave {
        Api a = exigirApi();
        Memory json = memoria(datosClienteJson);
        DatosCliente datos = datosCliente(datosClienteJson, json);
        OpcionesObtener opciones = new OpcionesObtener();
        PointerByReference resultado = new PointerByReference();
        int hr = a.WebAuthNAuthenticatorGetAssertion(ventana(w), new WString(idSitio), datos, opciones,
                resultado);
        if (hr != 0 || resultado.getValue() == null) {
            throw error(hr, false);
        }
        try {
            AsercionNativa as = new AsercionNativa(resultado.getValue());
            return new Asercion(leer(as.Credential.pbId, as.Credential.cbId),
                    leer(as.pbAuthenticatorData, as.cbAuthenticatorData), leer(as.pbSignature, as.cbSignature),
                    leer(as.pbUserId, as.cbUserId));
        } finally {
            a.WebAuthNFreeAssertion(resultado.getValue());
        }
    }

    /** Versión de la API de Windows (para diagnóstico). */
    public static int versionApi() {
        Api a = api();
        return a == null ? 0 : a.WebAuthNGetApiVersionNumber();
    }

    // ───────────────────────── Formato WebAuthn (como un navegador) ─────────────────────────

    public static String b64(byte[] datos) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }

    public static byte[] deB64(String texto) {
        return Base64.getUrlDecoder().decode(texto);
    }

    /** clientDataJSON con el reto y el origen que entrega el servidor. */
    public static byte[] datosCliente(String tipo, String retoB64, String origen) {
        String json = "{\"type\":\"" + tipo + "\",\"challenge\":\"" + retoB64 + "\",\"origin\":\"" + origen
                + "\",\"crossOrigin\":false}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** La credencial de un ingreso, en el mismo JSON que envía el navegador. */
    public static JsonObject credencialIngreso(Asercion a, byte[] datosCliente) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("clientDataJSON", b64(datosCliente));
        respuesta.addProperty("authenticatorData", b64(a.datosAutenticador()));
        respuesta.addProperty("signature", b64(a.firma()));
        if (a.idUsuario().length > 0) {
            respuesta.addProperty("userHandle", b64(a.idUsuario()));
        }
        return credencial(a.credencialId(), respuesta);
    }

    /** La credencial de una llave nueva, en el mismo JSON que envía el navegador. */
    public static JsonObject credencialRegistro(Creada c, byte[] datosCliente) {
        JsonObject respuesta = new JsonObject();
        respuesta.addProperty("clientDataJSON", b64(datosCliente));
        respuesta.addProperty("attestationObject", b64(c.objetoAtestacion()));
        JsonArray transportes = new JsonArray();
        transportes.add("internal");
        respuesta.add("transports", transportes);
        return credencial(c.credencialId(), respuesta);
    }

    private static JsonObject credencial(byte[] id, JsonObject respuesta) {
        JsonObject c = new JsonObject();
        c.addProperty("id", b64(id));
        c.addProperty("rawId", b64(id));
        c.addProperty("type", "public-key");
        c.add("response", respuesta);
        c.addProperty("authenticatorAttachment", "platform");
        c.add("clientExtensionResults", new JsonObject());
        return c;
    }
}
