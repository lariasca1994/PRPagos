package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;

import com.formdev.flatlaf.FlatClientProperties;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.datos.Pago;
import com.prpagos.datos.Sesion;

/**
 * Ventana principal: misma estructura que las páginas de la web (base.html).
 * Barra superior con la marca, el botón de tema y el menú en el mismo orden
 * (Dashboard · Presupuesto · Consultar registros · Nuevo registro · Perfil · Usuarios
 * (admin) · Salir), el contenido al centro con un ancho máximo y el pie.
 *
 * La sesión la controla el servidor, igual que en la web: 6 horas sin uso
 * (cada petición renueva el plazo) y como máximo 8 horas. Cuando el plazo del
 * token vence, o cuando cualquier petición responde que la sesión ya no vale
 * (cuenta suspendida, por ejemplo), se vuelve al ingreso con el mensaje que da
 * el servidor. En cada cambio de pantalla se pide la cuenta a la API, así que un
 * cambio de rol tiene efecto inmediato.
 */
public class VentanaPrincipal extends JFrame {

    static final String DASHBOARD = "dashboard";
    static final String PRESUPUESTO = "presupuesto";
    static final String CONSULTA = "consulta";
    static final String NUEVO = "nuevo";
    static final String EDITAR = "editar";
    static final String PERFIL = "perfil";
    static final String USUARIOS = "usuarios";

    /** El mismo texto que MENSAJES_CIERRE["salir"] en la web. */
    static final String CIERRE_SALIR = "Cerraste sesión.";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final Map<String, JButton> menu = new LinkedHashMap<>();
    private final PanelConsulta consulta;
    private final PanelPago nuevo;
    private final PanelPago editar;
    private final PanelDashboard dashboard;
    private final PanelPresupuesto presupuesto;
    private final PanelPerfil perfil;
    private final PanelUsuarios usuarios;
    /** Revisa cada 30 s si el token ya venció; entonces el servidor da el motivo del cierre. */
    private final Timer vigilancia;
    private boolean cerrada;
    private Sesion sesion;
    private String actual;
    private final Map<String, JScrollPane> desplazables = new java.util.HashMap<>();
    /** Al cambiar de sección, la vista queda arriba aunque la carga devuelva el foco a un campo. */
    private long anclarArribaHasta;

    public VentanaPrincipal(Sesion sesion) {
        super("PRPagos — " + sesion.nombre());
        this.sesion = sesion;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 780);
        // Admite tablets de 10" de pie (unos 800 px de ancho): el menú baja a una segunda fila y
        // las rejillas pasan a 2 o 1 columna (Ui.adaptable).
        setMinimumSize(new Dimension(480, 560));
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        consulta = new PanelConsulta(this);
        nuevo = PanelPago.nuevo(this);
        editar = PanelPago.edicion(this);
        dashboard = new PanelDashboard(this);
        presupuesto = new PanelPresupuesto(this);
        perfil = new PanelPerfil(this);
        usuarios = new PanelUsuarios(this);

        contenido.setOpaque(false);
        for (Object[] s : new Object[][] {{dashboard, DASHBOARD}, {presupuesto, PRESUPUESTO}, {consulta, CONSULTA}, {nuevo, NUEVO},
            {editar, EDITAR}, {perfil, PERFIL}, {usuarios, USUARIOS}}) {
            JScrollPane scroll = desplazable((JPanel) s[0]);
            desplazables.put((String) s[1], scroll);
            contenido.add(scroll, (String) s[1]);
        }

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(barra(), BorderLayout.NORTH);
        raiz.add(contenido, BorderLayout.CENTER);
        raiz.add(pie(), BorderLayout.SOUTH);
        setContentPane(raiz);

        // Si el servidor da la sesión por cerrada (en cualquier petición), se vuelve al ingreso.
        Api.alCerrarSesion(mensaje -> javax.swing.SwingUtilities.invokeLater(() -> cerrarSesion(mensaje, false)));
        vigilancia = new Timer(30_000, e -> {
            if (Api.vence() > 0 && System.currentTimeMillis() / 1000 >= Api.vence()) {
                Tarea.ejecutar(contenido, () -> Api.get("/api/sesion"), r -> { }, err -> { });
            }
        });
        vigilancia.start();

        mostrar(DASHBOARD);
    }

    Sesion sesion() {
        return sesion;
    }

    private JPanel barra() {
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

        JPanel nav = new JPanel(new FilaQueSeParte(FlowLayout.RIGHT, 20, 0));
        nav.setOpaque(false);
        menu.put(DASHBOARD, opcion("Dashboard", () -> ir(DASHBOARD)));
        menu.put(PRESUPUESTO, opcion("Presupuesto", () -> ir(PRESUPUESTO)));
        menu.put(CONSULTA, opcion("Consultar registros", () -> ir(CONSULTA)));
        menu.put(NUEVO, opcion("Nuevo registro", () -> ir(NUEVO)));
        menu.put(PERFIL, opcion("Perfil", () -> ir(PERFIL)));
        menu.put(USUARIOS, opcion("Usuarios (admin)", () -> ir(USUARIOS)));
        menu.values().forEach(nav::add);
        nav.add(opcion("Salir (" + sesion.nombre() + ")", () -> cerrarSesion(CIERRE_SALIR, true)));
        barra.add(nav, BorderLayout.EAST);
        // En ventanas angostas (tablet de pie) el menú baja debajo de la marca, como la web.
        barra.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                java.awt.Insets m = barra.getInsets();
                int disponible = barra.getWidth() - m.left - m.right;
                int enUnaFila = marca.getPreferredSize().width + 24 + ((FilaQueSeParte) nav.getLayout()).anchoEnUnaFila(nav);
                boolean abajo = disponible < enUnaFila;
                Object lugar = ((BorderLayout) barra.getLayout()).getConstraints(nav);
                if (abajo == BorderLayout.SOUTH.equals(lugar)) {
                    return;
                }
                barra.remove(nav);
                ((FlowLayout) nav.getLayout()).setAlignment(abajo ? FlowLayout.LEFT : FlowLayout.RIGHT);
                nav.setBorder(abajo ? BorderFactory.createEmptyBorder(8, -20, 0, 0) : null);
                barra.add(nav, abajo ? BorderLayout.SOUTH : BorderLayout.EAST);
                barra.revalidate();
                barra.repaint();
            }
        });
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

    /** La sección actual en azul, en negrita y subrayada (.nav-principal a.activo). */
    private void marcarMenu() {
        if (sesion == null) {
            return;
        }
        menu.get(USUARIOS).setVisible(sesion.admin());
        menu.forEach((clave, boton) -> {
            boolean activo = clave.equals(actual) || (EDITAR.equals(actual) && CONSULTA.equals(clave));
            boton.setForeground(activo ? Tema.c().plataforma() : Tema.c().textoSuave());
            boton.setFont(boton.getFont().deriveFont(activo ? Font.BOLD : Font.PLAIN));
            boton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(6, 2, 0, 2),
                    BorderFactory.createMatteBorder(0, 0, 2, 0,
                            activo ? Tema.c().plataforma() : new Color(0, 0, 0, 0))));
        });
    }

    private JScrollPane desplazable(JPanel panel) {
        JPanel margen = new MargenDesplazable() {
            @Override
            public void scrollRectToVisible(java.awt.Rectangle r) {
                if (System.currentTimeMillis() >= anclarArribaHasta) {
                    super.scrollRectToVisible(r);
                }
            }
        };
        margen.setOpaque(false);
        margen.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        margen.add(Ui.centrado(panel, 960), BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(margen);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Tema.alCambiar(() -> scroll.getViewport().setBackground(Tema.c().fondo()));
        return scroll;
    }

    /** FlowLayout que pasa a varias líneas y calcula su alto según el ancho disponible. */
    private static final class FilaQueSeParte extends FlowLayout {
        FilaQueSeParte(int alineacion, int hgap, int vgap) {
            super(alineacion, hgap, vgap);
        }

        int anchoEnUnaFila(java.awt.Container c) {
            int ancho = 0;
            for (Component h : c.getComponents()) {
                if (h.isVisible()) {
                    ancho += h.getPreferredSize().width + getHgap();
                }
            }
            return ancho;
        }

        @Override
        public Dimension preferredLayoutSize(java.awt.Container c) {
            int disponible = c.getWidth() > 0 ? c.getWidth() : Integer.MAX_VALUE;
            java.awt.Insets m = c.getInsets();
            disponible -= m.left + m.right;
            int x = 0;
            int alto = 0;
            int altoFila = 0;
            int maximo = 0;
            for (Component h : c.getComponents()) {
                if (!h.isVisible()) {
                    continue;
                }
                Dimension d = h.getPreferredSize();
                if (x > 0 && x + getHgap() + d.width > disponible) {
                    alto += altoFila + 6;
                    x = 0;
                    altoFila = 0;
                }
                x += (x > 0 ? getHgap() : 0) + d.width;
                maximo = Math.max(maximo, x);
                altoFila = Math.max(altoFila, d.height);
            }
            return new Dimension(maximo + m.left + m.right + 2 * getHgap(), alto + altoFila + m.top + m.bottom);
        }
    }

    /** Sigue el ancho de la vista: nunca hay barra horizontal; el contenido se reacomoda. */
    private static class MargenDesplazable extends JPanel implements javax.swing.Scrollable {
        MargenDesplazable() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientacion, int direccion) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientacion, int direccion) {
            return Math.max(16, r.height - 32);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof javax.swing.JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    private JPanel pie() {
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.CENTER));
        pie.setBorder(BorderFactory.createEmptyBorder(10, 0, 14, 0));
        JLabel texto = new JLabel("PRPagos — proyecto de portafolio de Luis Felipe Arias.");
        texto.setFont(texto.getFont().deriveFont(12.5f));
        Tema.alCambiar(() -> texto.setForeground(Tema.c().textoSuave()));
        pie.add(texto);
        return pie;
    }

    /**
     * Cambio de pantalla. Antes se pide la cuenta a la API: si la sesión ya no
     * vale, el servidor responde con el motivo y se vuelve al ingreso; si cambió
     * el rol, el menú se actualiza. La pantalla de usuarios exige rol admin.
     */
    void ir(String seccion) {
        Tarea.ejecutar(contenido, () -> Api.get("/api/sesion"), r -> {
            sesion = Sesion.de(Json.objeto(r, "usuario"));
            mostrar(USUARIOS.equals(seccion) && !sesion.admin() ? DASHBOARD : seccion);
        }, e -> {
            if (!(e instanceof Api.SesionCerrada)) {
                mostrar(seccion);
            }
        });
    }

    private void mostrar(String seccion) {
        actual = seccion;
        switch (seccion) {
            case CONSULTA -> consulta.recargar();
            case NUEVO -> nuevo.limpiar();
            case DASHBOARD -> dashboard.recargar();
            case PRESUPUESTO -> presupuesto.recargar();
            case PERFIL -> perfil.recargar();
            case USUARIOS -> usuarios.recargar();
            default -> { }
        }
        tarjetas.show(contenido, seccion);
        marcarMenu();
        arriba(seccion);
    }

    private void arriba(String seccion) {
        anclarArribaHasta = System.currentTimeMillis() + 4000;
        JScrollPane scroll = desplazables.get(seccion);
        if (scroll != null) {
            javax.swing.SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
        }
    }

    /** Los registros con esos filtros (una categoría y un mes), como los enlaces /registros?... de la web. */
    void verRegistros(com.google.gson.JsonObject filtro) {
        consulta.prepararFiltros(filtro);
        mostrar(CONSULTA);
    }

    /** El presupuesto de un año y mes concretos (enlace del dashboard). */
    void verPresupuesto(int anio, int mes) {
        actual = PRESUPUESTO;
        tarjetas.show(contenido, PRESUPUESTO);
        marcarMenu();
        arriba(PRESUPUESTO);
        presupuesto.abrir(anio, mes);
    }

    void editar(Pago pago) {
        editar.cargar(pago);
        actual = EDITAR;
        tarjetas.show(contenido, EDITAR);
        marcarMenu();
        arriba(EDITAR);
    }

    /** Volver a la consulta tras guardar, con el aviso arriba (como ?ok= / ?error= en la web). */
    void volverAConsulta(String mensaje, boolean exito) {
        mostrar(CONSULTA);
        consulta.avisar(mensaje, exito);
    }

    /** Vuelve al ingreso con el mensaje del motivo, como la web. */
    void cerrarSesion(String mensaje, boolean exito) {
        if (cerrada) {
            return;
        }
        cerrada = true;
        vigilancia.stop();
        Api.cerrarSesion();
        Api.alCerrarSesion(null);
        new VentanaLogin(mensaje, exito).setVisible(true);
        dispose();
    }
}
