package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.DoubleConsumer;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.Animator;

/**
 * Animaciones comunes del escritorio, iguales a las de la web (efectos.css y
 * efectos.js): mismas duraciones y curvas. Usa el Animator de FlatLaf. Si
 * Windows tiene apagadas las animaciones ("Mostrar animaciones en Windows",
 * el equivalente de prefers-reduced-motion), todo salta directo al final.
 */
public final class Efectos {

    /** cubic-bezier(.2,.8,.2,1): entradas. */
    public static final Curva SUAVE = new Curva(0.2f, 0.8f, 0.2f, 1f);
    /** cubic-bezier(.3,1.25,.5,1): indicador del menú, con un pequeño rebote. */
    public static final Curva REBOTE = new Curva(0.3f, 1.25f, 0.5f, 1f);
    /** cubic-bezier(.3,.9,.3,1): ancho del menú. */
    public static final Curva MENU = new Curva(0.3f, 0.9f, 0.3f, 1f);
    /** cubic-bezier(.65,0,.35,1): cruce de paneles en la portada. */
    public static final Curva CRUCE = new Curva(0.65f, 0f, 0.35f, 1f);

    private static final boolean REDUCIR = leerReducir();

    /**
     * Curva cubic-bezier como la de CSS. A diferencia de la de FlatLaf, admite puntos
     * de control fuera de [0, 1] en el eje del avance (el rebote del indicador).
     */
    public static final class Curva implements Animator.Interpolator {
        private final double x1;
        private final double y1;
        private final double x2;
        private final double y2;

        public Curva(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        private static double bezier(double t, double a, double b) {
            double u = 1 - t;
            return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
        }

        @Override
        public float interpolate(float x) {
            if (x <= 0 || x >= 1) {
                return x <= 0 ? 0 : 1;
            }
            // Se busca t tal que x(t) = x (bisección: x(t) es creciente porque x1, x2 están en [0, 1]).
            double lo = 0;
            double hi = 1;
            double t = x;
            for (int i = 0; i < 30; i++) {
                t = (lo + hi) / 2;
                if (bezier(t, x1, x2) < x) {
                    lo = t;
                } else {
                    hi = t;
                }
            }
            return (float) bezier(t, y1, y2);
        }
    }

    private Efectos() {
    }

    /** true si el sistema pide menos movimiento. */
    public static boolean reducir() {
        return REDUCIR;
    }

    /**
     * Anima de 0 a 1 en {@code ms} milisegundos (en el hilo de la interfaz) y al final llama a
     * {@code fin}. Devuelve el Animator para poder detenerlo; null si no hubo animación.
     */
    public static Animator animar(int ms, Curva curva, DoubleConsumer paso, Runnable fin) {
        if (REDUCIR || ms <= 0) {
            paso.accept(1);
            if (fin != null) {
                fin.run();
            }
            return null;
        }
        Animator a = new Animator(ms, new Animator.TimingTarget() {
            @Override
            public void timingEvent(float fraccion) {
                paso.accept(fraccion);
            }

            @Override
            public void end() {
                paso.accept(1);
                if (fin != null) {
                    fin.run();
                }
            }
        });
        a.setInterpolator(curva);
        a.setResolution(10);
        a.start();
        return a;
    }

    public static double entre(double desde, double hasta, double t) {
        return desde + (hasta - desde) * t;
    }

    // ───────────────────── Transición entre pantallas ─────────────────────

    /** Cómo entra la pantalla nueva (mismos modos que acceso.js). */
    public enum Modo { AVANZA, ARRIBA }

    /** Imagen de un componente tal como se ve ahora. */
    public static BufferedImage foto(JComponent c) {
        int w = Math.max(1, c.getWidth());
        int h = Math.max(1, c.getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.printAll(g);
        g.dispose();
        return img;
    }

    /**
     * La pantalla que sale se desvanece y se corre (180 ms); la nueva entra desde el lado hacia
     * donde se navega (500 ms). {@code cambiar} hace el cambio real (CardLayout.show, etc.): la
     * pantalla nueva queda funcionando debajo y solo se anima una capa encima, que luego se quita.
     */
    public static void transicion(JComponent zona, int direccion, Modo modo, Runnable cambiar) {
        JRootPane raiz = SwingUtilities.getRootPane(zona);
        if (REDUCIR || raiz == null || !zona.isShowing() || zona.getWidth() == 0) {
            cambiar.run();
            return;
        }
        BufferedImage vieja = foto(zona);
        cambiar.run();
        zona.validate();
        BufferedImage nueva = foto(zona);
        JLayeredPane capas = raiz.getLayeredPane();
        Point p = SwingUtilities.convertPoint(zona, 0, 0, capas);
        Capa capa = new Capa(vieja, nueva, direccion, modo, zona.getBackground());
        capa.setBounds(new Rectangle(p.x, p.y, zona.getWidth(), zona.getHeight()));
        capas.add(capa, JLayeredPane.DRAG_LAYER);
        capas.repaint();
        animar(560, SUAVE, t -> {
            capa.t = t;
            capa.repaint();
        }, () -> {
            capas.remove(capa);
            capas.repaint(p.x, p.y, capa.getWidth(), capa.getHeight());
        });
    }

    /** Capa temporal que pinta la salida y la entrada encima de la pantalla real. */
    private static final class Capa extends JComponent {
        private final BufferedImage vieja;
        private final BufferedImage nueva;
        private final int dir;
        private final Modo modo;
        private final java.awt.Color fondo;
        double t;

        Capa(BufferedImage vieja, BufferedImage nueva, int dir, Modo modo, java.awt.Color fondo) {
            this.vieja = vieja;
            this.nueva = nueva;
            this.dir = dir;
            this.modo = modo;
            this.fondo = fondo;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(fondo != null ? fondo : Tema.c().fondo());
            g2.fillRect(0, 0, getWidth(), getHeight());
            double total = 560;
            double tSale = Math.min(1, t * total / 180);
            double tEntra = Math.max(0, (t * total - 120) / (total - 120));
            boolean arriba = modo == Modo.ARRIBA;
            if (tSale < 1) {
                double d = -18 * tSale * dir;
                pintar(g2, vieja, 1 - tSale, arriba ? 0 : d, arriba ? d : 0);
            }
            double r = 1 - tEntra;
            pintar(g2, nueva, Math.min(1, tEntra * 1.25), arriba ? 0 : 22 * r * dir, arriba ? 22 * r * dir : 8 * r);
            g2.dispose();
        }

        private void pintar(Graphics2D g2, BufferedImage img, double alfa, double dx, double dy) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, Math.min(1, alfa))));
            g.drawImage(img, (int) Math.round(dx), (int) Math.round(dy), null);
            g.dispose();
        }
    }

    /** Temblor corto en horizontal (errores), moviendo el componente unos píxeles. */
    public static void temblar(Component c) {
        if (REDUCIR || c == null) {
            return;
        }
        Point origen = c.getLocation();
        int[] pasos = {-1, 3, -5, 5, -5, 5, -5, 3, -1, 0};
        animar(420, new Curva(0.36f, 0.07f, 0.19f, 0.97f), t -> {
            int i = Math.min(pasos.length - 1, (int) (t * pasos.length));
            c.setLocation(origen.x + pasos[i], origen.y);
        }, () -> c.setLocation(origen));
    }

    // ───────────────────── Movimiento reducido (Windows) ─────────────────────

    private interface User32 extends com.sun.jna.win32.StdCallLibrary {
        boolean SystemParametersInfoW(int accion, int parametro, com.sun.jna.ptr.IntByReference valor, int opciones);
    }

    /** SPI_GETCLIENTAREAANIMATION: false cuando la persona apagó las animaciones de Windows. */
    private static boolean leerReducir() {
        if (Boolean.getBoolean("prpagos.sinAnimaciones")) {
            return true;
        }
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                return false;
            }
            User32 u = com.sun.jna.Native.load("user32", User32.class);
            com.sun.jna.ptr.IntByReference valor = new com.sun.jna.ptr.IntByReference(1);
            return u.SystemParametersInfoW(0x1042, 0, valor, 0) && valor.getValue() == 0;
        } catch (Throwable e) {
            return false;
        }
    }
}
