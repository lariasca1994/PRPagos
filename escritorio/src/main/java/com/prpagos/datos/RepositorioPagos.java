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

    // ───────────── Administración (mismas consultas que web/app/repositorio.py) ─────────────

    /** Pagos de TODOS los usuarios, con el dueño de cada uno. */
    public static List<Pago> buscarTodos(LocalDate desde, LocalDate hasta, String concepto,
                                         Double montoMin, Double montoMax) throws SQLException {
        String sql = "SELECT p.id_pago, p.monto, p.fecha, p.concepto, u.usuario, u.nombre "
                + "FROM TBPagos p JOIN TBPLogin u ON u.id = p.usuario_id "
                + "WHERE (? IS NULL OR p.fecha >= ?) AND (? IS NULL OR p.fecha <= ?) "
                + "AND (? IS NULL OR UPPER(p.concepto) LIKE '%' || UPPER(?) || '%') "
                + "AND (? IS NULL OR p.monto >= ?) AND (? IS NULL OR p.monto <= ?) "
                + "ORDER BY p.fecha DESC";
        try (Connection conn = ConexionBD.obtenerConexion();
             java.sql.PreparedStatement st = conn.prepareStatement(sql)) {
            Object[] valores = {
                desde == null ? null : Date.valueOf(desde), hasta == null ? null : Date.valueOf(hasta),
                concepto, montoMin, montoMax,
            };
            int[] tipos = {Types.DATE, Types.DATE, Types.VARCHAR, Types.NUMERIC, Types.NUMERIC};
            for (int i = 0; i < valores.length; i++) {
                for (int repetido = 0; repetido < 2; repetido++) {
                    int indice = i * 2 + repetido + 1;
                    if (valores[i] == null) {
                        st.setNull(indice, tipos[i]);
                    } else {
                        st.setObject(indice, valores[i], tipos[i]);
                    }
                }
            }
            List<Pago> pagos = new ArrayList<>();
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    pagos.add(new Pago(rs.getLong(1), rs.getDouble(2), rs.getDate(3).toLocalDate(),
                            rs.getString(4), rs.getString(5), rs.getString(6)));
                }
            }
            return pagos;
        }
    }

    /** Edita el pago de cualquiera y deja auditoría con el admin como autor. */
    public static boolean actualizarComoAdmin(long adminId, long idPago, double monto, LocalDate fecha,
                                              String concepto) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            int filas;
            try (java.sql.PreparedStatement up = conn.prepareStatement(
                    "UPDATE TBPagos SET monto = ?, fecha = ?, concepto = ? WHERE id_pago = ?")) {
                up.setDouble(1, monto);
                up.setDate(2, Date.valueOf(fecha));
                up.setString(3, concepto);
                up.setLong(4, idPago);
                filas = up.executeUpdate();
            }
            if (filas > 0) {
                auditar(conn, adminId, idPago, "ACTUALIZAR (admin)",
                        "Nuevo monto: " + formatoOracle(monto) + ", Nuevo concepto: " + concepto);
            }
            conn.commit();
            return filas == 1;
        }
    }

    /** Elimina un pago (solo admin), dejando el registro de la eliminación. */
    public static boolean eliminar(long adminId, long idPago) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            auditar(conn, adminId, idPago, "ELIMINAR (admin)", "Pago eliminado");
            int filas;
            try (java.sql.PreparedStatement del = conn.prepareStatement("DELETE FROM TBPagos WHERE id_pago = ?")) {
                del.setLong(1, idPago);
                filas = del.executeUpdate();
            }
            conn.commit();
            return filas == 1;
        }
    }

    private static void auditar(Connection conn, long usuarioId, long idPago, String accion, String detalle)
            throws SQLException {
        try (java.sql.PreparedStatement st = conn.prepareStatement(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                + "VALUES (tbauditoria_sequence.NEXTVAL, ?, ?, ?, ?)")) {
            st.setLong(1, usuarioId);
            st.setLong(2, idPago);
            st.setString(3, accion);
            st.setString(4, detalle);
            st.executeUpdate();
        }
    }

    /** Como Python imprime un float (150000.0), para que la auditoría quede igual que desde la web. */
    private static String formatoOracle(double monto) {
        return String.valueOf(monto);
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
