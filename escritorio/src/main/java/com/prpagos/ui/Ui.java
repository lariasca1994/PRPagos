package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Piezas de interfaz equivalentes a las clases CSS de la web (.boton,
 * .boton-secundario, .tarjeta, .aviso, .campo-codigo, label...), para que las
 * pantallas se armen igual en las dos versiones.
 */
public final class Ui {

    private Ui() {
    }

    // ───────────── Texto ─────────────

    public static JLabel titulo(String texto, float tamano) {
        JLabel l = new JLabel(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, tamano));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    /** Texto secundario (var(--texto-suave)); ajusta líneas si no cabe. */
    public static JTextArea suave(String texto) {
        JTextArea a = new JTextArea(texto);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setEditable(false);
        a.setFocusable(false);
        a.setOpaque(false);
        a.setBorder(null);
        a.setAlignmentX(Component.LEFT_ALIGNMENT);
        a.setFont(new JLabel().getFont());
        Tema.alCambiar(() -> a.setForeground(Tema.c().textoSuave()));
        return a;
    }

    /** El "paso" en mayúsculas sobre el título de la portada (.acceso-paso). */
    public static JLabel paso(String texto) {
        JLabel l = new JLabel(texto.toUpperCase());
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        Tema.alCambiar(() -> l.setForeground(Tema.c().plataforma()));
        return l;
    }

    /** Etiqueta de un campo (label de los formularios). */
    public static JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 12.5f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        return l;
    }

    // ───────────── Campos ─────────────

    public static JTextField campo(String marcador) {
        JTextField t = new JTextField();
        estirar(t);
        if (marcador != null) {
            t.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, marcador);
        }
        return t;
    }

    public static JPasswordField clave() {
        JPasswordField t = new JPasswordField();
        t.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        estirar(t);
        return t;
    }

    /** Campo del código de 6 dígitos, grande y centrado (.campo-codigo). */
    public static JTextField campoCodigo() {
        JTextField t = campo("123 456");
        t.setHorizontalAlignment(SwingConstants.CENTER);
        t.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        t.setColumns(7);
        return t;
    }

    private static void estirar(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        c.setPreferredSize(new Dimension(280, 38));
    }

    // ───────────── Botones ─────────────

    public static JButton primario(String texto, ActionListener accion) {
        return boton(texto, "primario", accion);
    }

    public static JButton secundario(String texto, ActionListener accion) {
        return boton(texto, "secundario", accion);
    }

    public static JButton enlace(String texto, ActionListener accion) {
        return boton(texto, "enlace", accion);
    }

    private static JButton boton(String texto, String clase, ActionListener accion) {
        JButton b = new JButton(texto);
        b.putClientProperty(FlatClientProperties.STYLE_CLASS, clase);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (accion != null) {
            b.addActionListener(accion);
        }
        return b;
    }

    /** Botón 🌓 de la barra (.boton-tema). */
    public static JButton botonTema() {
        JButton b = boton("◐", "redondo", e -> Tema.alternar());
        b.setToolTipText("Cambiar entre tema claro y oscuro");
        b.setPreferredSize(new Dimension(34, 34));
        b.setFont(b.getFont().deriveFont(15f));
        return b;
    }

    // ───────────── Contenedores ─────────────

    /** Columna vertical sin fondo propio. */
    public static JPanel columna() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Fila horizontal sin fondo propio. */
    public static JPanel fila(int separacion) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, separacion, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    /** Tarjeta blanca redondeada con borde (.tarjeta / .tarjeta-formulario). */
    public static JPanel tarjeta() {
        JPanel p = new Redondeado(12, true);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    public static Component espacio(int alto) {
        return Box.createVerticalStrut(alto);
    }

    // ───────────── Avisos ─────────────

    /** Aviso de error o éxito (.aviso.error / .aviso.exito); oculto mientras no haya texto. */
    public static final class Aviso extends Redondeado {
        private final JTextArea texto = suave("");
        private boolean exito;

        public Aviso() {
            super(8, true);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            texto.setFont(texto.getFont().deriveFont(13f));
            add(texto);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setVisible(false);
            Tema.alCambiar(this::colorear);
        }

        public void error(String mensaje) {
            mostrar(mensaje, false);
        }

        public void exito(String mensaje) {
            mostrar(mensaje, true);
        }

        public void ocultar() {
            setVisible(false);
        }

        private void mostrar(String mensaje, boolean esExito) {
            exito = esExito;
            texto.setText(mensaje);
            colorear();
            setVisible(true);
            revalidate();
        }

        private void colorear() {
            Tema.Paleta p = Tema.c();
            fondo = exito ? p.exitoFondo() : p.errorFondo();
            borde = exito ? p.exitoBorde() : p.errorBorde();
            texto.setForeground(exito ? p.exitoTexto() : p.errorTexto());
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** Panel con esquinas redondeadas; por defecto, color de superficie y borde del tema. */
    public static class Redondeado extends JPanel {
        private final int arco;
        Color fondo;
        Color borde;

        public Redondeado(int arco, boolean conBorde) {
            this.arco = arco;
            setOpaque(false);
            Tema.alCambiar(() -> {
                fondo = Tema.c().superficie();
                borde = conBorde ? Tema.c().borde() : null;
                repaint();
            });
        }

        public void colores(Color fondo, Color borde) {
            this.fondo = fondo;
            this.borde = borde;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (fondo != null) {
                g2.setColor(fondo);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco * 2, arco * 2);
            }
            if (borde != null) {
                g2.setColor(borde);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco * 2, arco * 2);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ───────────── Marca ─────────────

    /** Ícono + "PRPagos", como el enlace .marca de la barra web. */
    public static JLabel marca() {
        JLabel l = new JLabel("PRPagos");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 18f));
        l.setIconTextGap(8);
        Image icono = icono();
        if (icono != null) {
            l.setIcon(new ImageIcon(icono.getScaledInstance(28, 28, Image.SCALE_SMOOTH)));
        }
        return l;
    }

    static Image icono() {
        try {
            return ImageIO.read(new File("resources/icono.png"));
        } catch (Exception e) {
            return null;
        }
    }

    /** Envuelve un contenido con ancho máximo y centrado (como main { max-width }). */
    public static JPanel centrado(JComponent contenido, int anchoMaximo) {
        JPanel envoltura = new JPanel(new java.awt.GridBagLayout());
        envoltura.setOpaque(false);
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.weightx = 1;
        g.weighty = 1;
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
        g.anchor = java.awt.GridBagConstraints.NORTH;
        JPanel limite = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(Math.min(d.width, anchoMaximo), d.height);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(anchoMaximo, super.getMaximumSize().height);
            }
        };
        limite.setOpaque(false);
        limite.add(contenido);
        envoltura.add(limite, g);
        return envoltura;
    }

    /** Ancho fijo para tarjetas de formulario (.tarjeta-formulario: 420px). */
    public static <T extends JComponent> T ancho(T c, int ancho) {
        c.setMaximumSize(new Dimension(ancho, Integer.MAX_VALUE));
        c.setPreferredSize(null);
        return c;
    }

    /** Utilidad para mensajes de error de la base, sin volcar trazas al usuario. */
    public static Function<Exception, String> mensajeError(String accion) {
        return e -> "No se pudo " + accion + ": "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
