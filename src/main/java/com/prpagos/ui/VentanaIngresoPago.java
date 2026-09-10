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
import javax.swing.JTextField;

import com.prpagos.conexion.ConexionBD;

public class VentanaIngresoPago extends JFrame {
    private JTextField txtMonto;
    private SelectorFecha selectorFecha;
    private JTextField txtConcepto;
    private JButton btnAgregar;
    private JButton btnVolver;
    private final long usuarioId;

    public VentanaIngresoPago(long usuarioId) {
        super("Ingresar Pago");
        this.usuarioId = usuarioId;
        setSize(320, 180);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        JLabel lblMonto = new JLabel("Monto:");
        txtMonto = new JTextField();
        JLabel lblFecha = new JLabel("Fecha:");
        selectorFecha = new SelectorFecha();
        JLabel lblConcepto = new JLabel("Concepto:");
        txtConcepto = new JTextField();
        btnAgregar = new JButton("Agregar");
        btnVolver = new JButton("Volver");

        btnAgregar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ingresarPago();
            }
        });
        btnVolver.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.add(lblMonto);
        panel.add(txtMonto);
        panel.add(lblFecha);
        panel.add(selectorFecha);
        panel.add(lblConcepto);
        panel.add(txtConcepto);
        panel.add(btnAgregar);
        panel.add(btnVolver);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void ingresarPago() {
        try {
            double monto = Double.parseDouble(txtMonto.getText());
            java.sql.Date sqlFecha = selectorFecha.getFechaSql();
            String concepto = txtConcepto.getText();

            try (Connection conn = ConexionBD.obtenerConexion()) {
                CallableStatement stmt = conn.prepareCall("{CALL insertar_tbpago(?, ?, ?, ?)}");
                stmt.setDouble(1, monto);
                stmt.setDate(2, sqlFecha);
                stmt.setString(3, concepto);
                stmt.setLong(4, usuarioId);
                stmt.execute();
                stmt.close();
            }

            JOptionPane.showMessageDialog(this, "Pago ingresado correctamente.");
            dispose();
        } catch (NumberFormatException | SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al ingresar el pago: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
