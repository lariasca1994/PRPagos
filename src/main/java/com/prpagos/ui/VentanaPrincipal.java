package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class VentanaPrincipal extends JFrame {
    private JButton btnConsultar;
    private JButton btnIngresar;
    private JButton btnSalir;
    private final long usuarioId;

    public VentanaPrincipal(String nombre, long usuarioId) {
        super("Sistema de Pagos — " + nombre);
        this.usuarioId = usuarioId;
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        btnConsultar = new JButton("Consultar Pagos");
        btnIngresar = new JButton("Ingresar Pago");
        btnSalir = new JButton("Salir");

        btnConsultar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                abrirVentanaConsultaPagos();
            }
        });

        btnIngresar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                abrirVentanaIngresoPago();
            }
        });

        btnSalir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        JPanel panel = new JPanel(new GridLayout(3, 1));
        panel.add(btnConsultar);
        panel.add(btnIngresar);
        panel.add(btnSalir);

        getContentPane().add(panel, BorderLayout.CENTER);
    }

    private void abrirVentanaConsultaPagos() {
        VentanaConsultaPagos ventanaConsultaPagos = new VentanaConsultaPagos(usuarioId);
        ventanaConsultaPagos.setVisible(true);
    }

    private void abrirVentanaIngresoPago() {
        VentanaIngresoPago ventanaIngresoPago = new VentanaIngresoPago(usuarioId);
        ventanaIngresoPago.setVisible(true);
    }
}
