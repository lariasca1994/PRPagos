package com.prpagos.datos;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;

import com.prpagos.conexion.ConexionBD;

/**
 * Acceso a datos de las cuentas. Llama a los MISMOS procedimientos que la
 * web (sql/seguridad.sql): el bloqueo por intentos fallidos, el último
 * código TOTP usado y la auditoría los decide la base, igual para las dos
 * interfaces.
 */
public final class RepositorioUsuarios {

    private RepositorioUsuarios() {
    }

    public static Credenciales porCorreo(String correo) throws SQLException {
        return obtener(correo, null);
    }

    public static Credenciales porId(long usuarioId) throws SQLException {
        return obtener(null, usuarioId);
    }

    private static Credenciales obtener(String correo, Long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall(
                     "{CALL obtener_credenciales(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}")) {
            if (correo != null) {
                st.setString(1, correo);
            } else {
                st.setNull(1, Types.VARCHAR);
            }
            if (usuarioId != null) {
                st.setLong(2, usuarioId);
            } else {
                st.setNull(2, Types.NUMERIC);
            }
            st.registerOutParameter(3, Types.NUMERIC);
            st.registerOutParameter(4, Types.VARCHAR);
            st.registerOutParameter(5, Types.VARCHAR);
            st.registerOutParameter(6, Types.VARCHAR);
            st.registerOutParameter(7, Types.VARCHAR);
            st.registerOutParameter(8, Types.NUMERIC);
            st.registerOutParameter(9, Types.NUMERIC);
            st.registerOutParameter(10, Types.NUMERIC);
            st.execute();
            long id = st.getLong(3);
            if (st.wasNull()) {
                return null;
            }
            return new Credenciales(id, st.getString(4), st.getString(5), st.getString(6),
                    st.getString(7), st.getInt(8) == 1, st.getLong(9), st.getInt(10) == 1);
        }
    }

    /** motivo: FALLO_CLAVE, FALLO_TOTP o FALLO_RECUPERAR. */
    public static void registrarFallo(long usuarioId, String motivo) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL registrar_fallo_acceso(?, ?)}")) {
            st.setLong(1, usuarioId);
            st.setString(2, motivo);
            st.execute();
        }
    }

    /** False si ese paso TOTP ya se había usado (en esta interfaz o en la web). */
    public static boolean registrarExito(long usuarioId, long paso, String motivo) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL registrar_acceso_exitoso(?, ?, ?, ?)}")) {
            st.setLong(1, usuarioId);
            st.setLong(2, paso);
            st.setString(3, motivo);
            st.registerOutParameter(4, Types.NUMERIC);
            st.execute();
            return st.getInt(4) > 0;
        }
    }

    public static void guardarTotp(long usuarioId, String secretoCifrado, long paso) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL guardar_totp(?, ?, ?)}")) {
            st.setLong(1, usuarioId);
            st.setString(2, secretoCifrado);
            st.setLong(3, paso);
            st.execute();
        }
    }

    /** motivo: CAMBIO_CLAVE, RECUPERAR_CLAVE o MIGRAR_HASH. */
    public static void cambiarPassword(long usuarioId, String hash, String motivo) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             CallableStatement st = conn.prepareCall("{CALL cambiar_password(?, ?, ?)}")) {
            st.setLong(1, usuarioId);
            st.setString(2, hash);
            st.setString(3, motivo);
            st.execute();
        }
    }

    /**
     * Rol y estado actuales de la cuenta (null si ya no existe). Se consulta al
     * cambiar de pantalla, igual que la web en cada petición.
     */
    public static Cuenta estado(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, usuario, nombre, rol, activo FROM TBPLogin WHERE id = ?")) {
            st.setLong(1, usuarioId);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                return rs.next() ? cuenta(rs) : null;
            }
        }
    }

    // ───────────── Administración (mismas reglas que web/app/repositorio.py) ─────────────

    public static java.util.List<Cuenta> listar() throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "SELECT id, usuario, nombre, rol, activo FROM TBPLogin ORDER BY nombre");
             java.sql.ResultSet rs = st.executeQuery()) {
            java.util.List<Cuenta> cuentas = new java.util.ArrayList<>();
            while (rs.next()) {
                cuentas.add(cuenta(rs));
            }
            return cuentas;
        }
    }

    public static void suspender(long usuarioId) throws SQLException {
        actualizarActivo(usuarioId, 0);
    }

    public static void reactivar(long usuarioId) throws SQLException {
        actualizarActivo(usuarioId, 1);
    }

    /**
     * Null si se eliminó; si no, el motivo. No elimina cuentas con pagos o
     * auditoría de pagos (hay que suspenderlas); la auditoría de acceso
     * (id_pago NULL) se borra junto con la cuenta.
     */
    public static String eliminar(long usuarioId) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);
            try {
                if (contar(conn, "SELECT COUNT(*) FROM TBPagos WHERE usuario_id = ?", usuarioId) > 0) {
                    conn.rollback();
                    return "Esa cuenta tiene pagos registrados -- suspéndela en vez de eliminarla";
                }
                if (contar(conn, "SELECT COUNT(*) FROM TBAuditoria WHERE usuario_id = ? AND id_pago IS NOT NULL",
                        usuarioId) > 0) {
                    conn.rollback();
                    return "Esa cuenta tiene historial de auditoría -- suspéndela en vez de eliminarla";
                }
                int borradas;
                try (PreparedStatement a = conn.prepareStatement(
                        "DELETE FROM TBAuditoria WHERE usuario_id = ? AND id_pago IS NULL");
                     PreparedStatement b = conn.prepareStatement("DELETE FROM TBPLogin WHERE id = ?")) {
                    a.setLong(1, usuarioId);
                    a.executeUpdate();
                    b.setLong(1, usuarioId);
                    borradas = b.executeUpdate();
                }
                conn.commit();
                return borradas == 1 ? null : "No se encontró el usuario";
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static void actualizarActivo(long usuarioId, int activo) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement("UPDATE TBPLogin SET activo = ? WHERE id = ?")) {
            st.setInt(1, activo);
            st.setLong(2, usuarioId);
            st.executeUpdate();
        }
    }

    private static int contar(Connection conn, String sql, long usuarioId) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setLong(1, usuarioId);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Cuenta cuenta(java.sql.ResultSet rs) throws SQLException {
        return new Cuenta(rs.getLong("id"), rs.getString("usuario"), rs.getString("nombre"),
                rs.getString("rol"), rs.getInt("activo") == 1);
    }

    /** False si el correo ya tiene cuenta. */
    public static boolean crear(String correo, String nombre, String hash) throws SQLException {
        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement st = conn.prepareStatement(
                     "INSERT INTO TBPLogin (id, usuario, nombre, password, rol, activo) "
                     + "VALUES (tblogin_sequence.NEXTVAL, ?, ?, ?, 'usuario', 1)")) {
            st.setString(1, correo);
            st.setString(2, nombre);
            st.setString(3, hash);
            st.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) { // ORA-00001: el UNIQUE de "usuario"
                return false;
            }
            throw e;
        }
    }
}
