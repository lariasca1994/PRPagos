package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import com.prpagos.datos.Cuenta;
import com.prpagos.datos.Pago;
import com.prpagos.datos.RepositorioUsuarios;
import com.prpagos.datos.Sesion;

/**
 * Ventana principal: misma estructura que las páginas de la web (base.html).
 * Barra superior con la marca, el botón de tema y el menú en el mismo orden
 * (Dashboard · Consultar registros · Nuevo registro · Perfil · Usuarios
 * (admin) · Salir), el contenido al centro con un ancho máximo y el pie.
 *
 * Sesión con las mismas reglas y mensajes que la web: se cierra tras 6 horas
 * sin uso (cada tecla o clic reinicia el plazo) y como máximo a las 8 horas;
 * en cada cambio de pantalla se vuelve a consultar la cuenta (como
 * dependencias.py en cada petición), así que una suspensión o un cambio de
 * rol tienen efecto inmediato.
 */
public class VentanaPrincipal extends JFrame {

    static final String DASHBOARD = "dashboard";
    static final String CONSULTA = "consulta";
    static final String NUEVO = "nuevo";
    static final String EDITAR = "editar";
    static final String PERFIL = "perfil";
    static final String USUARIOS = "usuarios";

    private static final int INACTIVIDAD_MS = 6 * 60 * 60 * 1000;
    private static final int DURACION_MAXIMA_MS = 8 * 60 * 60 * 1000;
    /** Los mismos textos que MENSAJES_CIERRE en web/app/seguridad.py. */
    static final String CIERRE_INACTIVIDAD = "Tu sesión se cerró tras 6 horas de inactividad. Ingresa de nuevo.";
    static final String CIERRE_MAXIMO = "Tu sesión se cerró al cumplir 8 horas. Ingresa de nuevo.";
    static final String CIERRE_SUSPENDIDA = "Tu sesión se cerró porque tu cuenta ya no está activa.";
    static final String CIERRE_SALIR = "Cerraste sesión.";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final Map<String, JButton> menu = new LinkedHashMap<>();
    private final PanelConsulta consulta;
    private final PanelPago nuevo;
    private final PanelPago editar;
    private final PanelDashboard dashboard;
    private final PanelPerfil perfil;
    private final PanelUsuarios usuarios;
    private final Timer inactividad;
    private final Timer duracionMaxima;
    private final java.awt.event.AWTEventListener actividad;
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
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        consulta = new PanelConsulta(this);
        nuevo = PanelPago.nuevo(this);
        editar = PanelPago.edicion(this);
        dashboard = new PanelDashboard(this);
        perfil = new PanelPerfil(this);
        usuarios = new PanelUsuarios(this);

        contenido.setOpaque(false);
        for (Object[] s : new Object[][] {{dashboard, DASHBOARD}, {consulta, CONSULTA}, {nuevo, NUEVO},
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

        inactividad = new Timer(INACTIVIDAD_MS, e -> cerrarSesion(CIERRE_INACTIVIDAD, false));
        inactividad.setRepeats(false);
        inactividad.start();
        duracionMaxima = new Timer(DURACION_MAXIMA_MS, e -> cerrarSesion(CIERRE_MAXIMO, false));
        duracionMaxima.setRepeats(false);
        duracionMaxima.start();
        // Cualquier tecla o clic en esta ventana cuenta como actividad.
        actividad = evento -> {
            if (evento.getSource() instanceof java.awt.Component c
                    && javax.swing.SwingUtilities.getWindowAncestor(c) == this) {
                inactividad.restart();
            }
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(actividad,
                java.awt.AWTEvent.KEY_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK);

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

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        nav.setOpaque(false);
        menu.put(DASHBOARD, opcion("Dashboard", () -> ir(DASHBOARD)));
        menu.put(CONSULTA, opcion("Consultar registros", () -> ir(CONSULTA)));
        menu.put(NUEVO, opcion("Nuevo registro", () -> ir(NUEVO)));
        menu.put(PERFIL, opcion("Perfil", () -> ir(PERFIL)));
        menu.put(USUARIOS, opcion("Usuarios (admin)", () -> ir(USUARIOS)));
        menu.values().forEach(nav::add);
        nav.add(opcion("Salir (" + sesion.nombre() + ")", () -> cerrarSesion(CIERRE_SALIR, true)));
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
        JPanel margen = new JPanel(new BorderLayout()) {
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
     * Cambio de pantalla. Antes se revisa la cuenta en la base: si fue
     * suspendida o eliminada se vuelve al ingreso; si cambió el rol, el menú
     * y los permisos se actualizan. La pantalla de usuarios exige rol admin
     * (si no, se va a la consulta, como exigir_admin en la web).
     */
    void ir(String seccion) {
        Tarea.ejecutar(contenido, () -> RepositorioUsuarios.estado(sesion.usuarioId()), cuenta -> {
            if (!vigente(cuenta)) {
                cerrarSesion(CIERRE_SUSPENDIDA, false);
                return;
            }
            mostrar(USUARIOS.equals(seccion) && !sesion.admin() ? DASHBOARD : seccion);
        }, e -> mostrar(seccion));
    }

    /** Antes de una acción que escribe en la base: false si la cuenta ya no vale. */
    boolean verificarCuenta() throws java.sql.SQLException {
        return vigente(RepositorioUsuarios.estado(sesion.usuarioId()));
    }

    private boolean vigente(Cuenta cuenta) {
        if (cuenta == null || !cuenta.activo()) {
            return false;
        }
        sesion = sesion.conAdmin(cuenta.esAdmin());
        return true;
    }

    private void mostrar(String seccion) {
        actual = seccion;
        switch (seccion) {
            case CONSULTA -> consulta.recargar();
            case NUEVO -> nuevo.limpiar();
            case DASHBOARD -> dashboard.recargar();
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

    /** Cuenta suspendida o eliminada mientras se usaba la aplicación. */
    void cuentaNoVigente() {
        cerrarSesion(CIERRE_SUSPENDIDA, false);
    }

    /** Vuelve al ingreso con el mensaje del motivo, como la web. */
    void cerrarSesion(String mensaje, boolean exito) {
        inactividad.stop();
        duracionMaxima.stop();
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(actividad);
        new VentanaLogin(mensaje, exito).setVisible(true);
        dispose();
    }
}
