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
