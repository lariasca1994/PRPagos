package com.prpagos.datos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.prpagos.compartido.Compartido;
import com.prpagos.compartido.Sql;
import com.prpagos.conexion.ConexionBD;

/**
 * Llaves de acceso: las mismas sentencias de compartido/consultas que usa la
 * web (repositorio.py), así que una llave registrada en una aparece en la otra.
 */
public final class RepositorioLlaves {

    /** Fila de Perfil. Las fechas ya vienen en hora de Colombia ("—" si nunca se usó). */
    public record Llave(long id, String nombre, String creada, String ultimoUso, String credencialId) {
    }

    /** Llave buscada al ingresar. */
    public record Guardada(long id, long usuarioId, String clavePublica, long contador) {
    }

    private RepositorioLlaves() {
    }

    public static List<Llave> deUsuario(long usuarioId) throws SQLException {
        List<Llave> lista = new ArrayList<>();
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement st = Sql.preparar(c, Compartido.consulta("llaves_usuario"),
                        Map.of("usuario_id", usuarioId));
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                lista.add(new Llave(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
            }
        }
        return lista;
    }

    public static Guardada porCredencial(String credencialId) throws SQLException {
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement st = Sql.preparar(c, Compartido.consulta("llave_por_credencial"),
                        Map.of("credencial_id", credencialId));
                ResultSet rs = st.executeQuery()) {
            return rs.next() ? new Guardada(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4)) : null;
        }
    }

    /** false si esa llave ya estaba registrada. */
    public static boolean guardar(long usuarioId, String credencialId, String clavePublica, long contador,
                                  String nombre) throws SQLException {
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement st = Sql.preparar(c, Compartido.consulta("llave_nueva"), Map.of(
                        "usuario_id", usuarioId, "credencial_id", credencialId, "clave_publica", clavePublica,
                        "contador", contador, "nombre", nombre))) {
            st.execute();
            return true;
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1) { // ORA-00001 dentro del bloque PL/SQL
                return false;
            }
            throw e;
        }
    }

    public static void usar(long llaveId, long usuarioId, long contador) throws SQLException {
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement st = Sql.preparar(c, Compartido.consulta("llave_usada"),
                        Map.of("id", llaveId, "usuario_id", usuarioId, "contador", contador))) {
            st.execute();
        }
    }

    public static void eliminar(long usuarioId, long llaveId) throws SQLException {
        try (Connection c = ConexionBD.obtenerConexion();
                PreparedStatement st = Sql.preparar(c, Compartido.consulta("llave_eliminar"),
                        Map.of("id", llaveId, "usuario_id", usuarioId))) {
            st.execute();
        }
    }
}
