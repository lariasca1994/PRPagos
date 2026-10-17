package com.prpagos.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lector CBOR (RFC 8949) mínimo para las llaves de acceso: lo justo para leer
 * la clave pública COSE de un autenticador (mapas, enteros, bytes, textos,
 * listas y valores simples). En la web lo hace la librería webauthn.
 */
final class Cbor {

    private final byte[] datos;
    private int pos;

    private Cbor(byte[] datos, int inicio) {
        this.datos = datos;
        this.pos = inicio;
    }

    /** Lee un elemento desde {@code inicio}; {@code fin[0]} queda en la posición siguiente. */
    static Object leer(byte[] datos, int inicio, int[] fin) {
        Cbor c = new Cbor(datos, inicio);
        Object valor = c.elemento();
        if (fin != null) {
            fin[0] = c.pos;
        }
        return valor;
    }

    private int byteSinSigno() {
        if (pos >= datos.length) {
            throw new IllegalArgumentException("CBOR incompleto");
        }
        return datos[pos++] & 0xFF;
    }

    private long longitud(int info) {
        if (info < 24) {
            return info;
        }
        int bytes = switch (info) {
            case 24 -> 1;
            case 25 -> 2;
            case 26 -> 4;
            case 27 -> 8;
            default -> throw new IllegalArgumentException("CBOR de longitud indefinida no admitido");
        };
        long valor = 0;
        for (int i = 0; i < bytes; i++) {
            valor = (valor << 8) | byteSinSigno();
        }
        return valor;
    }

    private byte[] bytes(long n) {
        if (n < 0 || pos + n > datos.length) {
            throw new IllegalArgumentException("CBOR incompleto");
        }
        byte[] b = new byte[(int) n];
        System.arraycopy(datos, pos, b, 0, b.length);
        pos += b.length;
        return b;
    }

    private Object elemento() {
        int inicial = byteSinSigno();
        int tipo = inicial >> 5;
        int info = inicial & 0x1F;
        switch (tipo) {
            case 0:
                return longitud(info);
            case 1:
                return -1 - longitud(info);
            case 2:
                return bytes(longitud(info));
            case 3:
                return new String(bytes(longitud(info)), StandardCharsets.UTF_8);
            case 4: {
                long n = longitud(info);
                List<Object> lista = new ArrayList<>();
                for (long i = 0; i < n; i++) {
                    lista.add(elemento());
                }
                return lista;
            }
            case 5: {
                long n = longitud(info);
                Map<Object, Object> mapa = new LinkedHashMap<>();
                for (long i = 0; i < n; i++) {
                    mapa.put(elemento(), elemento());
                }
                return mapa;
            }
            case 7:
                return switch (info) {
                    case 20 -> Boolean.FALSE;
                    case 21 -> Boolean.TRUE;
                    case 22, 23 -> null;
                    default -> throw new IllegalArgumentException("Valor CBOR no admitido");
                };
            default:
                throw new IllegalArgumentException("Tipo CBOR no admitido: " + tipo);
        }
    }
}
