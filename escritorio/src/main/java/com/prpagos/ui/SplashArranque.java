package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.Timer;

/**
 * Animación de entrada al abrir PRPagos (paridad con la web: static/arranque.css). Mismo ícono y
 * secuencia: sube el recibo, se escriben sus líneas, aparece el escudo y se marca el check; luego
 * el nombre y el lema. Dura 1,4 s y después abre la ventana de acceso.
 */
public final class SplashArranque extends JComponent {

    private static final int ANCHO = 360;
    private static final int ALTO = 400;
    private static final double DURACION = 1.4;
    private static final Color ROJO = new Color(0xC7, 0x46, 0x34);
    private static final Color AZUL = new Color(0x42, 0x85, 0xF4);

    private final long inicio = System.nanoTime();

    private SplashArranque() {
        setDoubleBuffered(true);
    }

    /** Muestra la animación y, al terminar, ejecuta {@code luego} (abrir la ventana de acceso). */
    public static void mostrar(Runnable luego) {
        JWindow ventana = new JWindow();
        SplashArranque lienzo = new SplashArranque();
        ventana.setContentPane(lienzo);
        ventana.setSize(ANCHO, ALTO);
        ventana.setLocationRelativeTo(null);
        try {
            ventana.setShape(new RoundRectangle2D.Double(0, 0, ANCHO, ALTO, 28, 28));
        } catch (UnsupportedOperationException ignorado) {
            // Sin ventanas con forma: se muestra rectangular.
        }
        Timer animacion = new Timer(16, null);
        animacion.addActionListener(e -> {
            lienzo.repaint();
            if (lienzo.segundos() >= DURACION) {
                animacion.stop();
                ventana.dispose();
                luego.run();
            }
        });
        ventana.setVisible(true);
        animacion.start();
    }

    private double segundos() {
        return (System.nanoTime() - inicio) / 1e9;
    }

    /** 0..1 con salida suave entre {@code desde} y {@code desde + duracion}. */
    private static double avance(double t, double desde, double duracion) {
        double x = Math.max(0, Math.min(1, (t - desde) / duracion));
        return 1 - Math.pow(1 - x, 3);
    }

    @Override
    protected void paintComponent(Graphics g) {
        double t = segundos();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Fondo: rojo de la marca con un brillo central, igual que la web.
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(ANCHO / 2.0, ALTO * 0.35), ALTO * 0.8f,
                new float[] {0f, 1f}, new Color[] {new Color(0xD5, 0x57, 0x4A), new Color(0x9E, 0x34, 0x27)}));
        g2.fillRect(0, 0, ANCHO, ALTO);

        // Ícono en coordenadas del SVG (64 × 64) escalado a 112 px.
        double escala = 112 / 64.0;
        AffineTransform base = g2.getTransform();
        g2.translate((ANCHO - 112) / 2.0, 104);
        g2.scale(escala, escala);

        g2.setColor(new Color(0, 0, 0, 60));
        g2.fill(new RoundRectangle2D.Double(0, 4, 64, 64, 28, 28));
        g2.setColor(ROJO);
        g2.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 28, 28));

        // Recibo: sube y aparece.
        double recibo = avance(t, 0.05, 0.45);
        Path2D papel = new Path2D.Double();
        papel.moveTo(14, 10);
        papel.lineTo(40, 10);
        papel.lineTo(40, 51);
        double[] dientes = {35.7, 47.5, 31.3, 51, 27, 47.5, 22.7, 51, 18.3, 47.5, 14, 51};
        for (int i = 0; i < dientes.length; i += 2) {
            papel.lineTo(dientes[i], dientes[i + 1]);
        }
        papel.closePath();
        Graphics2D gp = (Graphics2D) g2.create();
        gp.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) recibo));
        gp.translate(0, 18 / escala * (1 - recibo));
        gp.setColor(Color.WHITE);
        gp.fill(papel);
        gp.dispose();

        // Líneas del recibo: se escriben de izquierda a derecha, una tras otra.
        g2.setColor(ROJO);
        g2.setStroke(new BasicStroke(3.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        linea(g2, 20, 20, 34, avance(t, 0.40, 0.22));
        linea(g2, 20, 27.5, 34, avance(t, 0.55, 0.22));
        linea(g2, 20, 35, 27, avance(t, 0.70, 0.18));

        // Escudo: crece con un pequeño rebote.
        double escudo = rebote(Math.max(0, Math.min(1, (t - 0.75) / 0.38)));
        if (escudo > 0) {
            Path2D forma = new Path2D.Double();
            forma.moveTo(46, 30);
            forma.lineTo(56, 33.8);
            forma.lineTo(56, 42);
            forma.curveTo(56, 48.4, 51.8, 52.6, 46, 55);
            forma.curveTo(40.2, 52.6, 36, 48.4, 36, 42);
            forma.lineTo(36, 33.8);
            forma.closePath();
            Graphics2D ge = (Graphics2D) g2.create();
            ge.translate(46, 42.5);
            ge.scale(escudo, escudo);
            ge.translate(-46, -42.5);
            ge.setColor(AZUL);
            ge.fill(forma);
            ge.setColor(Color.WHITE);
            ge.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            ge.draw(forma);
            ge.dispose();
        }

        // Check: se dibuja en dos tramos.
        double check = avance(t, 1.0, 0.25);
        if (check > 0) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double primero = Math.min(1, check / 0.35);
            g2.draw(new java.awt.geom.Line2D.Double(41.2, 42.4, 41.2 + 3.4 * primero, 42.4 + 3.4 * primero));
            if (check > 0.35) {
                double segundo = (check - 0.35) / 0.65;
                g2.draw(new java.awt.geom.Line2D.Double(44.6, 45.8, 44.6 + 6.2 * segundo, 45.8 - 6.6 * segundo));
            }
        }
        g2.setTransform(base);

        // Nombre y lema.
        texto(g2, "PRPagos", new Font(Font.SANS_SERIF, Font.BOLD, 20), Color.WHITE, 262, avance(t, 0.60, 0.45));
        texto(g2, "Pagos y presupuesto", new Font(Font.SANS_SERIF, Font.PLAIN, 13), new Color(0xFF, 0xE1, 0xDC), 288,
                avance(t, 0.75, 0.45));
        g2.dispose();
    }

    private static void linea(Graphics2D g2, double x0, double y, double x1, double avance) {
        if (avance <= 0) {
            return;
        }
        g2.draw(new java.awt.geom.Line2D.Double(x0, y, x0 + (x1 - x0) * avance, y));
    }

    /** Escala con un leve sobrepaso, como cubic-bezier(.34, 1.56, .64, 1) en la web. */
    private static double rebote(double x) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        return x <= 0 ? 0 : 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
    }

    private void texto(Graphics2D g2, String s, Font fuente, Color color, int y, double avance) {
        if (avance <= 0) {
            return;
        }
        Graphics2D gt = (Graphics2D) g2.create();
        gt.setFont(fuente);
        gt.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) avance));
        gt.setColor(color);
        FontMetrics m = gt.getFontMetrics();
        gt.drawString(s, (ANCHO - m.stringWidth(s)) / 2, (int) (y + 6 * (1 - avance)));
        gt.dispose();
    }
}
