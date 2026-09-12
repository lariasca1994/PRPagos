package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.prpagos.conexion.ConexionBD;
import com.prpagos.seguridad.Seguridad;

public class VentanaLogin extends JFrame {
    private JTextField txtUsuario;
    private JPasswordField txtContrasena;
    private JButton btnIngresar;
    private JButton btnSalir;
    private JButton btnNuevoUsuario;
    private JButton btnOlvidarContrasena;

    public VentanaLogin() {
        super("Login");
        setSize(400, 250);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        JLabel lblUsuario = new JLabel("Usuario:");
        txtUsuario = new JTextField();
        JLabel lblContrasena = new JLabel("Contraseña:");
        txtContrasena = new JPasswordField();
        btnIngresar = new JButton("Ingresar");
        btnSalir = new JButton("Salir");
        btnNuevoUsuario = new JButton("Crear Usuario");
        btnOlvidarContrasena = new JButton("Olvidé mi contraseña");

        btnIngresar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                login();
            }
        });
        btnSalir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        btnNuevoUsuario.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                abrirVentanaCrearUsuario();
            }
        });
        btnOlvidarContrasena.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                abrirVentanaRecuperarContrasena();
            }
        });

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(lblUsuario);
        panel.add(txtUsuario);
        panel.add(lblContrasena);
        panel.add(txtContrasena);
        panel.add(btnIngresar);
        panel.add(btnSalir);
        panel.add(btnNuevoUsuario);
        panel.add(btnOlvidarContrasena);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void login() {
        String usuario = txtUsuario.getText();
        // CORRECCIÓN: se cifra antes de enviarla — el procedimiento
        // validar_login ahora compara hash contra hash, nunca texto plano.
        String contrasenaHash = Seguridad.hashPassword(new String(txtContrasena.getPassword()));

        try (Connection conn = ConexionBD.obtenerConexion()) {
            CallableStatement stmt = conn.prepareCall("{CALL validar_login(?, ?, ?, ?)}");
            stmt.setString(1, usuario);
            stmt.setString(2, contrasenaHash);
            stmt.registerOutParameter(3, java.sql.Types.VARCHAR);
            stmt.registerOutParameter(4, java.sql.Types.NUMERIC);
            stmt.execute();

            String nombre = stmt.getString(3);
            long usuarioId = stmt.getLong(4);
            if (nombre != null) {
                JOptionPane.showMessageDialog(this, "Bienvenido, " + nombre + ".");
                VentanaPrincipal ventanaPrincipal = new VentanaPrincipal(nombre, usuarioId);
                ventanaPrincipal.setVisible(true);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Usuario o contraseña incorrectos.", "Error", JOptionPane.ERROR_MESSAGE);
            }
            stmt.close();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al conectar a la base de datos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirVentanaCrearUsuario() {
        VentanaCrearUsuario ventanaCrearUsuario = new VentanaCrearUsuario();
        ventanaCrearUsuario.setVisible(true);
    }

    private void abrirVentanaRecuperarContrasena() {
        VentanaRecuperarContrasena ventanaRecuperarContrasena = new VentanaRecuperarContrasena();
        ventanaRecuperarContrasena.setVisible(true);
    }

    public static void main(String[] args) {
        VentanaLogin ventanaLogin = new VentanaLogin();
        ventanaLogin.setVisible(true);
    }
}
