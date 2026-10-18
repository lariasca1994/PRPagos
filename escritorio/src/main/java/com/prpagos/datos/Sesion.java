package com.prpagos.datos;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Json;

/**
 * La cuenta con la sesión abierta, tal como la entrega la API. El token vive
 * en cliente.Api; el servidor valida la cuenta en cada petición (una
 * suspensión o un cambio de rol tienen efecto inmediato).
 */
public record Sesion(long usuarioId, String correo, String nombre, boolean admin) {

    /** Desde {"id", "correo", "nombre", "admin"} de la API. */
    public static Sesion de(JsonObject usuario) {
        return new Sesion(Json.entero(usuario, "id"), Json.texto(usuario, "correo"), Json.texto(usuario, "nombre"),
                Json.logico(usuario, "admin"));
    }
}
