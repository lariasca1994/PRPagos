package com.prpagos.ui;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Selector de fecha tipo calendario desplegable, hecho con Swing puro
 * (sin depender de ninguna librería externa nueva). Reemplaza el campo
 * de texto donde antes había que escribir la fecha a mano — eso obligaba
 * a que el formato fuera exacto (dd/MM/yyyy) y era fácil equivocarse.
 */
public class SelectorFecha extends JPanel {

    private final JButton botonFecha;
    private LocalDate fechaSeleccionada;
    private YearMonth mesMostrado;

    public SelectorFecha() {
        this(LocalDate.now());
    }

    public SelectorFecha(LocalDate fechaInicial) {
        this.fechaSeleccionada = fechaInicial;
        this.mesMostrado = YearMonth.from(fechaInicial);

        setLayout(new BorderLayout());
        botonFecha = new JButton(formatear(fechaSeleccionada));
        botonFecha.addActionListener(e -> mostrarCalendario());
        add(botonFecha, BorderLayout.CENTER);
    }

    private String formatear(LocalDate fecha) {
        return String.format("%02d/%02d/%04d", fecha.getDayOfMonth(), fecha.getMonthValue(), fecha.getYear());
    }

    public LocalDate getFechaSeleccionada() {
        return fechaSeleccionada;
    }

    public java.sql.Date getFechaSql() {
        return java.sql.Date.valueOf(fechaSeleccionada);
    }

    public void setFechaSeleccionada(LocalDate fecha) {
        this.fechaSeleccionada = fecha;
        this.mesMostrado = YearMonth.from(fecha);
        botonFecha.setText(formatear(fecha));
    }

    private void mostrarCalendario() {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        JPanel panelCalendario = construirPanelCalendario(popup);
        popup.add(panelCalendario, BorderLayout.CENTER);
        popup.show(botonFecha, 0, botonFecha.getHeight());
    }

    private JPanel construirPanelCalendario(JPopupMenu popup) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel encabezado = new JPanel(new BorderLayout());
        JButton btnAnterior = new JButton("<");
        JButton btnSiguiente = new JButton(">");
        JLabel lblMesAno = new JLabel("", SwingConstants.CENTER);
        actualizarLabelMes(lblMesAno);

        JPanel panelDias = new JPanel(new GridLayout(0, 7, 2, 2));

        btnAnterior.addActionListener(e -> {
            mesMostrado = mesMostrado.minusMonths(1);
            actualizarLabelMes(lblMesAno);
            actualizarDias(panelDias, popup);
        });
        btnSiguiente.addActionListener(e -> {
            mesMostrado = mesMostrado.plusMonths(1);
            actualizarLabelMes(lblMesAno);
            actualizarDias(panelDias, popup);
        });

        encabezado.add(btnAnterior, BorderLayout.WEST);
        encabezado.add(lblMesAno, BorderLayout.CENTER);
        encabezado.add(btnSiguiente, BorderLayout.EAST);

        actualizarDias(panelDias, popup);

        panel.add(encabezado, BorderLayout.NORTH);
        panel.add(panelDias, BorderLayout.CENTER);
        return panel;
    }

    private void actualizarLabelMes(JLabel label) {
        String nombreMes = mesMostrado.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        label.setText(capitalizar(nombreMes) + " " + mesMostrado.getYear());
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
    }

    private void actualizarDias(JPanel panelDias, JPopupMenu popup) {
        panelDias.removeAll();

        String[] encabezados = {"L", "M", "X", "J", "V", "S", "D"};
        for (String dia : encabezados) {
            JLabel lbl = new JLabel(dia, SwingConstants.CENTER);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            panelDias.add(lbl);
        }

        LocalDate primerDiaMes = mesMostrado.atDay(1);
        int diaSemanaInicio = primerDiaMes.getDayOfWeek().getValue(); // 1=lunes .. 7=domingo

        for (int i = 1; i < diaSemanaInicio; i++) {
            panelDias.add(new JLabel(""));
        }

        int diasEnMes = mesMostrado.lengthOfMonth();
        for (int dia = 1; dia <= diasEnMes; dia++) {
            LocalDate fechaDia = mesMostrado.atDay(dia);
            JButton btnDia = new JButton(String.valueOf(dia));
            btnDia.setMargin(new Insets(2, 2, 2, 2));
            if (fechaDia.equals(fechaSeleccionada)) {
                btnDia.setBackground(new Color(0x46, 0x82, 0xB4));
                btnDia.setForeground(Color.WHITE);
            }
            btnDia.addActionListener(e -> {
                setFechaSeleccionada(fechaDia);
                popup.setVisible(false);
            });
            panelDias.add(btnDia);
        }

        panelDias.revalidate();
        panelDias.repaint();
        popup.pack();
    }
}
