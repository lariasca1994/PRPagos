package com.prpagos.datos;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.prpagos.compartido.Compartido;
import com.prpagos.compartido.Sql;
import com.prpagos.conexion.ConexionBD;

/**
 * Registros de ingresos y gastos: las MISMAS consultas (compartido/consultas/)
 * y los MISMOS procedimientos que usa la web.
 */
public final class RepositorioPagos {

    private RepositorioPagos() {
    }

    /** Filtros de la consulta; null = sin filtro (igual que la web). */
    public record Filtros(String tipo, LocalDate desde, LocalDate hasta, String concepto, Double montoMin,
                          Double montoMax) {
    }

    /** usuarioId null = todos los usuarios (admin), con el dueño de cada uno. */
    public static List<Pago> buscar(Long usuarioId, Filtros f) throws SQLException {
        Map<String, Object> p = new HashMap<>();
        p.put("tipo", f.tipo());
        p.put("fecha_desde", f.desde());
        p.put("fecha_hasta", f.hasta());
        p.put("concepto", f.concepto());
        p.put("monto_min", f.montoMin());
        p.put("monto_max", f.montoMax());
        boolean todos = usuarioId == null;
        if (!todos) {
            p.put("usuario_id", usuarioId);
        }
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta(todos ? "registros_todos" : "registros"), p);
             ResultSet rs = st.executeQuery()) {
            List<Pago> pagos = new ArrayList<>();
            while (rs.next()) {
                long cat = rs.getLong(6);
                Long categoriaId = rs.wasNull() ? null : cat;
                pagos.add(new Pago(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getDate(4).toLocalDate(),
                        rs.getString(5), categoriaId, rs.getString(7),
                        todos ? rs.getString(8) : null, todos ? rs.getString(9) : null));
            }
            return pagos;
        }
    }

    public static void insertar(long usuarioId, String tipo, double monto, LocalDate fecha, String concepto,
                                Long categoriaId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL insertar_registro(?, ?, ?, ?, ?, ?)}")) {
            st.setString(1, tipo);
            st.setDouble(2, monto);
            st.setDate(3, Date.valueOf(fecha));
            st.setString(4, concepto);
            numeroONull(st, 5, categoriaId);
            st.setLong(6, usuarioId);
            st.execute();
        }
    }

    /** False si el registro no es de este usuario (el procedimiento no toca ninguna fila). */
    public static boolean actualizar(long usuarioId, long idPago, String tipo, double monto, LocalDate fecha,
                                     String concepto, Long categoriaId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL actualizar_registro(?, ?, ?, ?, ?, ?, ?, ?)}")) {
            st.setLong(1, idPago);
            st.setString(2, tipo);
            st.setDouble(3, monto);
            st.setDate(4, Date.valueOf(fecha));
            st.setString(5, concepto);
            numeroONull(st, 6, categoriaId);
            st.setLong(7, usuarioId);
            st.registerOutParameter(8, Types.NUMERIC);
            st.execute();
            return st.getInt(8) > 0;
        }
    }

    /** El admin edita el registro de cualquiera; la auditoría queda a su nombre. */
    public static boolean actualizarComoAdmin(long adminId, long idPago, String tipo, double monto, LocalDate fecha,
                                              String concepto, Long categoriaId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            int filas;
            try (PreparedStatement up = conn.prepareStatement(
                    "UPDATE TBPagos SET tipo = ?, monto = ?, fecha = ?, concepto = ?, categoria_id = ? WHERE id_pago = ?")) {
                up.setString(1, tipo);
                up.setDouble(2, monto);
                up.setDate(3, Date.valueOf(fecha));
                up.setString(4, concepto);
                if (categoriaId == null) {
                    up.setNull(5, Types.NUMERIC);
                } else {
                    up.setLong(5, categoriaId);
                }
                up.setLong(6, idPago);
                filas = up.executeUpdate();
            }
            if (filas > 0) {
                auditar(conn, adminId, idPago, "ACTUALIZAR (admin)", tipo + " " + com.prpagos.finanzas.Finanzas.numero(monto) + ", Concepto: "
                        + concepto.substring(0, Math.min(120, concepto.length())));
            }
            conn.commit();
            return filas == 1;
        }
    }

    public static Long dueno(long idPago) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement("SELECT usuario_id FROM TBPagos WHERE id_pago = ?")) {
            st.setLong(1, idPago);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** Elimina un registro (solo admin), dejando el registro de la eliminación. */
    public static boolean eliminar(long adminId, long idPago) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            auditar(conn, adminId, idPago, "ELIMINAR (admin)", "Pago eliminado");
            int filas;
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM TBPagos WHERE id_pago = ?")) {
                del.setLong(1, idPago);
                filas = del.executeUpdate();
            }
            conn.commit();
            return filas == 1;
        }
    }

    private static void auditar(Connection conn, long usuarioId, long idPago, String accion, String detalle)
            throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                + "VALUES (tbauditoria_sequence.NEXTVAL, ?, ?, ?, ?)")) {
            st.setLong(1, usuarioId);
            st.setLong(2, idPago);
            st.setString(3, accion);
            st.setString(4, detalle);
            st.executeUpdate();
        }
    }

    private static void numeroONull(CallableStatement st, int i, Long valor) throws SQLException {
        if (valor != null) {
            st.setLong(i, valor);
        } else {
            st.setNull(i, Types.NUMERIC);
        }
    }
}
