package com.prpagos.finanzas;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.prpagos.conexion.ConexionBD;

/**
 * Envío de correos con la API transaccional de Brevo (plan gratuito). Gemelo de
 * web/app/correo.py: la misma petición, el mismo remitente y las mismas
 * plantillas (compartido/correo/).
 */
public final class Correo {

    private static final String URL_BREVO = "https://api.brevo.com/v3/smtp/email";
    private static final String NOMBRE_REMITENTE = "PRPagos";
    private static final HttpClient CLIENTE = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    private Correo() {
    }

    public static class ErrorCorreo extends Exception {
        public ErrorCorreo(String mensaje) {
            super(mensaje);
        }
    }

    public static boolean disponible() {
        return !vacio(propiedad("brevo.api.key")) && !vacio(propiedad("brevo.remitente"));
    }

    public static void enviar(String destino, String nombre, String asunto, String html) throws ErrorCorreo {
        if (!disponible()) {
            throw new ErrorCorreo("El envío de correos no está configurado (brevo.api.key y brevo.remitente).");
        }
        String cuerpo = "{\"sender\":{\"name\":" + json(NOMBRE_REMITENTE) + ",\"email\":"
                + json(propiedad("brevo.remitente")) + "},\"to\":[{\"email\":" + json(destino) + ",\"name\":"
                + json(nombre) + "}],\"subject\":" + json(asunto) + ",\"htmlContent\":" + json(html) + "}";
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(URL_BREVO))
                .timeout(Duration.ofSeconds(20))
                .header("api-key", propiedad("brevo.api.key"))
                .header("content-type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        try {
            HttpResponse<String> r = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200 && r.statusCode() != 201 && r.statusCode() != 202) {
                String detalle = r.body() == null ? "" : r.body().substring(0, Math.min(200, r.body().length()));
                throw new ErrorCorreo("Brevo rechazó el correo (" + r.statusCode() + "): " + detalle);
            }
        } catch (IOException e) {
            throw new ErrorCorreo("No se pudo conectar con Brevo: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorCorreo("Envío interrumpido");
        }
    }

    private static String propiedad(String nombre) {
        try {
            return ConexionBD.propiedad(nombre);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** Cadena JSON con escape de comillas, barras y caracteres de control. */
    static String json(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
