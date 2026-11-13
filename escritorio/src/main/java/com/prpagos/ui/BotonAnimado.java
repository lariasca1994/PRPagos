package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.Timer;

/**
 * Botón con los mismos efectos que .boton en la web (efectos.css): al pasar el
 * ratón sube 1 px y lo cruza un reflejo de luz; al presionar se hunde y sale
 * una onda desde el punto del clic; "enviando" atenúa el texto y muestra una
 * barra que va y viene (sin aceptar otro clic); al terminar bien pasa a ✓; en
 * un error tiembla. Los colores salen del estilo de FlatLaf (Tema).
 */
public class BotonAnimado extends JButton {

    private static BotonAnimado ultimo;
    private static long ultimoClic;

    private final boolean conEfectos;
    private double hover;
    private double brillo = -1;
    private double presion;
    private final List<double[]> ondas = new ArrayList<>();
    private boolean ocupado;
    private boolean listo;
    private double fase;
    private int temblorX;
    private Color frenteOriginal;
    private final Timer barra = new Timer(16, e -> {
        fase = (fase + 0.018) % 2;
        repaint();
    });

    public BotonAnimado(String texto, boolean conEfectos) {
        super(texto);
        this.conEfectos = conEfectos;
        if (!conEfectos) {
            return;
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isEnabled() || ocupado) {
                    return;
                }
                Efectos.animar(180, Efectos.SUAVE, t -> { hover = Math.max(hover, t); repaint(); }, null);
                Efectos.animar(750, Efectos.SUAVE, t -> { brillo = t; repaint(); }, () -> { brillo = -1; repaint(); });
            }

            @Override
            public void mouseExited(MouseEvent e) {
                double desde = hover;
                Efectos.animar(180, Efectos.SUAVE, t -> { hover = desde * (1 - t); repaint(); }, null);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled() || ocupado) {
                    return;
                }
                presion = 1;
                double[] onda = {e.getX(), e.getY(), 0};
                ondas.add(onda);
                Efectos.animar(550, Efectos.SUAVE, t -> { onda[2] = t; repaint(); }, () -> { ondas.remove(onda); repaint(); });
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Efectos.animar(180, Efectos.SUAVE, t -> { presion = 1 - t; repaint(); }, null);
            }
        });
    }

    @Override
    protected void fireActionPerformed(ActionEvent event) {
        if (ocupado) {
            return;
        }
        ultimo = this;
        ultimoClic = System.currentTimeMillis();
        super.fireActionPerformed(event);
    }

    /** El botón que se acaba de usar (para que Tarea muestre "enviando" en él). */
    static BotonAnimado reciente() {
        return System.currentTimeMillis() - ultimoClic < 400 ? ultimo : null;
    }

    /** "Enviando": texto atenuado, barra que va y viene y sin aceptar otro clic. */
    public void setOcupado(boolean si) {
        if (ocupado == si) {
            return;
        }
        ocupado = si;
        if (si) {
            frenteOriginal = getForeground();
            Color f = getForeground();
            setForeground(new Color(f.getRed(), f.getGreen(), f.getBlue(), 90));
            if (!Efectos.reducir()) {
                barra.start();
            }
        } else {
            barra.stop();
            if (frenteOriginal != null) {
                setForeground(frenteOriginal);
            }
        }
        repaint();
    }

    /** Terminó bien: el texto pasa a ✓ (antes de cambiar de pantalla). */
    public void setListo(boolean si) {
        setOcupado(false);
        listo = si;
        repaint();
    }

    /** Error: temblor corto. */
    public void temblar() {
        int[] pasos = {-1, 3, -5, 5, -5, 5, -5, 3, -1, 0};
        Efectos.animar(420, Efectos.SUAVE, t -> {
            temblorX = pasos[Math.min(pasos.length - 1, (int) (t * pasos.length))];
            repaint();
        }, () -> { temblorX = 0; repaint(); });
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        double escala = 1 - 0.03 * presion;
        g2.translate(temblorX, isEnabled() ? -hover : 0);
        g2.translate(w / 2.0, h / 2.0);
        g2.scale(escala, escala);
        g2.translate(-w / 2.0, -h / 2.0);
        super.paint(g2);
        if (conEfectos) {
            RoundRectangle2D forma = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 16, 16);
            g2.clip(forma);
            Color frente = frenteOriginal != null && ocupado ? frenteOriginal : getForeground();
            boolean relleno = getBackground() != null && luz(getBackground()) < 0.55;
            Color luz = relleno ? Color.WHITE : frente;
            for (double[] o : ondas) {
                double r = Math.max(w, h) * 1.1 * o[2];
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.28 * (1 - o[2]))));
                g2.setColor(luz);
                g2.fill(new java.awt.geom.Ellipse2D.Double(o[0] - r, o[1] - r, r * 2, r * 2));
            }
            if (brillo >= 0 && relleno) {
                double x = -0.6 * w + brillo * 2.2 * w;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        (float) (0.28 * Math.sin(Math.PI * brillo))));
                g2.setPaint(new GradientPaint((float) x, 0, new Color(255, 255, 255, 0),
                        (float) (x + w * 0.22), 0, Color.WHITE, true));
                Path2D banda = new Path2D.Double();
                banda.moveTo(x, h);
                banda.lineTo(x + h * 0.4, 0);
                banda.lineTo(x + w * 0.45 + h * 0.4, 0);
                banda.lineTo(x + w * 0.45, h);
                banda.closePath();
                g2.fill(banda);
            }
            g2.setComposite(AlphaComposite.SrcOver);
            if (ocupado) {
                double pos = fase <= 1 ? fase : 2 - fase;
                int ancho = (int) (w * 0.35);
                int x = (int) ((w - ancho) * (Efectos.reducir() ? 0 : pos));
                g2.setColor(frente);
                g2.fillRoundRect(x, h - 4, Efectos.reducir() ? w : ancho, 3, 3, 3);
            }
            if (listo) {
                g2.setColor(getBackground());
                g2.fill(forma);
                g2.setColor(frente);
                g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double cx = w / 2.0;
                double cy = h / 2.0;
                Path2D check = new Path2D.Double();
                check.moveTo(cx - 8, cy + 0.5);
                check.lineTo(cx - 2.5, cy + 6);
                check.lineTo(cx + 8.5, cy - 5.5);
                g2.draw(check);
            }
        }
        g2.dispose();
    }

    private static double luz(Color c) {
        return (0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue()) / 255;
    }
}
