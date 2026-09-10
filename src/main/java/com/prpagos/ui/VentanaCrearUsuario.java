package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.prpagos.conexion.ConexionBD;
import com.prpagos.seguridad.Seguridad;

public class VentanaCrearUsuario extends JFrame {
    private static final Pattern PATRON_CORREO = Pattern.compile("^[a-zA-Z0-9_.+-]+@EAN\\.com$", Pattern.CASE_INSENSITIVE);

    private JTextField txtUsuario;
    private JTextField txtNombre;
    private JPasswordField txtContrasena;
    private JButton btnCrear;
    private JButton btnCancelar;

    public VentanaCrearUsuario() {
        super("Crear Usuario");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        JLabel lblUsuario = new JLabel("Usuario (correo @EAN.com):");
        txtUsuario = new JTextField();
        JLabel lblNombre = new JLabel("Nombre:");
        txtNombre = new JTextField();
        JLabel lblContrasena = new JLabel("Contraseña:");
        txtContrasena = new JPasswordField();
        btnCrear = new JButton("Crear");
        btnCancelar = new JButton("Cancelar");

        btnCrear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                crearUsuario();
            }
        });
        btnCancelar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.add(lblUsuario);
        panel.add(txtUsuario);
        panel.add(lblNombre);
        panel.add(txtNombre);
        panel.add(lblContrasena);
        panel.add(txtContrasena);
        panel.add(btnCrear);
        panel.add(btnCancelar);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void crearUsuario() {
        String usuario = txtUsuario.getText();
        String nombre = txtNombre.getText();
        String contrasena = new String(txtContrasena.getPassword());

        // CORRECCIÓN: antes no se validaba el formato del usuario aquí,
        // pero "recuperar contraseña" sí exigía que fuera un correo
        // @EAN.com — quedaba inconsistente. Ahora se exige desde la
        // creación.
        Matcher matcher = PATRON_CORREO.matcher(usuario);
        if (!matcher.matches()) {
            JOptionPane.showMessageDialog(this, "El usuario debe ser un correo válido @EAN.com", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = ConexionBD.obtenerConexion()) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO TBPLogin (id, usuario, nombre, password) VALUES (tblogin_sequence.NEXTVAL, ?, ?, ?)");
            stmt.setString(1, usuario);
            stmt.setString(2, nombre);
            // CORRECCIÓN: se guarda el hash, nunca la contraseña real.
            stmt.setString(3, Seguridad.hashPassword(contrasena));
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 1) {
                JOptionPane.showMessageDialog(this, "Usuario creado exitosamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Error al crear el usuario.", "Error", JOptionPane.ERROR_MESSAGE);
            }
            stmt.close();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al crear el usuario: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
