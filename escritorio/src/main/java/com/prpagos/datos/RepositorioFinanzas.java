package com.prpagos.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.prpagos.compartido.Compartido;
import com.prpagos.compartido.Sql;
import com.prpagos.conexion.ConexionBD;
import com.prpagos.finanzas.Finanzas;

/**
 * Categorías, plan 50/30/20, perfil, recuperación de acceso y los cálculos
 * del dashboard y de los resúmenes. Mismas consultas (compartido/consultas/)
 * y mismas sentencias que web/app/repositorio.py.
 */
public final class RepositorioFinanzas {

    private RepositorioFinanzas() {
    }

    // ───────────────────────── Categorías y plan ─────────────────────────

    public record Categoria(long id, String nombre, String tipo, String grupo, boolean propia, int usos) {
    }

    public static List<Categoria> categorias(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("categorias"), Map.of("usuario_id", usuarioId));
             ResultSet rs = st.executeQuery()) {
            List<Categoria> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new Categoria(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5) == 1, rs.getInt(6)));
            }
            return lista;
        }
    }

    /** False si ya tiene una categoría con ese nombre y tipo (propia o sugerida). */
    public static boolean crearCategoria(long usuarioId, String nombre, String tipo, String grupo) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            try (PreparedStatement st = conn.prepareStatement(
                    "SELECT COUNT(*) FROM TBCategorias WHERE UPPER(nombre) = UPPER(?) AND tipo = ? "
                    + "AND (usuario_id IS NULL OR usuario_id = ?)")) {
                st.setString(1, nombre);
                st.setString(2, tipo);
                st.setLong(3, usuarioId);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return false;
                    }
                }
            }
            try (PreparedStatement st = conn.prepareStatement(
                    "INSERT INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) "
                    + "VALUES (tbcategoria_sequence.NEXTVAL, ?, ?, ?, ?)")) {
                st.setLong(1, usuarioId);
                st.setString(2, nombre);
                st.setString(3, tipo);
                st.setString(4, grupo);
                st.executeUpdate();
            }
            return true;
        }
    }

    /** Null si se eliminó; si no, el motivo (mismos textos que la web). */
    public static String eliminarCategoria(long usuarioId, long categoriaId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            try (PreparedStatement st = conn.prepareStatement("SELECT COUNT(*) FROM TBPagos WHERE categoria_id = ?")) {
                st.setLong(1, categoriaId);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return "Esa categoría tiene registros: cámbialos de categoría antes de eliminarla";
                    }
                }
            }
            try (PreparedStatement st = conn.prepareStatement("DELETE FROM TBCategorias WHERE id = ? AND usuario_id = ?")) {
                st.setLong(1, categoriaId);
                st.setLong(2, usuarioId);
                return st.executeUpdate() == 1 ? null : "Solo puedes eliminar tus propias categorías";
            }
        }
    }

    public static Map<String, Double> plan(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("plan"), Map.of("usuario_id", usuarioId));
             ResultSet rs = st.executeQuery()) {
            rs.next();
            Map<String, Double> pcts = new LinkedHashMap<>();
            pcts.put("NECESIDADES", rs.getDouble(1));
            pcts.put("DESEOS", rs.getDouble(2));
            pcts.put("AHORRO", rs.getDouble(3));
            return pcts;
        }
    }

    public static void guardarPlan(long usuarioId, double necesidades, double deseos, double ahorro) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn,
                     "MERGE INTO TBPresupuestos p USING (SELECT :usuario_id AS usuario_id FROM dual) s "
                     + "ON (p.usuario_id = s.usuario_id) "
                     + "WHEN MATCHED THEN UPDATE SET pct_necesidades = :n, pct_deseos = :d, pct_ahorro = :a "
                     + "WHEN NOT MATCHED THEN INSERT (usuario_id, pct_necesidades, pct_deseos, pct_ahorro) "
                     + "VALUES (:usuario_id, :n, :d, :a)",
                     Map.of("usuario_id", usuarioId, "n", necesidades, "d", deseos, "a", ahorro))) {
            st.executeUpdate();
        }
    }

    // ───────────────────────── Perfil ─────────────────────────

    public record Perfil(String usuario, String nombre, boolean notificaciones, int respaldoRestantes) {
    }

    public static Perfil perfil(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT usuario, nombre, notificaciones, (SELECT COUNT(*) FROM TBCodigosRespaldo r "
                     + "WHERE r.usuario_id = l.id AND r.usado_en IS NULL) FROM TBPLogin l WHERE id = ?")) {
            st.setLong(1, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? new Perfil(rs.getString(1), rs.getString(2), rs.getInt(3) == 1, rs.getInt(4)) : null;
            }
        }
    }

    public static void guardarDatosPerfil(long usuarioId, String nombre, boolean notificaciones) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement("UPDATE TBPLogin SET nombre = ?, notificaciones = ? WHERE id = ?")) {
            st.setString(1, nombre);
            st.setInt(2, notificaciones ? 1 : 0);
            st.setLong(3, usuarioId);
            st.executeUpdate();
        }
    }

    // ───────────────────── Recuperación (respaldo y correo) ─────────────────────

    /** Los códigos nuevos anulan todos los anteriores. */
    public static void reemplazarCodigosRespaldo(long usuarioId, List<String> hashes) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM TBCodigosRespaldo WHERE usuario_id = ?");
                 PreparedStatement ins = conn.prepareStatement(
                         "INSERT INTO TBCodigosRespaldo (id, usuario_id, codigo_hash) "
                         + "VALUES (tbrespaldo_sequence.NEXTVAL, ?, ?)")) {
                del.setLong(1, usuarioId);
                del.executeUpdate();
                for (String h : hashes) {
                    ins.setLong(1, usuarioId);
                    ins.setString(2, h);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            conn.commit();
        }
    }

    /** Lo marca como usado; false si no existe o ya se había usado. */
    public static boolean usarCodigoRespaldo(long usuarioId, String hash) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            int filas;
            try (PreparedStatement st = conn.prepareStatement(
                    "UPDATE TBCodigosRespaldo SET usado_en = SYSTIMESTAMP "
                    + "WHERE usuario_id = ? AND codigo_hash = ? AND usado_en IS NULL")) {
                st.setLong(1, usuarioId);
                st.setString(2, hash);
                filas = st.executeUpdate();
            }
            if (filas > 0) {
                auditarAcceso(conn, usuarioId, "USO_RESPALDO", "Código de respaldo usado");
            }
            conn.commit();
            return filas == 1;
        }
    }

    public static void reiniciarIntentos(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE TBPLogin SET intentos_fallidos = 0, bloqueo_hasta = NULL WHERE id = ?")) {
            st.setLong(1, usuarioId);
            st.executeUpdate();
        }
    }

    public static void guardarCodigoCorreo(long usuarioId, String hash, int minutos) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "UPDATE TBPLogin SET recup_hash = ?, recup_expira = SYSTIMESTAMP + NUMTODSINTERVAL(?, 'MINUTE') "
                     + "WHERE id = ?")) {
            st.setString(1, hash);
            st.setInt(2, minutos);
            st.setLong(3, usuarioId);
            st.executeUpdate();
        }
    }

    /** Válido una sola vez y antes de vencer; al usarse se borra. */
    public static boolean usarCodigoCorreo(long usuarioId, String hash) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            int filas;
            try (PreparedStatement st = conn.prepareStatement(
                    "UPDATE TBPLogin SET recup_hash = NULL, recup_expira = NULL "
                    + "WHERE id = ? AND recup_hash = ? AND recup_expira > SYSTIMESTAMP")) {
                st.setLong(1, usuarioId);
                st.setString(2, hash);
                filas = st.executeUpdate();
            }
            if (filas > 0) {
                auditarAcceso(conn, usuarioId, "RECUPERA_CORREO", "Código por correo usado");
            }
            conn.commit();
            return filas == 1;
        }
    }

    private static void auditarAcceso(Connection conn, long usuarioId, String accion, String detalle) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                + "VALUES (tbauditoria_sequence.NEXTVAL, ?, NULL, ?, ?)")) {
            st.setLong(1, usuarioId);
            st.setString(2, accion);
            st.setString(3, detalle);
            st.executeUpdate();
        }
    }

    // ───────────────────────── Dashboard y resúmenes ─────────────────────────

    public record Totales(double ingresos, double gastos, int movimientos) {
    }

    private static Map<String, Object> rango(long usuarioId, LocalDate desde, LocalDate hasta) {
        Map<String, Object> p = new HashMap<>();
        p.put("usuario_id", usuarioId);
        p.put("desde", desde);
        p.put("hasta", hasta);
        return p;
    }

    public static Totales totales(long usuarioId, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("totales_periodo"), rango(usuarioId, desde, hasta));
             ResultSet rs = st.executeQuery()) {
            rs.next();
            return new Totales(rs.getDouble(1), rs.getDouble(2), rs.getInt(3));
        }
    }

    public static List<Finanzas.Mes> serieMeses(long usuarioId, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("serie_meses"), rango(usuarioId, desde, hasta));
             ResultSet rs = st.executeQuery()) {
            List<Finanzas.Mes> meses = new ArrayList<>();
            while (rs.next()) {
                meses.add(new Finanzas.Mes(rs.getDate(1).toLocalDate(), rs.getDouble(2), rs.getDouble(3)));
            }
            return meses;
        }
    }

    public static List<Finanzas.Categoria> gastosPorCategoria(long usuarioId, LocalDate desde, LocalDate hasta)
            throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("gastos_categoria"), rango(usuarioId, desde, hasta));
             ResultSet rs = st.executeQuery()) {
            List<Finanzas.Categoria> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new Finanzas.Categoria(rs.getString(1), rs.getString(2), rs.getDouble(3)));
            }
            return lista;
        }
    }

    public static Map<String, Double> gastosPorGrupo(long usuarioId, LocalDate desde, LocalDate hasta) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = Sql.preparar(conn, Compartido.consulta("gastos_grupo"), rango(usuarioId, desde, hasta));
             ResultSet rs = st.executeQuery()) {
            Map<String, Double> grupos = new HashMap<>();
            while (rs.next()) {
                grupos.put(rs.getString(1), rs.getDouble(2));
            }
            return grupos;
        }
    }
}
