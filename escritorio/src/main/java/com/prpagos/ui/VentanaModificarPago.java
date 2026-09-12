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

public class VentanaModificarPago extends JFrame {
    private JTextField txtMonto;
    private SelectorFecha selectorFecha;
    private JTextField txtConcepto;
    private JButton btnModificar;
    private JButton btnVolver;
    private int idPago;
    private final long usuarioId;
    private VentanaConsultaPagos ventanaConsultaPagos;

    public VentanaModificarPago(int idPago, double montoActual, java.sql.Date fechaActual, String conceptoActual, long usuarioId, VentanaConsultaPagos ventanaConsultaPagos) {
        super("Modificar Pago");
        setSize(320, 250);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        this.idPago = idPago;
        this.usuarioId = usuarioId;
        this.ventanaConsultaPagos = ventanaConsultaPagos;

        JLabel lblMonto = new JLabel("Monto:");
        txtMonto = new JTextField(String.valueOf(montoActual));
        JLabel lblFecha = new JLabel("Fecha:");
        selectorFecha = new SelectorFecha(fechaActual.toLocalDate());
        JLabel lblConcepto = new JLabel("Concepto:");
        txtConcepto = new JTextField(conceptoActual);
        btnModificar = new JButton("Modificar");
        btnVolver = new JButton("Volver");

        btnModificar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                modificarPago();
            }
        });
        btnVolver.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panel = new JPanel(new GridLayout(5, 2, 10, 10));
        panel.add(lblMonto);
        panel.add(txtMonto);
        panel.add(lblFecha);
        panel.add(selectorFecha);
        panel.add(lblConcepto);
        panel.add(txtConcepto);
        panel.add(btnModificar);
        panel.add(btnVolver);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void modificarPago() {
        try {
            double nuevoMonto = Double.parseDouble(txtMonto.getText());
            java.sql.Date nuevoFecha = selectorFecha.getFechaSql();
            String nuevoConcepto = txtConcepto.getText();

            int filasAfectadas;
            try (Connection conn = ConexionBD.obtenerConexion()) {
                CallableStatement stmt = conn.prepareCall("{CALL actualizar_tbpago(?, ?, ?, ?, ?, ?)}");
                stmt.setInt(1, idPago);
                stmt.setDouble(2, nuevoMonto);
                stmt.setDate(3, nuevoFecha);
                stmt.setString(4, nuevoConcepto);
                stmt.setLong(5, usuarioId);
                stmt.registerOutParameter(6, java.sql.Types.NUMERIC);
                stmt.execute();
                filasAfectadas = stmt.getInt(6);
                stmt.close();
            }

            // CORRECCION: si el pago no pertenece a este usuario, la
            // actualizacion no afecta ninguna fila -- se informa en vez
            // de mostrar "éxito" sobre un pago que no era suyo.
            if (filasAfectadas == 0) {
                JOptionPane.showMessageDialog(this, "No se pudo modificar: este pago no te pertenece.", "No autorizado", JOptionPane.ERROR_MESSAGE);
                return;
            }

            JOptionPane.showMessageDialog(this, "Pago modificado correctamente.");
            ventanaConsultaPagos.actualizarListaDePagos();
            dispose();
        } catch (NumberFormatException | SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error al modificar el pago: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
