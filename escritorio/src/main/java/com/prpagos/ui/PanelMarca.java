package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Panel de marca de la portada de acceso (lado derecho), copia del
 * .marca-panel de la web: trama de papel de seguridad, titular "Cada peso,
 * con su plan.", el recibo con el sello EN EL PLAN y los tres puntos.
 */
public class PanelMarca extends JPanel {

    private static final String[][] RENGLONES = {
        {"Vivienda", "1.250.000,00"},
        {"Mercado", "620.000,00"},
        {"Ocio", "310.000,00"},
        {"Ahorro", "840.000,00"},
    };

    public PanelMarca() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(48, 56, 48, 56));
        setOpaque(true);

        JLabel etiqueta = new JLabel("PRESUPUESTO PERSONAL · ORACLE");
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD, 11.5f));

        JLabel titular = new JLabel();
        titular.setFont(new Font("Georgia", Font.BOLD, 36));

        javax.swing.JTextArea bajada = Ui.suave(
                "Registra ingresos y gastos por categoría, sigue tu plan 50/30/20 en el dashboard y recibe "
                + "un resumen por correo cada semana y cada mes. Cada usuario ve solo lo suyo.");
        bajada.setFont(bajada.getFont().deriveFont(14.5f));
        bajada.setMaximumSize(new Dimension(460, 120));

        JLabel puntos = new JLabel();
        puntos.setFont(puntos.getFont().deriveFont(13.5f));

        for (JComponent c : new JComponent[] {etiqueta, titular, bajada, puntos}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        Recibo recibo = new Recibo();

        add(javax.swing.Box.createVerticalGlue());
        add(etiqueta);
        add(Ui.espacio(12));
        add(titular);
        add(Ui.espacio(14));
        add(bajada);
        add(Ui.espacio(32));
        add(recibo);
        add(Ui.espacio(36));
        add(puntos);
        add(javax.swing.Box.createVerticalGlue());

        Tema.alCambiar(() -> {
            Tema.Paleta p = Tema.c();
            String acento = Tema.esOscuro() ? hex(p.enlace()) : hex(p.acento());
            etiqueta.setForeground(p.plataforma());
            titular.setForeground(p.marcaTexto());
            titular.setText("<html>Cada peso,<br><i style='color:" + acento + "'>con su plan.</i></html>");
            bajada.setForeground(p.marcaSuave());
            puntos.setForeground(p.marcaSuave());
            String fuerte = "<b style='color:" + hex(p.marcaTexto()) + "'>";
            puntos.setText("<html>" + fuerte + "Registra</b> ingresos y gastos &nbsp;·&nbsp; "
                    + fuerte + "Sigue</b> tu plan 50/30/20 &nbsp;·&nbsp; " + fuerte
                    + "Recibe</b> tu resumen por correo</html>");
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Tema.Paleta p = Tema.c();
        g2.setColor(p.marcaFondo());
        g2.fillRect(0, 0, getWidth(), getHeight());
        // radial-gradient(80% 60% at 0% 0%, acento-suave, transparent)
        float radio = Math.max(1, getWidth() * 0.8f);
        Color suave = new Color(p.acento().getRed(), p.acento().getGreen(), p.acento().getBlue(), 36);
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), radio, new float[] {0f, 0.6f},
                new Color[] {suave, new Color(suave.getRed(), suave.getGreen(), suave.getBlue(), 0)}));
        g2.fillRect(0, 0, getWidth(), getHeight());
        // Trama cruzada de papel de seguridad (45° y -45°, cada 9 px).
        g2.setColor(p.trama());
        int largo = getWidth() + getHeight();
        for (int i = -largo; i < largo; i += 9) {
            g2.drawLine(i, 0, i + getHeight(), getHeight());
            g2.drawLine(i, getHeight(), i + getHeight(), 0);
        }
        g2.dispose();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** El recibo inclinado con borde de zigzag (.recibo). */
    private static final class Recibo extends JComponent {
        private static final int ANCHO = 352;
        private static final int ALTO = 250;

        // Animación de la web (acceso.css): cada renglón "se imprime" (0,45 s, bajando 6 px)
        // a los 0,3 / 0,75 / 1,2 / 1,65 s y el sello cae a los 2,3 s (0,35 s, de escala 1,6 a 1).
        private static final double[] INICIO_RENGLON = {0.30, 0.75, 1.20, 1.65};
        private static final double DURACION_RENGLON = 0.45;
        private static final double INICIO_SELLO = 2.30;
        private static final double DURACION_SELLO = 0.35;
        private long inicio = -1;
        private final javax.swing.Timer animacion = new javax.swing.Timer(16, e -> {
            repaint();
            if (segundos() > INICIO_SELLO + DURACION_SELLO + 0.1) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });

        Recibo() {
            setAlignmentX(Component.LEFT_ALIGNMENT);
            Dimension d = new Dimension(ANCHO + 20, ALTO + 30);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        /** La animación empieza cuando el recibo aparece en pantalla, como al cargar la página. */
        @Override
        public void addNotify() {
            super.addNotify();
            inicio = System.nanoTime();
            animacion.restart();
        }

        @Override
        public void removeNotify() {
            animacion.stop();
            super.removeNotify();
        }

        private double segundos() {
            return inicio < 0 ? 99 : (System.nanoTime() - inicio) / 1e9;
        }

        /** 0..1 con aceleración de salida (ease-out), como la curva de la web. */
        private static double progreso(double t, double desde, double duracion) {
            double x = Math.max(0, Math.min(1, (t - desde) / duracion));
            return 1 - Math.pow(1 - x, 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Tema.Paleta p = Tema.c();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.translate(10, 14);
            g2.rotate(Math.toRadians(-1.5), ANCHO / 2.0, ALTO / 2.0);

            // Sombra suave.
            g2.setPaint(new GradientPaint(0, ALTO - 30, new Color(120, 30, 20, 0), 0, ALTO + 14,
                    new Color(120, 30, 20, Tema.esOscuro() ? 0 : 45)));
            g2.fillRect(6, ALTO - 30, ANCHO - 12, 44);

            // Papel con borde inferior en zigzag.
            Polygon papel = new Polygon();
            papel.addPoint(0, 0);
            papel.addPoint(ANCHO, 0);
            papel.addPoint(ANCHO, ALTO);
            for (int x = ANCHO; x >= 0; x -= 14) {
                papel.addPoint(x, ALTO);
                papel.addPoint(Math.max(0, x - 7), ALTO + 8);
            }
            papel.addPoint(0, ALTO);
            g2.setColor(p.papel());
            g2.fillPolygon(papel);
            g2.setColor(p.papelBorde());
            g2.drawLine(0, 0, ANCHO, 0);
            g2.drawLine(0, 0, 0, ALTO);
            g2.drawLine(ANCHO, 0, ANCHO, ALTO);

            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
            Font monoChica = mono.deriveFont(10.5f);
            int izq = 22;
            int der = ANCHO - 22;
            int y = 30;
            g2.setFont(monoChica);
            g2.setColor(p.marcaSuave());
            g2.drawString("GASTOS DEL MES", izq, y);
            derecha(g2, "PLAN 50/30/20", der, y);
            y += 12;
            punteada(g2, p, izq, der, y);
            y += 26;
            g2.setFont(mono);
            g2.setColor(p.marcaTexto());
            double t = segundos();
            java.awt.Composite normal = g2.getComposite();
            for (int i = 0; i < RENGLONES.length; i++) {
                double a = progreso(t, INICIO_RENGLON[i], DURACION_RENGLON);
                if (a > 0) {
                    int caida = (int) Math.round(-6 * (1 - a));
                    g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) a));
                    g2.drawString(RENGLONES[i][0], izq, y + caida);
                    derecha(g2, RENGLONES[i][1], der, y + caida);
                }
                y += 26;
            }
            g2.setComposite(normal);
            y -= 6;
            punteada(g2, p, izq, der, y);
            y += 24;
            g2.setFont(mono.deriveFont(Font.BOLD));
            g2.drawString("TOTAL", izq, y);
            derecha(g2, "3.020.000,00", der, y);

            // Sello EN EL PLAN sobre el borde inferior, como en la web (.sello): aparece al final
            // con un "golpe" de escala 1,6 -> 1 y queda con opacidad 0,9.
            double s = progreso(t, INICIO_SELLO, DURACION_SELLO);
            if (s <= 0) {
                g2.dispose();
                return;
            }
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) (0.9 * s)));
            double escala = 1.6 - 0.6 * s;
            g2.rotate(Math.toRadians(-8), der - 56, ALTO);
            g2.translate(der - 56, ALTO);
            g2.scale(escala, escala);
            g2.translate(-(der - 56), -ALTO);
            g2.setColor(p.papel());
            g2.fillRoundRect(der - 112, ALTO - 16, 112, 32, 8, 8);
            Color sello = Tema.esOscuro() ? p.enlace() : p.acento();
            g2.setColor(new Color(sello.getRed(), sello.getGreen(), sello.getBlue(), 230));
            g2.setStroke(new BasicStroke(2.2f));
            g2.drawRoundRect(der - 112, ALTO - 16, 112, 32, 8, 8);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("EN EL PLAN", der - 56 - fm.stringWidth("EN EL PLAN") / 2, ALTO + 5);
            g2.dispose();
        }

        private static void derecha(Graphics2D g2, String texto, int x, int y) {
            g2.drawString(texto, x - g2.getFontMetrics().stringWidth(texto), y);
        }

        private static void punteada(Graphics2D g2, Tema.Paleta p, int x1, int x2, int y) {
            g2.setColor(p.papelBorde());
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] {4f, 3f}, 0f));
            g2.drawLine(x1, y, x2, y);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(p.marcaTexto());
        }
    }
}
