package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.renderer.category.BarPainter;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.ui.RectangleEdge;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Json;

/**
 * Piezas animadas del dashboard, iguales a dashboard.js de la web: cifras que
 * cuentan desde cero, variación frente al mes anterior, barras redondeadas que
 * crecen con el mes elegido resaltado y su valor en una píldora, y los
 * medidores semicirculares por segmentos del plan 50/30/20.
 */
final class GraficasAnimadas {

    private GraficasAnimadas() {
    }

    /** "$ 1,2 M" / "$ 850 mil": para las píldoras y el centro del anillo. */
    static String corto(double v) {
        double a = Math.abs(v);
        String s = v < 0 ? "-" : "";
        if (a >= 1e6) {
            return s + "$ " + String.format(java.util.Locale.ROOT, "%.1f", a / 1e6).replace('.', ',') + " M";
        }
        if (a >= 1e3) {
            return s + "$ " + Math.round(a / 1e3) + " mil";
        }
        return s + "$ " + Math.round(a);
    }

    // ───────────────────────── Cifras ─────────────────────────

    /** El número del texto ("$ 1.250.000,00", "29,9 %") cuenta desde cero en 700 ms. */
    static void contar(JLabel l, String texto) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d[\\d.]*(,\\d+)?").matcher(texto);
        if (!m.find() || Efectos.reducir()) {
            l.setText(texto);
            return;
        }
        String crudo = m.group();
        int dec = crudo.contains(",") ? crudo.length() - crudo.indexOf(',') - 1 : 0;
        double valor = Double.parseDouble(crudo.replace(".", "").replace(',', '.'));
        String antes = texto.substring(0, m.start());
        String despues = texto.substring(m.end());
        l.getAccessibleContext().setAccessibleName(texto);
        Efectos.animar(700, new Efectos.Curva(0.33f, 1f, 0.68f, 1f),
                t -> l.setText(t >= 1 ? texto : antes + formato(valor * t, dec) + despues), () -> l.setText(texto));
    }

    private static String formato(double v, int dec) {
        java.text.DecimalFormatSymbols s = new java.text.DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        java.text.DecimalFormat f = new java.text.DecimalFormat(dec > 0 ? "#,##0." + "0".repeat(dec) : "#,##0", s);
        return f.format(v);
    }

    /**
     * "▲ 4 % vs. mes anterior": compara el mes elegido con el anterior en la serie de 6
     * meses que ya calcula el servidor. {@code subirEsBueno} decide el color (y la flecha
     * siempre acompaña, el color nunca va solo).
     */
    static void variacion(JLabel l, double actual, double anterior, boolean subirEsBueno, boolean sobreColor) {
        if (anterior == 0) {
            l.setText("Sin datos del mes anterior");
            l.setForeground(sobreColor ? Color.WHITE : Tema.c().textoSuave());
            return;
        }
        long pct = Math.round((actual - anterior) / Math.abs(anterior) * 100);
        boolean sube = pct > 0;
        l.setText((pct == 0 ? "= " : sube ? "▲ " : "▼ ") + Math.abs(pct) + " % vs. mes anterior");
        if (sobreColor) {
            l.setForeground(Color.WHITE);
        } else if (pct == 0) {
            l.setForeground(Tema.c().textoSuave());
        } else {
            l.setForeground(PanelDashboard.colorEstado(sube == subirEsBueno ? "bien" : "mal"));
        }
    }

    // ───────────────────────── Barras ─────────────────────────

    /**
     * Barras redondeadas que crecen desde la base, una tras otra; el último mes (el
     * elegido) va en su color y los demás atenuados.
     */
    static final class Barras implements BarPainter {
        double crecer = 1;
        private final int ultima;

        Barras(int columnas) {
            this.ultima = columnas - 1;
        }

        @Override
        public void paintBar(Graphics2D g2, BarRenderer r, int fila, int columna, RectangularShape barra, RectangleEdge base) {
            double inicio = columna * 70 + fila * 40;
            double f = Math.max(0, Math.min(1, (crecer * 1100 - inicio) / 700));
            f = 1 - Math.pow(1 - f, 4);
            double alto = barra.getHeight() * f;
            Rectangle2D b = new Rectangle2D.Double(barra.getX(), barra.getMaxY() - alto, barra.getWidth(), alto);
            Paint p = r.getItemPaint(fila, columna);
            if (p instanceof Color c && columna != ultima) {
                p = new Color(c.getRed(), c.getGreen(), c.getBlue(), 82);
            }
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(p);
            double arco = Math.min(16, b.getWidth());
            g.fill(new RoundRectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight(), arco, arco));
            g.dispose();
        }

        @Override
        public void paintBarShadow(Graphics2D g2, BarRenderer r, int fila, int columna, RectangularShape barra,
                                   RectangleEdge base, boolean pegada) {
        }
    }

    /** ChartPanel que pinta encima la píldora con el valor de las barras del mes elegido. */
    static final class PanelConPildoras extends ChartPanel {
        private final Barras barras;
        private final List<Double> valores;
        private final int columnas;

        PanelConPildoras(JFreeChart grafica, Barras barras, List<Double> valores, int columnas) {
            super(grafica, false, false, false, false, true);
            this.barras = barras;
            this.valores = valores;
            this.columnas = columnas;
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getChartRenderingInfo() == null || barras.crecer < 1) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            FontMetrics fm = g2.getFontMetrics();
            Rectangle2D previa = null;
            List<ChartEntity> entidades = new ArrayList<>();
            for (Object o : getChartRenderingInfo().getEntityCollection().getEntities()) {
                entidades.add((ChartEntity) o);
            }
            for (ChartEntity e : entidades) {
                if (!(e instanceof CategoryItemEntity c)) {
                    continue;
                }
                int col = c.getDataset().getColumnIndex(c.getColumnKey());
                int fila = c.getDataset().getRowIndex(c.getRowKey());
                if (col != columnas - 1) {
                    continue;
                }
                double valor = valores.get(fila);
                if (valor == 0) {
                    continue;
                }
                Shape area = c.getArea();
                Rectangle2D b = area.getBounds2D();
                String txt = corto(valor);
                double w = fm.stringWidth(txt) + 12;
                double h = 18;
                double x = Math.min(getWidth() - w - 4, Math.max(4, b.getCenterX() - w / 2));
                double y = b.getY() - h - 6;
                if (previa != null && x < previa.getMaxX() + 4 && x + w > previa.getX() - 4) {
                    y = Math.min(y, previa.getY() - h - 4);
                }
                previa = new Rectangle2D.Double(x, y, w, h);
                g2.setColor(Tema.c().texto());
                g2.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
                g2.setColor(Tema.c().superficie());
                g2.drawString(txt, (float) (x + 6), (float) (y + (h + fm.getAscent() - fm.getDescent()) / 2));
            }
            g2.dispose();
        }
    }

    // ───────────────────────── Medidores del plan ─────────────────────────

    /** Los tres medidores (Necesidades, Deseos, Ahorro) con el % usado y el estado del servidor. */
    static JPanel medidores(List<JsonObject> plan) {
        JPanel p = new JPanel(new GridLayout(1, Math.max(1, plan.size()), 12, 0));
        p.setOpaque(false);
        StringBuilder accesible = new StringBuilder();
        int n = 0;
        for (JsonObject f : plan) {
            p.add(new Medidor(f, n++));
            accesible.append(Json.texto(f, "grupo")).append(": ").append(Json.texto(f, "usado")).append(" usado, ")
                    .append(Json.texto(f, "estado")).append(". ");
        }
        p.getAccessibleContext().setAccessibleName(accesible.toString());
        return p;
    }

    private static final class Medidor extends JComponent {
        private static final int SEGMENTOS = 18;
        private final String grupo;
        private final String usado;
        private final String estado;
        private final String clase;
        private final Color color;
        private final int llenos;
        private double t;

        Medidor(JsonObject f, int n) {
            grupo = Json.texto(f, "grupo");
            usado = Json.texto(f, "usado");
            estado = Json.texto(f, "estado");
            clase = Json.texto(f, "clase_estado");
            color = Color.decode(Json.texto(f, "color_grupo"));
            int pct;
            try {
                pct = Integer.parseInt(usado.replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                pct = 0;
            }
            llenos = (int) Math.round(Math.min(pct, 100) / 100.0 * SEGMENTOS);
            setPreferredSize(new Dimension(150, 170));
            javax.swing.Timer espera = new javax.swing.Timer(250 + n * 140, e ->
                    Efectos.animar(32 * SEGMENTOS + 280, Efectos.SUAVE, v -> { t = v; repaint(); }, null));
            espera.setRepeats(false);
            if (Efectos.reducir()) {
                t = 1;
            } else {
                espera.start();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Tema.Paleta p = Tema.c();
            double r = Math.min(getWidth() * 0.4, 58);
            double cx = getWidth() / 2.0;
            double cy = 14 + r;
            g2.setStroke(new BasicStroke((float) (r * 0.19), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int encendidos = (int) Math.floor(t * llenos + 0.0001);
            for (int i = 0; i < SEGMENTOS; i++) {
                double a0 = 180 - (i / (double) SEGMENTOS) * 180 - 1.8;
                double ext = -(180.0 / SEGMENTOS - 3.6);
                g2.setColor(i < encendidos ? color : p.borde());
                g2.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, a0, ext, Arc2D.OPEN));
            }
            g2.setColor(p.texto());
            g2.setFont(getFont().deriveFont(Font.BOLD, 19f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(usado, (float) (cx - fm.stringWidth(usado) / 2.0), (float) (cy - 4));
            g2.setFont(getFont().deriveFont(11.5f));
            fm = g2.getFontMetrics();
            g2.setColor(p.textoSuave());
            g2.drawString("usado", (float) (cx - fm.stringWidth("usado") / 2.0), (float) (cy + 12));
            g2.setColor(p.texto());
            g2.setFont(getFont().deriveFont(Font.BOLD, 13.5f));
            fm = g2.getFontMetrics();
            g2.drawString(grupo, (float) (cx - fm.stringWidth(grupo) / 2.0), (float) (cy + 38));
            String icono = switch (clase) {
                case "bien" -> "✓ ";
                case "mal" -> "✕ ";
                default -> "! ";
            };
            g2.setColor(PanelDashboard.colorEstado(clase));
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            fm = g2.getFontMetrics();
            String e = icono + estado;
            g2.drawString(e, (float) (cx - fm.stringWidth(e) / 2.0), (float) (cy + 58));
            g2.dispose();
        }

        @Override
        public Font getFont() {
            Font f = super.getFont();
            return f != null ? f : javax.swing.UIManager.getFont("Label.font");
        }
    }
}
