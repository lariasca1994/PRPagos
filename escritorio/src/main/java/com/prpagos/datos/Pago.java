package com.prpagos.datos;

import java.time.LocalDate;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Json;

/**
 * Un registro (ingreso o gasto), tal como lo entrega la API. duenoCorreo y
 * duenoNombre solo vienen en la consulta del administrador.
 */
public record Pago(long idPago, String tipo, String tipoTexto, double monto, LocalDate fecha, String concepto,
                   Long categoriaId, String categoria, String duenoCorreo, String duenoNombre) {

    /** tipoTexto: el nombre que da el servidor (tipos {"GASTO": "Gasto", ...}). */
    public static Pago de(JsonObject r, JsonObject tipos) {
        String tipo = Json.texto(r, "tipo");
        return new Pago(Json.entero(r, "id_pago"), tipo, Json.texto(tipos, tipo), Json.numero(r, "monto"),
                LocalDate.parse(Json.texto(r, "fecha")), Json.texto(r, "concepto"), Json.enteroONulo(r, "categoria_id"),
                Json.textoONulo(r, "categoria"), Json.textoONulo(r, "usuario"), Json.textoONulo(r, "nombre"));
    }

    public boolean esIngreso() {
        return "INGRESO".equals(tipo);
    }
}
