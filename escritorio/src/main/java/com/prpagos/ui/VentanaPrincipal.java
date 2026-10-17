package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.formdev.flatlaf.FlatClientProperties;
import com.prpagos.datos.Pago;
import com.prpagos.datos.Sesion;

/**
 * Ventana principal: misma estructura que las páginas de la web (base.html).
 * Barra superior con la marca, el botón de tema y el menú en el mismo orden
 * (Consultar registros · Nuevo registro · Seguridad · Salir), el contenido al
 * centro con un ancho máximo y el pie.
 */
public class VentanaPrincipal extends JFrame {

    static final String CONSULTA = "consulta";
    static final String NUEVO = "nuevo";
    static final String EDITAR = "editar";
    static final String SEGURIDAD = "seguridad";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final Map<String, JButton> menu = new LinkedHashMap<>();
    private final PanelConsulta consulta;
    private final PanelPago nuevo;
    private final PanelPago editar;
    private final PanelSeguridad seguridad;
    private String actual;

    public VentanaPrincipal(Sesion sesion) {
        super("PRPagos — " + sesion.nombre());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        consulta = new PanelConsulta(this, sesion);
        nuevo = PanelPago.nuevo(this, sesion);
        editar = PanelPago.edicion(this, sesion);
        seguridad = new PanelSeguridad(sesion);

        contenido.setOpaque(false);
        contenido.add(desplazable(consulta), CONSULTA);
        contenido.add(desplazable(nuevo), NUEVO);
        contenido.add(desplazable(editar), EDITAR);
        contenido.add(desplazable(seguridad), SEGURIDAD);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(barra(sesion), BorderLayout.NORTH);
        raiz.add(contenido, BorderLayout.CENTER);
        raiz.add(pie(), BorderLayout.SOUTH);
        setContentPane(raiz);

        ir(CONSULTA);
    }

    private JPanel barra(Sesion sesion) {
        JPanel barra = new JPanel(new BorderLayout());
        Tema.alCambiar(() -> {
            barra.setBackground(Tema.c().superficie());
            barra.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.c().borde()),
                    BorderFactory.createEmptyBorder(12, 28, 12, 28)));
        });
        JPanel marca = Ui.fila(12);
        marca.add(Ui.marca());
        marca.add(Ui.botonTema());
        barra.add(marca, BorderLayout.WEST);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        nav.setOpaque(false);
        menu.put(CONSULTA, opcion("Consultar registros", () -> ir(CONSULTA)));
        menu.put(NUEVO, opcion("Nuevo registro", () -> ir(NUEVO)));
        menu.put(SEGURIDAD, opcion("Seguridad", () -> ir(SEGURIDAD)));
        menu.values().forEach(nav::add);
        nav.add(opcion("Salir (" + sesion.nombre() + ")", this::salir));
        barra.add(nav, BorderLayout.EAST);
        Tema.alCambiar(this::marcarMenu);
        return barra;
    }

    private JButton opcion(String texto, Runnable accion) {
        JButton b = new JButton(texto);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusPainted(false);
        b.addActionListener(e -> accion.run());
        b.setForeground(Tema.c().textoSuave());
        return b;
    }

    /** La sección actual en rojo, en negrita y subrayada (.nav-principal a.activo). */
    private void marcarMenu() {
        menu.forEach((clave, boton) -> {
            boolean activo = clave.equals(actual) || (EDITAR.equals(actual) && CONSULTA.equals(clave));
            boton.setForeground(activo ? Tema.c().plataforma() : Tema.c().textoSuave());
            boton.setFont(boton.getFont().deriveFont(activo ? Font.BOLD : Font.PLAIN));
            boton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(6, 2, 0, 2),
                    BorderFactory.createMatteBorder(0, 0, 2, 0,
                            activo ? Tema.c().plataforma() : new java.awt.Color(0, 0, 0, 0))));
        });
    }

    private JScrollPane desplazable(JPanel panel) {
        JPanel margen = new JPanel(new BorderLayout());
        margen.setOpaque(false);
        margen.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        margen.add(Ui.centrado(panel, 960), BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(margen);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Tema.alCambiar(() -> scroll.getViewport().setBackground(Tema.c().fondo()));
        return scroll;
    }

    private JPanel pie() {
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pie.setBorder(BorderFactory.createEmptyBorder(10, 0, 14, 0));
        javax.swing.JLabel texto = new javax.swing.JLabel("PRPagos — proyecto de portafolio de Luis Felipe Arias.");
        texto.setFont(texto.getFont().deriveFont(12.5f));
        Tema.alCambiar(() -> texto.setForeground(Tema.c().textoSuave()));
        pie.add(texto);
        return pie;
    }

    void ir(String seccion) {
        actual = seccion;
        if (CONSULTA.equals(seccion)) {
            consulta.recargar();
        } else if (NUEVO.equals(seccion)) {
            nuevo.limpiar();
        } else if (SEGURIDAD.equals(seccion)) {
            seguridad.recargar();
        }
        tarjetas.show(contenido, seccion);
        marcarMenu();
    }

    void editar(Pago pago) {
        editar.cargar(pago);
        actual = EDITAR;
        tarjetas.show(contenido, EDITAR);
        marcarMenu();
    }

    /** Volver a la consulta tras guardar, con el aviso de éxito arriba. */
    void volverAConsulta(String mensaje) {
        ir(CONSULTA);
        consulta.avisar(mensaje);
    }

    private void salir() {
        new VentanaLogin().setVisible(true);
        dispose();
    }
}
