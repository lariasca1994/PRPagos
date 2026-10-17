package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Selector de fecha tipo calendario desplegable, hecho con Swing puro.
 * Opcional (para los filtros "Desde" y "Hasta", que en la web pueden quedar
 * vacíos): entonces muestra "dd/mm/aaaa" y el calendario trae "Borrar".
 */
public class SelectorFecha extends JPanel {

    private final JButton botonFecha;
    private final boolean opcional;
    private LocalDate fechaSeleccionada;
    private YearMonth mesMostrado;

    public SelectorFecha() {
        this(LocalDate.now(), false);
    }

    public SelectorFecha(LocalDate fechaInicial) {
        this(fechaInicial, false);
    }

    /** Selector que admite quedar vacío (null). */
    public static SelectorFecha opcional() {
        return new SelectorFecha(null, true);
    }

    private SelectorFecha(LocalDate fechaInicial, boolean opcional) {
        this.opcional = opcional;
        this.fechaSeleccionada = fechaInicial;
        this.mesMostrado = YearMonth.from(fechaInicial != null ? fechaInicial : LocalDate.now());
        setLayout(new BorderLayout());
        setOpaque(false);
        botonFecha = new JButton();
        botonFecha.setHorizontalAlignment(SwingConstants.LEFT);
        botonFecha.putClientProperty(FlatClientProperties.STYLE, "margin: 6,10,6,10");
        botonFecha.addActionListener(e -> mostrarCalendario());
        add(botonFecha, BorderLayout.CENTER);
        setPreferredSize(new Dimension(160, 38));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        setAlignmentX(LEFT_ALIGNMENT);
        actualizarBoton();
        Tema.alCambiar(this::colorearBoton);
    }

    public LocalDate getFechaSeleccionada() {
        return fechaSeleccionada;
    }

    public void setFechaSeleccionada(LocalDate fecha) {
        if (fecha == null && !opcional) {
            fecha = LocalDate.now();
        }
        this.fechaSeleccionada = fecha;
        if (fecha != null) {
            this.mesMostrado = YearMonth.from(fecha);
        }
        actualizarBoton();
    }

    private void actualizarBoton() {
        if (fechaSeleccionada == null) {
            botonFecha.setText("dd/mm/aaaa");
        } else {
            botonFecha.setText(String.format("%02d/%02d/%04d", fechaSeleccionada.getDayOfMonth(),
                    fechaSeleccionada.getMonthValue(), fechaSeleccionada.getYear()));
        }
        colorearBoton();
    }

    private void colorearBoton() {
        botonFecha.setForeground(fechaSeleccionada == null ? Tema.c().textoSuave() : Tema.c().texto());
        botonFecha.setBackground(Tema.c().fondo());
    }

    private void mostrarCalendario() {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.add(construirPanelCalendario(popup), BorderLayout.CENTER);
        popup.show(botonFecha, 0, botonFecha.getHeight());
    }

    private JPanel construirPanelCalendario(JPopupMenu popup) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JButton btnAnterior = Ui.enlace("‹", null);
        JButton btnSiguiente = Ui.enlace("›", null);
        btnAnterior.setFont(btnAnterior.getFont().deriveFont(Font.BOLD, 18f));
        btnSiguiente.setFont(btnSiguiente.getFont().deriveFont(Font.BOLD, 18f));
        JLabel lblMesAno = new JLabel("", SwingConstants.CENTER);
        lblMesAno.setFont(lblMesAno.getFont().deriveFont(Font.BOLD));
        actualizarLabelMes(lblMesAno);

        JPanel panelDias = new JPanel(new GridLayout(0, 7, 2, 2));
        panelDias.setOpaque(false);

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
        JPanel pie = Ui.fila(8);
        pie.add(Ui.enlace("Hoy", e -> {
            setFechaSeleccionada(LocalDate.now());
            popup.setVisible(false);
        }));
        if (opcional) {
            pie.add(Ui.enlace("Borrar", e -> {
                setFechaSeleccionada(null);
                popup.setVisible(false);
            }));
        }
        panel.add(pie, BorderLayout.SOUTH);
        return panel;
    }

    private void actualizarLabelMes(JLabel label) {
        String nombreMes = mesMostrado.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"));
        label.setText(nombreMes.substring(0, 1).toUpperCase() + nombreMes.substring(1) + " "
                + mesMostrado.getYear());
    }

    private void actualizarDias(JPanel panelDias, JPopupMenu popup) {
        panelDias.removeAll();
        for (String dia : new String[] {"L", "M", "X", "J", "V", "S", "D"}) {
            JLabel lbl = new JLabel(dia, SwingConstants.CENTER);
            lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
            lbl.setForeground(Tema.c().textoSuave());
            panelDias.add(lbl);
        }
        int diaSemanaInicio = mesMostrado.atDay(1).getDayOfWeek().getValue(); // 1=lunes .. 7=domingo
        for (int i = 1; i < diaSemanaInicio; i++) {
            panelDias.add(new JLabel(""));
        }
        for (int dia = 1; dia <= mesMostrado.lengthOfMonth(); dia++) {
            LocalDate fechaDia = mesMostrado.atDay(dia);
            JButton btnDia = new JButton(String.valueOf(dia));
            btnDia.setMargin(new Insets(4, 4, 4, 4));
            if (fechaDia.equals(fechaSeleccionada)) {
                btnDia.putClientProperty(FlatClientProperties.STYLE_CLASS, "primario");
                btnDia.putClientProperty(FlatClientProperties.STYLE, "margin: 4,4,4,4");
            } else {
                btnDia.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                        FlatClientProperties.BUTTON_TYPE_BORDERLESS);
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
