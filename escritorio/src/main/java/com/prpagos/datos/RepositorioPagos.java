package com.prpagos.datos;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.prpagos.conexion.ConexionBD;

/** Pagos del usuario: los mismos procedimientos que usa la web. */
public final class RepositorioPagos {

    private RepositorioPagos() {
    }

    public static List<Pago> buscar(long usuarioId, LocalDate desde, LocalDate hasta, String concepto,
                                    Double montoMin, Double montoMax) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL buscar_tbpagos(?, ?, ?, ?, ?, ?, ?)}")) {
            st.setLong(1, usuarioId);
            fechaONull(st, 2, desde);
            fechaONull(st, 3, hasta);
            if (concepto != null) {
                st.setString(4, concepto);
            } else {
                st.setNull(4, Types.VARCHAR);
            }
            numeroONull(st, 5, montoMin);
            numeroONull(st, 6, montoMax);
            st.registerOutParameter(7, Types.REF_CURSOR);
            st.execute();
            List<Pago> pagos = new ArrayList<>();
            try (ResultSet rs = (ResultSet) st.getObject(7)) {
                while (rs.next()) {
                    pagos.add(new Pago(rs.getLong("id_pago"), rs.getDouble("monto"),
                            rs.getDate("fecha").toLocalDate(), rs.getString("concepto")));
                }
            }
            return pagos;
        }
    }

    public static void insertar(long usuarioId, double monto, LocalDate fecha, String concepto)
            throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL insertar_tbpago(?, ?, ?, ?)}")) {
            st.setDouble(1, monto);
            st.setDate(2, Date.valueOf(fecha));
            st.setString(3, concepto);
            st.setLong(4, usuarioId);
            st.execute();
        }
    }

    /** False si el pago no es de este usuario (el procedimiento no toca ninguna fila). */
    public static boolean actualizar(long usuarioId, long idPago, double monto, LocalDate fecha,
                                     String concepto) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL actualizar_tbpago(?, ?, ?, ?, ?, ?)}")) {
            st.setLong(1, idPago);
            st.setDouble(2, monto);
            st.setDate(3, Date.valueOf(fecha));
            st.setString(4, concepto);
            st.setLong(5, usuarioId);
            st.registerOutParameter(6, Types.NUMERIC);
            st.execute();
            return st.getInt(6) > 0;
        }
    }

    private static void fechaONull(CallableStatement st, int i, LocalDate fecha) throws SQLException {
        if (fecha != null) {
            st.setDate(i, Date.valueOf(fecha));
        } else {
            st.setNull(i, Types.DATE);
        }
    }

    private static void numeroONull(CallableStatement st, int i, Double valor) throws SQLException {
        if (valor != null) {
            st.setDouble(i, valor);
        } else {
            st.setNull(i, Types.NUMERIC);
        }
    }
}
