package com.prpagos.cliente;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Único punto de acceso a los datos: la API de la web (web/app/routers/api.py).
 * La aplicación de escritorio no tiene base de datos ni claves; pide todo aquí,
 * igual que el navegador pide las páginas, así que los datos, las reglas y los
 * mensajes son los mismos en la web y en el escritorio.
 *
 * La sesión es el mismo token de la web (6 h sin uso, máximo 8 h). Cada
 * respuesta trae el token renovado en la cabecera X-Sesion. Si la sesión ya no
 * vale (401), se avisa al oyente de cierre con el mensaje del servidor.
 */
public final class Api {

    /** Dirección de producción; -Dprpagos.url o la variable PRPAGOS_URL la cambian (pruebas locales). */
    private static final String URL_POR_DEFECTO = "https://prpagos-web-1087929107584.us-east4.run.app";

    /**
     * Archivo del repositorio con la dirección vigente del servidor. Si la web
     * cambia de dirección, las aplicaciones instaladas la encuentran aquí (y la
     * guardan), así que nunca se quedan sin poder actualizarse.
     */
    private static final String URL_DESCUBRIMIENTO =
            "https://raw.githubusercontent.com/lariasca1994/PRPagos/main/escritorio/servidor.json";
    private static final long ESPERA_DESCUBRIMIENTO_MS = 60_000;
    private static volatile String descubierta = leerGuardada();
    private static volatile long ultimoDescubrimiento;

    private static final Gson GSON = new Gson();
    // HTTP/1.1: sin el intento de "upgrade" a HTTP/2 que algunos servidores rechazan en http://.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static volatile String token;
    private static volatile Consumer<String> alCerrarSesion = mensaje -> { };

    private Api() {
    }

    // ───────────────────────── Configuración ─────────────────────────

    public static String base() {
        String url = System.getProperty("prpagos.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("PRPAGOS_URL");
        }
        if (url == null || url.isBlank()) {
            url = descubierta;
        }
        url = url == null || url.isBlank() ? URL_POR_DEFECTO : url.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static void usarSesion(String nuevo) {
        token = nuevo;
    }

    public static void cerrarSesion() {
        token = null;
    }

    /**
     * Momento (epoch, s) en que vence la sesión si no hay más actividad: el
     * campo "exp" del token que emitió el servidor (no hace falta ninguna
     * clave para leerlo). 0 si no hay sesión.
     */
    public static long vence() {
        String actual = token;
        if (actual == null) {
            return 0;
        }
        try {
            String carga = new String(java.util.Base64.getUrlDecoder().decode(actual.split("\\.")[1]),
                    StandardCharsets.UTF_8);
            return JsonParser.parseString(carga).getAsJsonObject().get("exp").getAsLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Qué hacer cuando el servidor da la sesión por cerrada (recibe el mensaje del motivo). */
    public static void alCerrarSesion(Consumer<String> oyente) {
        alCerrarSesion = oyente == null ? mensaje -> { } : oyente;
    }

    // ───────────────────────── Peticiones ─────────────────────────

    public static JsonObject get(String ruta) throws ErrorApi {
        return json(enviar("GET", ruta, null));
    }

    public static JsonObject get(String ruta, Map<String, String> parametros) throws ErrorApi {
        return get(ruta + consulta(parametros));
    }

    public static JsonObject post(String ruta, Object cuerpo) throws ErrorApi {
        return json(enviar("POST", ruta, cuerpo == null ? new JsonObject() : cuerpo));
    }

    public static JsonObject put(String ruta, Object cuerpo) throws ErrorApi {
        return json(enviar("PUT", ruta, cuerpo));
    }

    public static JsonObject delete(String ruta) throws ErrorApi {
        return json(enviar("DELETE", ruta, null));
    }

    /** Respuesta de texto (el CSV de la consulta). */
    public static String texto(String ruta, Map<String, String> parametros) throws ErrorApi {
        return enviar("GET", ruta + consulta(parametros), null);
    }

    public static String consulta(Map<String, String> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            return "";
        }
        StringJoiner j = new StringJoiner("&", "?", "");
        parametros.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                j.add(k + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8));
            }
        });
        return j.length() == 1 ? "" : j.toString();
    }

    private static JsonObject json(String texto) throws ErrorApi {
        try {
            JsonElement e = JsonParser.parseString(texto);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            throw new ErrorApi("Respuesta inesperada del servidor.");
        }
    }

    /**
     * Si el servidor no responde (o la dirección ya no existe), consulta la
     * dirección vigente en el repositorio y, si cambió, repite el pedido allí.
     */
    private static String enviar(String metodo, String ruta, Object cuerpo) throws ErrorApi {
        String actual = base();
        try {
            return enviar(actual, metodo, ruta, cuerpo);
        } catch (SinServidor e) {
            String nueva = descubrir();
            if (nueva != null && !nueva.equals(actual)) {
                try {
                    return enviar(nueva, metodo, ruta, cuerpo);
                } catch (SinServidor otra) {
                    throw new ErrorApi(otra.getMessage());
                }
            }
            throw new ErrorApi(e.getMessage());
        }
    }

    private static String enviar(String base, String metodo, String ruta, Object cuerpo) throws ErrorApi {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        String actual = token;
        if (actual != null) {
            pedido.header("Authorization", "Bearer " + actual);
        }
        if (cuerpo != null) {
            pedido.header("Content-Type", "application/json; charset=utf-8")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(GSON.toJson(cuerpo), StandardCharsets.UTF_8));
        } else {
            pedido.method(metodo, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> respuesta;
        try {
            respuesta = HTTP.send(pedido.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SinServidor("No se pudo conectar con el simulador de pagos. Revisa tu conexión a internet e inténtalo de nuevo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorApi("Se interrumpió la conexión con el simulador de pagos.");
        }
        respuesta.headers().firstValue("X-Sesion").ifPresent(nuevo -> {
            if (token != null) {
                token = nuevo;
            }
        });
        int estado = respuesta.statusCode();
        if (estado >= 200 && estado < 300) {
            return respuesta.body();
        }
        if (estado == 404 && !esRespuestaDeLaApi(respuesta.body())) {
            // La dirección ya no tiene la aplicación: puede haberse mudado.
            throw new SinServidor("No se pudo conectar con el simulador de pagos. Inténtalo de nuevo en unos minutos.");
        }
        String mensaje = mensajeDe(respuesta.body(), estado);
        if (estado == 401 && actual != null) {
            token = null;
            alCerrarSesion.accept(mensaje);
            throw new SesionCerrada(mensaje);
        }
        throw new ErrorApi(mensaje);
    }

    private static boolean esRespuestaDeLaApi(String cuerpo) {
        try {
            return JsonParser.parseString(cuerpo).isJsonObject();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ───────────────────────── Dirección vigente del servidor ─────────────────────────

    /** Lee la dirección vigente publicada en el repositorio y la guarda. Null si no se pudo. */
    static String descubrir() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoDescubrimiento < ESPERA_DESCUBRIMIENTO_MS) {
            return descubierta;
        }
        ultimoDescubrimiento = ahora;
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(URL_DESCUBRIMIENTO))
                    .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
            HttpResponse<String> r = HTTP.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() != 200) {
                return null;
            }
            String url = urlValida(JsonParser.parseString(r.body()).getAsJsonObject().get("url").getAsString());
            if (url == null) {
                return null;
            }
            if (!url.equals(descubierta)) {
                descubierta = url;
                guardar(url);
            }
            return url;
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Solo direcciones HTTPS sin ruta ni credenciales. */
    static String urlValida(String texto) {
        try {
            URI u = URI.create(texto.strip());
            if (!"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null) {
                return null;
            }
            return "https://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static java.nio.file.Path archivoGuardado() {
        String appData = System.getenv("APPDATA");
        java.nio.file.Path carpeta = appData != null ? java.nio.file.Path.of(appData, "PRPagos")
                : java.nio.file.Path.of(System.getProperty("user.home"), ".prpagos");
        return carpeta.resolve("servidor.txt");
    }

    private static String leerGuardada() {
        try {
            java.nio.file.Path f = archivoGuardado();
            return java.nio.file.Files.isRegularFile(f)
                    ? urlValida(java.nio.file.Files.readString(f, StandardCharsets.UTF_8)) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void guardar(String url) {
        try {
            java.nio.file.Path f = archivoGuardado();
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, url, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // Sin guardarla, se vuelve a consultar en la próxima falla.
        }
    }

    private static String mensajeDe(String cuerpo, int estado) {
        try {
            JsonObject o = JsonParser.parseString(cuerpo).getAsJsonObject();
            if (o.has("error") && o.get("error").isJsonPrimitive()) {
                return o.get("error").getAsString();
            }
        } catch (RuntimeException e) {
            // Sin cuerpo JSON: mensaje genérico según el estado.
        }
        return switch (estado) {
            case 403 -> "No tienes permiso para esta acción.";
            case 404 -> "No se encontró lo que buscabas.";
            case 503 -> "El simulador de pagos no está disponible en este momento. Intenta de nuevo en unos minutos.";
            default -> "Ocurrió un error inesperado. Intenta de nuevo.";
        };
    }

    // ───────────────────────── Errores ─────────────────────────

    /** Mensaje para la persona (el mismo que muestra la web). */
    public static class ErrorApi extends Exception {
        private static final long serialVersionUID = 1L;

        public ErrorApi(String mensaje) {
            super(mensaje);
        }
    }

    /** No respondió el servidor o la dirección ya no lo tiene: se intenta la dirección vigente. */
    static final class SinServidor extends ErrorApi {
        private static final long serialVersionUID = 1L;

        SinServidor(String mensaje) {
            super(mensaje);
        }
    }

    /** La sesión ya no vale: ya se avisó al oyente, que vuelve al ingreso. */
    public static final class SesionCerrada extends ErrorApi {
        private static final long serialVersionUID = 1L;

        SesionCerrada(String mensaje) {
            super(mensaje);
        }
    }
}
