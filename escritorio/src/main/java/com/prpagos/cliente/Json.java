package com.prpagos.cliente;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Lectura cómoda de las respuestas de la API (campo ausente o null = valor vacío). */
public final class Json {

    private Json() {
    }

    private static JsonElement campo(JsonObject o, String clave) {
        JsonElement e = o == null ? null : o.get(clave);
        return e == null || e.isJsonNull() ? null : e;
    }

    public static String texto(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e == null ? "" : e.getAsString();
    }

    /** null si el campo no existe o es null. */
    public static String textoONulo(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e == null ? null : e.getAsString();
    }

    public static double numero(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e == null ? 0 : e.getAsDouble();
    }

    public static long entero(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e == null ? 0 : e.getAsLong();
    }

    public static Long enteroONulo(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e == null ? null : e.getAsLong();
    }

    public static boolean logico(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e != null && e.getAsBoolean();
    }

    public static JsonObject objeto(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public static List<JsonObject> lista(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        List<JsonObject> lista = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonObject()) {
                    lista.add(x.getAsJsonObject());
                }
            }
        }
        return lista;
    }

    public static List<String> textos(JsonObject o, String clave) {
        JsonElement e = campo(o, clave);
        List<String> lista = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                lista.add(x.getAsString());
            }
        }
        return lista;
    }

    /** Cuerpo de una petición: pares clave, valor. */
    public static JsonObject de(Object... pares) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            Object v = pares[i + 1];
            String k = String.valueOf(pares[i]);
            if (v == null) {
                o.add(k, com.google.gson.JsonNull.INSTANCE);
            } else if (v instanceof Boolean b) {
                o.addProperty(k, b);
            } else if (v instanceof Number n) {
                o.addProperty(k, n);
            } else if (v instanceof JsonElement j) {
                o.add(k, j);
            } else {
                o.addProperty(k, String.valueOf(v));
            }
        }
        return o;
    }
}
