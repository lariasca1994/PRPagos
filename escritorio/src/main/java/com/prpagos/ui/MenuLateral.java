package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.Animator;

/**
 * Menú lateral de la ventana principal, igual al de la web (base.html y
 * efectos.css): mismas opciones y orden; entra en cascada la primera vez;
 * un fondo suave sigue al ratón; la opción activa lleva un haz de luz rojo a
 * la izquierda que se desliza al cambiar de sección; y se compacta a solo
 * íconos (el ancho se cierra suave, los textos se van en cascada y, al pasar
 * el ratón, una etiqueta con el nombre salta a la derecha del ícono). La
 * preferencia queda guardada en este equipo.
 */
final class MenuLateral extends JPanel {

    private static final int ANCHO = 240;
    private static final int COMPACTO = 72;
    private static final int ALTO_OPCION = 44;
    private static final Preferences PREFERENCIAS = Preferences.userNodeForPackage(MenuLateral.class);
    private static boolean yaEntro;
    /** El anillo de foco solo se ve si se navega con el teclado (como :focus-visible en la web). */
    private static volatile boolean conTeclado;

    static {
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                conTeclado = true;
            } else if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
                conTeclado = false;
            }
        }, java.awt.AWTEvent.KEY_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK);
    }

    private final Map<String, Opcion> opciones = new LinkedHashMap<>();
    private final List<Opcion> pie = new ArrayList<>();
    private final String nombre;
    private double ancho;
    private double textos = 1;
    private boolean compacto;
    private double indY = -1;
    private double indAlfa;
    private double hovY = -1;
    private double hovAlfa;
    private double entrada = 1;
    private String activa;
    private Animator animInd;
    private Animator animHov;
    private Etiqueta etiqueta;

    MenuLateral(String nombreUsuario, boolean tableta) {
        super(null);
        this.nombre = nombreUsuario;
        setOpaque(false);
        compacto = PREFERENCIAS.get("compacto", null) != null ? PREFERENCIAS.getBoolean("compacto", false) : tableta;
        ancho = compacto ? COMPACTO : ANCHO;
        textos = compacto ? 0 : 1;
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(e.getPoint())) {
                    salirRaton();
                }
            }
        });
    }

    /** Agrega una opción del menú principal (clave, texto, ícono). */
    void opcion(String clave, String texto, String icono, Runnable accion) {
        Opcion o = new Opcion(texto, icono, opciones.size(), accion);
        opciones.put(clave, o);
        add(o);
    }

    /** Agrega una opción del pie (tema, salir, compactar). */
    void opcionPie(String texto, String icono, Runnable accion) {
        Opcion o = new Opcion(texto, icono, pie.size() + 1, accion);
        o.esPie = true;
        pie.add(o);
        add(o);
    }

    void visible(String clave, boolean si) {
        Opcion o = opciones.get(clave);
        if (o != null && o.isVisible() != si) {
            o.setVisible(si);
            revalidate();
            repaint();
        }
    }

    /** La sección actual: el indicador se desliza hasta ella. */
    void marcar(String clave) {
        if (clave.equals(activa)) {
            return;
        }
        activa = clave;
        Opcion o = opciones.get(clave);
        opciones.values().forEach(x -> x.repaint());
        if (o == null) {
            return;
        }
        double destino = o.getY();
        if (indY < 0 || !isShowing()) {
            indY = destino;
            indAlfa = 1;
            repaint();
            return;
        }
        double desde = indY;
        if (animInd != null) {
            animInd.stop();
        }
        indAlfa = 1;
        animInd = Efectos.animar(320, Efectos.REBOTE, t -> { indY = Efectos.entre(desde, destino, t); repaint(); }, null);
    }

    /** Entrada en cascada (solo la primera vez que se abre la ventana en esta sesión). */
    void entrar() {
        if (yaEntro || Efectos.reducir()) {
            yaEntro = true;
            return;
        }
        yaEntro = true;
        entrada = 0;
        Efectos.animar(900, Efectos.SUAVE, t -> { entrada = t; repaint(); }, null);
    }

    void alternar() {
        compacto = !compacto;
        PREFERENCIAS.putBoolean("compacto", compacto);
        double a0 = ancho;
        double a1 = compacto ? COMPACTO : ANCHO;
        double t0 = textos;
        double t1 = compacto ? 0 : 1;
        if (etiqueta != null) {
            etiqueta.ocultar();
        }
        Efectos.animar(400, Efectos.MENU, t -> {
            ancho = Efectos.entre(a0, a1, t);
            textos = Efectos.entre(t0, t1, Math.min(1, t * 1.6));
            revalidate();
            repaint();
            if (getParent() != null) {
                getParent().revalidate();
            }
        }, null);
    }

    boolean esCompacto() {
        return compacto;
    }

    // ───────────────────────── Medidas ─────────────────────────

    @Override
    public Dimension getPreferredSize() {
        return new Dimension((int) Math.round(ancho), 400);
    }

    @Override
    public void doLayout() {
        int w = (int) Math.round(ancho);
        int y = 76;
        for (Opcion o : opciones.values()) {
            if (!o.isVisible()) {
                continue;
            }
            o.setBounds(12, y, w - 24, ALTO_OPCION);
            y += ALTO_OPCION + 4;
        }
        int abajo = getHeight() - 14 - ALTO_OPCION;
        for (int i = pie.size() - 1; i >= 0; i--) {
            pie.get(i).setBounds(12, abajo, w - 24, ALTO_OPCION);
            abajo -= ALTO_OPCION + 2;
        }
        Opcion o = activa == null ? null : opciones.get(activa);
        if (o != null && (animInd == null || !animInd.isRunning())) {
            indY = o.getY();
        }
    }

    private int yUsuario() {
        return getHeight() - 14 - pie.size() * (ALTO_OPCION + 2) - 52;
    }

    // ───────────────────────── Pintura ─────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Tema.Paleta p = Tema.c();
        int w = getWidth();
        g2.setColor(p.superficie());
        g2.fillRect(0, 0, w, getHeight());
        g2.setColor(p.borde());
        g2.drawLine(w - 1, 0, w - 1, getHeight());

        // Marca
        float alfaMarca = (float) Math.min(1, entrada * 2);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alfaMarca));
        java.awt.Image icono = Ui.icono();
        if (icono != null) {
            g2.drawImage(icono, 21, 20, 30, 30, null);
        }
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (alfaMarca * textos)));
        g2.setColor(p.texto());
        g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
        g2.drawString("Simulador de pagos", (float) (60 - 10 * (1 - textos)), 41);
        g2.setComposite(AlphaComposite.SrcOver);

        int ancho = w - 24;
        // Fondo que sigue al ratón
        if (hovY >= 0 && hovAlfa > 0) {
            Color h = p.plataformaSuave();
            g2.setColor(new Color(h.getRed(), h.getGreen(), h.getBlue(), (int) (255 * 0.75 * hovAlfa)));
            g2.fillRoundRect(12, (int) Math.round(hovY), ancho, ALTO_OPCION, 20, 20);
        }
        // Opción activa: fondo, haz de luz y degradado que se apaga.
        if (indY >= 0 && indAlfa > 0) {
            int y = (int) Math.round(indY);
            Color a = acentoTexto();
            g2.setColor(p.acentoSuave());
            g2.fillRoundRect(12, y, ancho, ALTO_OPCION, 20, 20);
            g2.setClip(new java.awt.geom.RoundRectangle2D.Double(12, y, ancho, ALTO_OPCION, 20, 20));
            g2.setPaint(new GradientPaint(12, 0, new Color(a.getRed(), a.getGreen(), a.getBlue(), 56),
                    96, 0, new Color(a.getRed(), a.getGreen(), a.getBlue(), 0)));
            g2.fillRect(12, y, 84, ALTO_OPCION);
            for (int i = 4; i >= 1; i--) {
                g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), 18 * (5 - i)));
                g2.fillRoundRect(12 - i * 2, y + ALTO_OPCION / 2 - 10 - i * 2, 3 + i * 4, 20 + i * 4, 8, 8);
            }
            g2.setColor(a);
            g2.fillRoundRect(10, y + ALTO_OPCION / 2 - 10, 5, 20, 4, 4);
            g2.setClip(null);
        }
        // Usuario
        int yu = yUsuario();
        g2.setColor(p.borde());
        g2.drawLine(12, yu - 8, w - 12, yu - 8);
        g2.setColor(p.acentoSuave());
        g2.fillOval(21, yu + 6, 32, 32);
        g2.setColor(acentoTexto());
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        String inicial = nombre.isBlank() ? "?" : nombre.substring(0, 1).toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(inicial, 37 - fm.stringWidth(inicial) / 2f, yu + 27);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) textos));
        g2.setColor(p.texto());
        g2.setFont(getFont().deriveFont(13f));
        g2.drawString(recortar(nombre, g2.getFontMetrics(), w - 80), (float) (64 - 10 * (1 - textos)), yu + 27);
        g2.dispose();
    }

    private static String recortar(String texto, FontMetrics fm, int maximo) {
        if (fm.stringWidth(texto) <= maximo) {
            return texto;
        }
        String t = texto;
        while (t.length() > 1 && fm.stringWidth(t + "…") > maximo) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    static Color acentoTexto() {
        return Tema.esOscuro() ? Tema.c().enlace() : Tema.c().acento();
    }

    // ───────────────────────── Ratón ─────────────────────────

    private void sobre(Opcion o) {
        double destino = o.getY();
        if (hovY < 0 || hovAlfa == 0) {
            hovY = destino;
        }
        double desde = hovY;
        if (animHov != null) {
            animHov.stop();
        }
        animHov = Efectos.animar(280, Efectos.SUAVE, t -> {
            hovY = Efectos.entre(desde, destino, t);
            hovAlfa = Math.max(hovAlfa, t);
            repaint();
        }, null);
        if (compacto) {
            mostrarEtiqueta(o);
        }
    }

    private void salirRaton() {
        double desde = hovAlfa;
        Efectos.animar(250, Efectos.SUAVE, t -> { hovAlfa = desde * (1 - t); repaint(); }, null);
        if (etiqueta != null) {
            etiqueta.ocultar();
        }
    }

    private void mostrarEtiqueta(Opcion o) {
        JLayeredPane capas = SwingUtilities.getRootPane(this) == null ? null : SwingUtilities.getRootPane(this).getLayeredPane();
        if (capas == null) {
            return;
        }
        if (etiqueta == null || etiqueta.getParent() != capas) {
            etiqueta = new Etiqueta();
            capas.add(etiqueta, JLayeredPane.POPUP_LAYER);
        }
        Point p = SwingUtilities.convertPoint(o, o.getWidth() + 10, o.getHeight() / 2, capas);
        etiqueta.mostrar(o.texto, p);
    }

    // ───────────────────────── Opción ─────────────────────────

    private final class Opcion extends JButton {
        final String texto;
        final String icono;
        final int k;
        final Runnable accion;
        boolean esPie;
        private double encima;

        Opcion(String texto, String icono, int k, Runnable accion) {
            this.texto = texto;
            this.icono = icono;
            this.k = k;
            this.accion = accion;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(texto);
            setToolTipText(null);
            addActionListener(e -> accion.run());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    sobre(Opcion.this);
                    Efectos.animar(250, Efectos.REBOTE, t -> { encima = t; repaint(); }, null);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    double desde = encima;
                    Efectos.animar(200, Efectos.SUAVE, t -> { encima = desde * (1 - t); repaint(); }, null);
                    Point p = SwingUtilities.convertPoint(Opcion.this, e.getPoint(), MenuLateral.this);
                    if (!MenuLateral.this.contains(p)) {
                        salirRaton();
                    }
                }
            });
        }

        boolean activa() {
            return !esPie && opciones.get(activa) == this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Cascada de entrada: 90 ms + 55 ms por opción, 450 ms cada una.
            double inicio = (90 + k * 55) / 900.0;
            double t = Math.max(0, Math.min(1, (entrada - inicio) / 0.5));
            t = 1 - Math.pow(1 - t, 3);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) t));
            g2.translate(-14 * (1 - t), 0);
            Tema.Paleta p = Tema.c();
            boolean act = activa();
            Color color = act ? acentoTexto() : (encima > 0.5 ? p.texto() : p.textoSuave());
            if (getModel().isPressed()) {
                g2.translate(getWidth() / 2.0, getHeight() / 2.0);
                g2.scale(0.96, 0.96);
                g2.translate(-getWidth() / 2.0, -getHeight() / 2.0);
            }
            double escala = act ? 1.12 : 1;
            double lado = 20 * escala;
            double ix = 14 - (lado - 20) / 2 + (act ? 0 : 2 * encima);
            double iy = (getHeight() - lado) / 2;
            if ("alternar".equals(icono) && compacto) {
                Graphics2D r = (Graphics2D) g2.create();
                r.rotate(Math.PI, ix + lado / 2, iy + lado / 2);
                Iconos.dibujar(r, icono, ix, iy, lado, color);
                r.dispose();
            } else {
                Iconos.dibujar(g2, icono, ix, iy, lado, color);
            }
            // El texto se va en cascada hacia la izquierda al compactar.
            double tt = Math.max(0, Math.min(1, textos * (1 + k * 0.06) - k * 0.06));
            if (tt > 0) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (t * tt)));
                g2.setColor(color);
                g2.setFont(getFont().deriveFont(act ? Font.BOLD : Font.PLAIN, 14f));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "alternar".equals(icono) ? (compacto ? "Extender menú" : "Compactar menú") : texto;
                g2.drawString(txt, (float) (48 - 10 * (1 - tt)), (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            }
            if (isFocusOwner() && conTeclado) {
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(p.plataforma());
                g2.setStroke(new java.awt.BasicStroke(2f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
            }
            g2.dispose();
        }
    }

    // ───────────────────────── Etiqueta del modo compacto ─────────────────────────

    /** Nombre de la opción a la derecha del ícono, con su flecha, que sigue al cursor. */
    private static final class Etiqueta extends JComponent {
        private String texto = "";
        private double alfa;
        private double y = -1;
        private Animator anim;

        Etiqueta() {
            setFont(javax.swing.UIManager.getFont("Label.font"));
        }

        void mostrar(String t, Point p) {
            texto = t;
            FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD, 12.5f));
            int w = fm.stringWidth(t) + 28;
            int h = 28;
            double destino = p.y - h / 2.0;
            double desde = y < 0 || alfa == 0 ? destino : y;
            double a0 = alfa;
            if (anim != null) {
                anim.stop();
            }
            anim = Efectos.animar(240, Efectos.REBOTE, k -> {
                y = Efectos.entre(desde, destino, k);
                alfa = Efectos.entre(a0, 1, k);
                setBounds(p.x - (int) (8 * (1 - alfa)), (int) Math.round(y), w, h);
                repaint();
            }, null);
        }

        void ocultar() {
            double a0 = alfa;
            if (anim != null) {
                anim.stop();
            }
            anim = Efectos.animar(180, Efectos.SUAVE, k -> { alfa = a0 * (1 - k); repaint(); }, null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (alfa <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1, alfa)));
            Tema.Paleta p = Tema.c();
            g2.setColor(p.texto());
            g2.fillRoundRect(6, 0, getWidth() - 6, getHeight(), 14, 14);
            Path2D flecha = new Path2D.Double();
            flecha.moveTo(7, getHeight() / 2.0 - 5);
            flecha.lineTo(1, getHeight() / 2.0);
            flecha.lineTo(7, getHeight() / 2.0 + 5);
            g2.fill(flecha);
            g2.setColor(p.superficie());
            g2.setFont(getFont().deriveFont(Font.BOLD, 12.5f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, 16, (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g2.dispose();
        }
    }
}
