package com.prpagos.conexion;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Punto único de conexión a la base de datos.
 *
 * CORRECCIÓN respecto a la versión anterior: las 6 clases de la interfaz
 * (VentanaLogin, VentanaCrearUsuario, VentanaConsultaPagos, etc.) tenían
 * cada una su propia copia de la cadena de conexión, con el usuario y la
 * contraseña de la base escritos directamente en el código. Ahora hay un
 * solo lugar (esta clase), que lee la configuración de config.properties
 * (un archivo que nunca se sube a git) y se conecta usando el Wallet de
 * Oracle Cloud en vez de "localhost".
 */
public final class ConexionBD {

    private static Properties config;

    static {
        // CORRECCIÓN: fuerza el registro del driver de Oracle ante
        // DriverManager. Normalmente esto pasa solo (JDBC 4+ lo detecta
        // automáticamente vía META-INF/services), pero si el classpath no
        // quedó bien armado en el IDE, sale "No suitable driver found"
        // aunque el .jar sí esté ahí. Cargarlo a mano aquí lo garantiza.
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                "No se encontró oracle.jdbc.OracleDriver. Verifica que ojdbc17.jar "
                + "esté agregado como Referenced Library en VS Code.");
        }
    }

    private ConexionBD() {
    }

    private static Properties cargarConfig() throws IOException {
        if (config != null) {
            return config;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new IOException(
                "No se encontró config.properties. Copia config.properties.example, "
                + "renómbralo a config.properties y completa tus datos reales.", e);
        }
        config = props;
        return config;
    }

    public static Connection obtenerConexion() throws SQLException {
        try {
            Properties props = cargarConfig();
            String walletPath = props.getProperty("wallet.path");
            String dbAlias = props.getProperty("db.alias");
            String usuario = props.getProperty("db.usuario");
            String password = props.getProperty("db.password");

            // Le indica al driver JDBC dónde está el Wallet (certificados
            // de conexión segura) descargado desde Oracle Cloud.
            System.setProperty("oracle.net.tns_admin", walletPath);

            String url = "jdbc:oracle:thin:@" + dbAlias;
            return DriverManager.getConnection(url, usuario, password);
        } catch (IOException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }
}
