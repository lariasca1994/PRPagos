package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.prpagos.cliente.Api;

/**
 * Pantalla de mantenimiento (el servidor responde 503: la base de datos no está
 * disponible) y pantalla sin conexión, iguales a mantenimiento.html y
 * offline.html de la web. Cubre la ventana, consulta solo /health (nunca
 * repite la acción que falló) y se quita sola cuando el servicio responde.
 */
final class PantallaEstado extends JPanel {

    private static final int REINTENTO_MANTENIMIENTO = 15;
    private static final int REINTENTO_SIN_RED = 8;

    private final boolean sinRed;
    private final JFrame ventana;
    private final int total;
    private int resto;
    private boolean revisando;
    private double fase;
    private final JLabel textoReintento = new JLabel();
    private final Anillo anillo = new Anillo();
    private final JButton reintentar = Ui.primario("Reintentar ahora", e -> revisar());
    private final Timer reloj;
    private final Timer ambiente;

    /** Muestra la pantalla sobre la ventana (si no está ya). */
    static void mostrar(Window w, boolean sinRed) {
        if (!(w instanceof JFrame f) || f.getGlassPane() instanceof PantallaEstado actual && actual.isVisible()) {
            return;
        }
        PantallaEstado p = new PantallaEstado(f, sinRed);
        f.setGlassPane(p);
        p.setVisible(true);
        p.requestFocusInWindow();
    }

    private PantallaEstado(JFrame ventana, boolean sinRed) {
        super(new GridBagLayout());
        this.ventana = ventana;
        this.sinRed = sinRed;
        this.total = sinRed ? REINTENTO_SIN_RED : REINTENTO_MANTENIMIENTO;
        this.resto = total;
        setOpaque(false);
        // Bloquea los clics a la pantalla de abajo mientras está visible.
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        setFocusable(true);

        JPanel caja = Ui.columna();
        caja.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (sinRed) {
            caja.add(centrar(new ReciboSinRed()));
            caja.add(Ui.espacio(26));
        } else {
            caja.add(centrar(new Orbe()));
            caja.add(Ui.espacio(10));
            JLabel etiqueta = etiqueta("SERVICIO EN PAUSA", 12f, Font.BOLD);
            Tema.alCambiar(() -> etiqueta.setForeground(acentoTexto()));
            caja.add(etiqueta);
            caja.add(Ui.espacio(8));
        }
        caja.add(etiqueta(sinRed ? "Sin conexión" : "Estamos en mantenimiento", 25f, Font.BOLD));
        caja.add(Ui.espacio(8));
        JLabel texto = etiqueta("<html><div style='text-align:center;width:400px'>" + (sinRed
                ? "Tus registros están guardados en la nube. Cuando vuelva internet podrás seguir donde ibas."
                : "La base de datos no está disponible en este momento, así que no se pueden consultar ni "
                + "registrar ingresos y gastos.") + "</div></html>", 14.5f, Font.PLAIN);
        Tema.alCambiar(() -> texto.setForeground(Tema.c().textoSuave()));
        caja.add(texto);
        if (!sinRed) {
            caja.add(Ui.espacio(14));
            caja.add(centrar(new BarraEspera()));
            caja.add(Ui.espacio(8));
            JLabel nota = etiqueta("Si la base de datos estaba dormida, suele volver en menos de un minuto.", 12.5f, Font.PLAIN);
            Tema.alCambiar(() -> nota.setForeground(Tema.c().textoSuave()));
            caja.add(nota);
            caja.add(Ui.espacio(14));
            JPanel fichas = new JPanel(new GridLayout(1, 3, 10, 0));
            fichas.setOpaque(false);
            fichas.add(ficha("Tus registros y tu presupuesto siguen guardados."));
            fichas.add(ficha("Ninguna acción quedó a medias: si no viste la confirmación, no se guardó."));
            fichas.add(ficha("Esta pantalla se quita sola cuando la base de datos responda."));
            fichas.setPreferredSize(new Dimension(620, 124));
            fichas.setMaximumSize(new Dimension(640, 124));
            fichas.setAlignmentX(Component.CENTER_ALIGNMENT);
            caja.add(fichas);
        }
        caja.add(Ui.espacio(18));
        JPanel pie = Ui.fila(10);
        pie.setAlignmentX(Component.CENTER_ALIGNMENT);
        textoReintento.setFont(textoReintento.getFont().deriveFont(13f));
        Tema.alCambiar(() -> textoReintento.setForeground(Tema.c().textoSuave()));
        pie.add(anillo);
        pie.add(textoReintento);
        pie.add(Box.espacio(10));
        pie.add(reintentar);
        pie.setMaximumSize(new Dimension(640, 50));
        caja.add(centrar(pie));
        add(caja);

        pintarReintento("Reintento automático en " + resto + " s");
        reloj = new Timer(1000, e -> {
            if (revisando) {
                return;
            }
            resto--;
            if (resto <= 0) {
                revisar();
            } else {
                pintarReintento("Reintento automático en " + resto + " s");
            }
        });
        reloj.start();
        ambiente = new Timer(33, e -> {
            fase += 0.033;
            repaint();
        });
        if (!Efectos.reducir()) {
            ambiente.start();
        }
        // Entrada por partes, como .mnt-caja > * en la web.
        Efectos.animar(500, Efectos.SUAVE, t -> { alfa = t; repaint(); }, null);
    }

    private double alfa = 1;

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Tema.Paleta p = Tema.c();
        g2.setColor(p.fondo());
        g2.fillRect(0, 0, getWidth(), getHeight());
        // Resplandor suave del color del proyecto arriba.
        float r = Math.max(320, getWidth() * 0.45f);
        g2.setPaint(new RadialGradientPaint(getWidth() / 2f, -20, r, new float[] {0f, 1f},
                new Color[] {p.acentoSuave(), new Color(p.fondo().getRed(), p.fondo().getGreen(), p.fondo().getBlue(), 0)}));
        g2.fillRect(0, 0, getWidth(), (int) r);
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alfa));
        g2.translate(0, (1 - alfa) * 12);
        super.paintChildren(g2);
        g2.dispose();
    }

    private void pintarReintento(String texto) {
        textoReintento.setText(texto);
        anillo.valor = revisando ? 1 : resto / (double) total;
        anillo.repaint();
    }

    private void revisar() {
        if (revisando) {
            return;
        }
        revisando = true;
        pintarReintento("Comprobando…");
        if (reintentar instanceof BotonAnimado b) {
            b.setOcupado(true);
        }
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Api.salud();
            }

            @Override
            protected void done() {
                boolean ok;
                try {
                    ok = get();
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) {
                    pintarReintento(sinRed ? "Volvió la conexión." : "La base de datos respondió. Volviendo…");
                    if (reintentar instanceof BotonAnimado b) {
                        b.setListo(true);
                    }
                    new Timer(500, x -> cerrar()) {{ setRepeats(false); }}.start();
                    return;
                }
                revisando = false;
                resto = total;
                if (reintentar instanceof BotonAnimado b) {
                    b.setOcupado(false);
                }
                pintarReintento((sinRed ? "Seguimos sin red." : "La base de datos aún no responde.")
                        + " Reintento en " + total + " s");
            }
        }.execute();
    }

    private void cerrar() {
        reloj.stop();
        ambiente.stop();
        Efectos.animar(250, Efectos.SUAVE, t -> { alfa = 1 - t; repaint(); }, () -> {
            setVisible(false);
            // La pantalla vuelve a cargar sus datos (con GET): nunca se repite lo que falló.
            if (ventana instanceof VentanaPrincipal vp) {
                vp.recargarActual();
            }
        });
    }

    // ───────────────────────── Piezas ─────────────────────────

    static Color acentoTexto() {
        return Tema.esOscuro() ? Tema.c().enlace() : Tema.c().acento();
    }

    private static JLabel etiqueta(String texto, float tam, int estilo) {
        JLabel l = new JLabel(texto, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(estilo, tam));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private static JComponent centrar(JComponent c) {
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        c.setMinimumSize(c.getPreferredSize());
        p.setMinimumSize(c.getPreferredSize());
        p.add(c);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    private static JPanel ficha(String texto) {
        Ui.Redondeado f = new Ui.Redondeado(12, true);
        f.setLayout(new java.awt.BorderLayout(0, 6));
        f.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel check = new JLabel(new Check());
        f.add(check, java.awt.BorderLayout.NORTH);
        // Ancho fijo: sin él, la etiqueta HTML pide una sola línea larguísima.
        JLabel l = new JLabel("<html><div style='width:132px'>" + texto + "</div></html>");
        l.setFont(l.getFont().deriveFont(13f));
        Tema.alCambiar(() -> l.setForeground(Tema.c().texto()));
        f.add(l, java.awt.BorderLayout.CENTER);
        return f;
    }

    private static final class Box {
        static Component espacio(int ancho) {
            return javax.swing.Box.createHorizontalStrut(ancho);
        }
    }

    /** Marca de verificación verde (.promesas svg). */
    private static final class Check implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Tema.c().exitoTexto());
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            p.moveTo(x + 3, y + 10);
            p.lineTo(x + 7.5, y + 14.5);
            p.lineTo(x + 17, y + 5);
            g2.draw(p);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    /** Anillo de la cuenta regresiva. */
    private static final class Anillo extends JComponent {
        double valor = 1;

        Anillo() {
            setPreferredSize(new Dimension(24, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Tema.c().borde());
            g2.drawOval(3, 3, 18, 18);
            g2.setColor(acentoTexto());
            g2.draw(new Arc2D.Double(3, 3, 18, 18, 90, 360 * valor, Arc2D.OPEN));
            g2.dispose();
        }
    }

    /** El ícono del proyecto flota despacio entre ondas que respiran (.orbe). */
    private final class Orbe extends JComponent {
        Orbe() {
            setPreferredSize(new Dimension(112, 112));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            Color a = acentoTexto();
            for (int i = 0; i < 3; i++) {
                double f = ((fase / 3.6) + i / 3.0) % 1.0;
                double radio = 28 + 28 * f;
                float op = (float) (f < 0.25 ? f / 0.25 * 0.55 : 0.55 * (1 - (f - 0.25) / 0.75));
                g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), Math.round(Math.max(0, op) * 255)));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval((int) (cx - radio), (int) (cy - radio), (int) (radio * 2), (int) (radio * 2));
            }
            double dy = Efectos.reducir() ? 0 : -4 * Math.sin(Math.PI * ((fase / 3.6) % 1.0));
            g2.setColor(Tema.c().superficie());
            g2.fillOval((int) (cx - 30), (int) (cy - 30 + dy), 60, 60);
            java.awt.Image icono = Ui.icono();
            if (icono != null) {
                g2.drawImage(icono, (int) (cx - 19), (int) (cy - 19 + dy), 38, 38, null);
            }
            g2.dispose();
        }
    }

    /** Barra de espera que va y viene, sin porcentaje (.mnt-barra). */
    private final class BarraEspera extends JComponent {
        BarraEspera() {
            setPreferredSize(new Dimension(300, 6));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Tema.c().acentoSuave());
            g2.fillRoundRect(0, 0, getWidth(), 6, 6, 6);
            double t = (fase / 1.8) % 2;
            t = t <= 1 ? t : 2 - t;
            t = t * t * (3 - 2 * t);
            int ancho = (int) (getWidth() * 0.38);
            g2.setColor(acentoTexto());
            g2.fillRoundRect((int) ((getWidth() - ancho) * t), 0, ancho, 6, 6, 6);
            g2.dispose();
        }
    }

    /** El recibo en pausa con el sello "SIN CONEXIÓN" (como offline.html). */
    private static final class ReciboSinRed extends JComponent {
        ReciboSinRed() {
            setPreferredSize(new Dimension(300, 210));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Tema.Paleta p = Tema.c();
            g2.rotate(Math.toRadians(-1.5), getWidth() / 2.0, getHeight() / 2.0);
            int x = 20;
            int y = 10;
            int w = getWidth() - 40;
            int h = 170;
            g2.setColor(p.superficie());
            g2.fillRoundRect(x, y, w, h, 18, 18);
            g2.setColor(p.borde());
            g2.drawRoundRect(x, y, w, h, 18, 18);
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            g2.setFont(mono.deriveFont(10.5f));
            g2.setColor(p.textoSuave());
            g2.drawString("SIMULADOR DE PAGOS", x + 16, y + 26);
            g2.setColor(acentoTexto());
            g2.drawString("EN PAUSA", x + w - 16 - g2.getFontMetrics().stringWidth("EN PAUSA"), y + 26);
            String[][] filas = {{"Conexión", "sin red"}, {"Tus registros", "en la nube"}, {"Reintento", "automático"}};
            g2.setFont(mono);
            int fy = y + 58;
            for (String[] f : filas) {
                g2.setColor(p.textoSuave());
                g2.drawString(f[0], x + 16, fy);
                g2.setColor(p.texto());
                g2.drawString(f[1], x + w - 16 - g2.getFontMetrics().stringWidth(f[1]), fy);
                fy += 28;
            }
            g2.setFont(mono.deriveFont(Font.BOLD, 13f));
            g2.drawString("TOTAL", x + 16, y + h - 18);
            g2.drawString("—", x + w - 26, y + h - 18);
            // Sello en la esquina inferior.
            g2.rotate(Math.toRadians(-9), x + w - 70, y + h);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            String sello = "SIN CONEXIÓN";
            int sw = g2.getFontMetrics().stringWidth(sello) + 18;
            g2.setColor(p.superficie());
            g2.fillRoundRect(x + w - 70 - sw / 2, y + h - 6, sw, 24, 6, 6);
            g2.setColor(acentoTexto());
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x + w - 70 - sw / 2, y + h - 6, sw, 24, 6, 6);
            g2.drawString(sello, x + w - 70 - sw / 2 + 9, y + h + 10);
            g2.dispose();
        }
    }
}
