package com.prpagos.compartido;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Las consultas de compartido/ usan parámetros con nombre (:usuario_id), como
 * python-oracledb. JDBC solo tiene "?": aquí se convierten respetando el
 * orden de aparición (un mismo nombre puede repetirse) y sin tocar el texto
 * entre comillas simples.
 */
public final class Sql {

    private Sql() {
    }

    public static PreparedStatement preparar(Connection conn, String sql, Map<String, Object> parametros)
            throws SQLException {
        List<String> nombres = new ArrayList<>();
        StringBuilder convertido = new StringBuilder();
        boolean enCadena = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                enCadena = !enCadena;
            }
            if (!enCadena && c == ':' && i + 1 < sql.length() && Character.isLetter(sql.charAt(i + 1))) {
                int fin = i + 1;
                while (fin < sql.length() && (Character.isLetterOrDigit(sql.charAt(fin)) || sql.charAt(fin) == '_')) {
                    fin++;
                }
                nombres.add(sql.substring(i + 1, fin));
                convertido.append('?');
                i = fin - 1;
            } else {
                convertido.append(c);
            }
        }
        PreparedStatement st = conn.prepareStatement(convertido.toString());
        for (int i = 0; i < nombres.size(); i++) {
            String nombre = nombres.get(i);
            if (!parametros.containsKey(nombre)) {
                st.close();
                throw new SQLException("Falta el parámetro :" + nombre);
            }
            asignar(st, i + 1, parametros.get(nombre));
        }
        return st;
    }

    private static void asignar(PreparedStatement st, int i, Object valor) throws SQLException {
        if (valor == null) {
            st.setNull(i, Types.VARCHAR);
        } else if (valor instanceof LocalDate d) {
            st.setDate(i, Date.valueOf(d));
        } else if (valor instanceof Double d) {
            st.setDouble(i, d);
        } else if (valor instanceof Long l) {
            st.setLong(i, l);
        } else if (valor instanceof Integer n) {
            st.setInt(i, n);
        } else {
            st.setString(i, valor.toString());
        }
    }
}
