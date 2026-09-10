package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

/**
 * CORRECCIÓN de seguridad respecto a la versión anterior: antes, con solo
 * escribir un correo que existiera en el sistema, se podía poner una
 * contraseña nueva a esa cuenta — sin ninguna verificación de que quien
 * lo pedía fuera el dueño real. Cualquiera podía tomar el control de la
 * cuenta de otra persona con solo saber (o adivinar) su correo.
 *
 * Aquí se agrega una verificación mínima real: además del correo, hay que
 * saber el nombre exacto con el que se registró esa cuenta. No es tan
 * fuerte como un token de un solo uso enviado por correo (lo correcto en
 * un sistema real), pero cierra el hueco más grave para el alcance de
 * este proyecto — y queda documentado como una simplificación consciente.
 */
public class VentanaRecuperarContrasena extends JFrame {
    private static final Pattern PATRON_CORREO = Pattern.compile("^[a-zA-Z0-9_.+-]+@EAN\\.com$", Pattern.CASE_INSENSITIVE);

    private JTextField txtCorreo;
    private JTextField txtNombreRegistrado;
    private JPasswordField txtNuevaContrasena;
    private JButton btnEnviar;
    private JButton btnCancelar;

    public VentanaRecuperarContrasena() {
        super("Recuperar Contraseña");
        setSize(420, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        JLabel lblCorreo = new JLabel("Correo:");
        txtCorreo = new JTextField();
        JLabel lblNombreRegistrado = new JLabel("Nombre con el que te registraste:");
        txtNombreRegistrado = new JTextField();
        JLabel lblNuevaContrasena = new JLabel("Nueva Contraseña:");
        txtNuevaContrasena = new JPasswordField();
        btnEnviar = new JButton("Enviar");
        btnCancelar = new JButton("Cancelar");

        btnEnviar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                recuperarContrasena();
            }
        });
        btnCancelar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.add(lblCorreo);
        panel.add(txtCorreo);
        panel.add(lblNombreRegistrado);
        panel.add(txtNombreRegistrado);
        panel.add(lblNuevaContrasena);
        panel.add(txtNuevaContrasena);
        panel.add(btnEnviar);
        panel.add(btnCancelar);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void recuperarContrasena() {
        String correo = txtCorreo.getText();
        String nombreIngresado = txtNombreRegistrado.getText();
        String nuevaContrasena = new String(txtNuevaContrasena.getPassword());

        if (!validarCorreo(correo)) {
            JOptionPane.showMessageDialog(this, "El correo electrónico no es válido.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try (Connection conn = ConexionBD.obtenerConexion()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT nombre FROM TBPLogin WHERE usuario = ?");
            stmt.setString(1, correo);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String nombreRegistrado = rs.getString("nombre");
                // Verificación real: el nombre debe coincidir con el registrado.
                if (!nombreRegistrado.equalsIgnoreCase(nombreIngresado)) {
                    JOptionPane.showMessageDialog(this, "El nombre no coincide con el registrado para ese correo.", "Error", JOptionPane.ERROR_MESSAGE);
                    rs.close();
                    stmt.close();
                    return;
                }

                PreparedStatement updateStmt = conn.prepareStatement("UPDATE TBPLogin SET password = ? WHERE usuario = ?");
                updateStmt.setString(1, Seguridad.hashPassword(nuevaContrasena));
                updateStmt.setString(2, correo);
                int rowsAffected = updateStmt.executeUpdate();
                updateStmt.close();

                if (rowsAffected == 1) {
                    JOptionPane.showMessageDialog(this, "Contraseña actualizada exitosamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Error al actualizar la contraseña.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "El correo electrónico no está registrado.", "Error", JOptionPane.ERROR_MESSAGE);
            }

            rs.close();
            stmt.close();
            dispose();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al recuperar la contraseña: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean validarCorreo(String correo) {
        Matcher matcher = PATRON_CORREO.matcher(correo);
        return matcher.matches();
    }
}
