package com.prpagos.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Campo del código de 6 dígitos dibujado como seis casillas con relieve, igual
 * que en la web (acceso.js / acceso.css): cada casilla se hunde al escribir y
 * se ilumina al llenarse, la activa lleva el cursor que parpadea. Sigue siendo
 * un JTextField (getText, setText, Enter, pegar), así que la lógica de la
 * ventana no cambia. Si se permite, "Usar un código de respaldo" vuelve al
 * campo normal (los códigos de respaldo tienen otro largo); pegar uno con
 * letras también lo hace solo.
 */
final class CampoCasillas extends JTextField {

    private static final int N = 6;
    private static final int LARGO_RESPALDO = 10;

    private final boolean permiteRespaldo;
    private boolean respaldo;
    private final double[] pulso = new double[N];
    private boolean cursorVisible = true;
    private final Border bordeNormal;
    private Runnable alCambiarModo = () -> { };

    CampoCasillas(boolean permiteRespaldo) {
        this.permiteRespaldo = permiteRespaldo;
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        bordeNormal = getBorder();
        ((AbstractDocument) getDocument()).setDocumentFilter(new Filtro());
        Timer parpadeo = new Timer(500, e -> {
            cursorVisible = !cursorVisible;
            if (!respaldo && isFocusOwner()) {
                repaint();
            }
        });
        parpadeo.start();
        addCaretListener(e -> {
            if (!respaldo && e.getDot() != getDocument().getLength()) {
                SwingUtilities.invokeLater(() -> setCaretPosition(getDocument().getLength()));
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        aplicarModo();
    }

    void alCambiarModo(Runnable r) {
        alCambiarModo = r;
    }

    boolean enRespaldo() {
        return respaldo;
    }

    void setRespaldo(boolean si) {
        if (si && !permiteRespaldo) {
            return;
        }
        respaldo = si;
        if (!si) {
            String digitos = getText().replaceAll("\\D", "");
            setText(digitos.substring(0, Math.min(N, digitos.length())));
        }
        aplicarModo();
        alCambiarModo.run();
        requestFocusInWindow();
    }

    private void aplicarModo() {
        setOpaque(respaldo);
        setBorder(respaldo ? bordeNormal : BorderFactory.createEmptyBorder());
        setCaretColor(respaldo ? null : new Color(0, 0, 0, 0));
        setPreferredSize(new Dimension(280, respaldo ? 44 : 56));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, respaldo ? 44 : 56));
        revalidate();
        repaint();
    }

    /** Vacía las casillas (después de un código equivocado, tras el temblor). */
    void limpiar() {
        Timer t = new Timer(Efectos.reducir() ? 0 : 420, e -> {
            setText("");
            requestFocusInWindow();
        });
        t.setRepeats(false);
        t.start();
    }

    void temblar() {
        Efectos.temblar(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (respaldo) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Tema.Paleta p = Tema.c();
        String v = getText();
        int espacio = getWidth() < 300 ? 6 : 9;
        double ancho = (getWidth() - espacio * (N - 1)) / (double) N;
        int alto = getHeight() - 4;
        Color borde = mezcla(p.textoSuave(), p.borde(), 0.7);
        Color acento = Tema.esOscuro() ? p.enlace() : p.acento();
        g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
        FontMetrics fm = g2.getFontMetrics();
        int activa = Math.min(v.length(), N - 1);
        for (int i = 0; i < N; i++) {
            double x = i * (ancho + espacio);
            boolean llena = i < v.length();
            boolean esActiva = isFocusOwner() && i == activa;
            Graphics2D c = (Graphics2D) g2.create();
            double s = 1 + 0.12 * Math.sin(Math.PI * pulso[i]);
            c.translate(x + ancho / 2, 2 + alto / 2.0 - 2 * Math.sin(Math.PI * pulso[i]));
            c.scale(s, s);
            c.translate(-ancho / 2, -alto / 2.0);
            int w = (int) Math.round(ancho);
            if (llena) {
                c.setColor(new Color(acento.getRed(), acento.getGreen(), acento.getBlue(), 40));
                c.fillRoundRect(1, 5, w - 2, alto - 2, 16, 16);
                c.setColor(p.superficie());
            } else {
                c.setColor(Tema.c().accesoFondo());
            }
            c.fillRoundRect(0, 0, w, alto, 16, 16);
            if (!llena) {
                // Relieve hundido: sombra interior arriba a la izquierda.
                c.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, alto, 16, 16));
                c.setStroke(new BasicStroke(5f));
                c.setColor(new Color(0, 0, 0, Tema.esOscuro() ? 70 : 22));
                c.drawRoundRect(-2, -2, w + 1, alto + 1, 18, 18);
                c.setClip(null);
            }
            if (esActiva) {
                Color pl = p.plataformaSuave();
                c.setColor(pl);
                c.setStroke(new BasicStroke(6f));
                c.drawRoundRect(-2, -2, w + 3, alto + 3, 20, 20);
            }
            c.setStroke(new BasicStroke(1.5f));
            c.setColor(esActiva ? p.plataforma() : (llena || v.length() == N ? acento : borde));
            c.drawRoundRect(0, 0, w - 1, alto - 1, 16, 16);
            if (llena) {
                String d = v.substring(i, i + 1);
                c.setColor(p.texto());
                c.drawString(d, (w - fm.stringWidth(d)) / 2f, (alto + fm.getAscent() - fm.getDescent()) / 2f);
            } else if (esActiva && cursorVisible) {
                c.setColor(p.plataforma());
                c.fillRect(w / 2 - 1, alto / 2 - 11, 2, 22);
            }
            c.dispose();
        }
        g2.dispose();
    }

    private void pulsar(int i) {
        if (i < 0 || i >= N || Efectos.reducir()) {
            return;
        }
        Efectos.animar(280, Efectos.REBOTE, t -> { pulso[i] = t; repaint(); }, () -> { pulso[i] = 0; repaint(); });
    }

    private static Color mezcla(Color a, Color b, double pa) {
        return new Color((int) (a.getRed() * pa + b.getRed() * (1 - pa)), (int) (a.getGreen() * pa + b.getGreen() * (1 - pa)),
                (int) (a.getBlue() * pa + b.getBlue() * (1 - pa)));
    }

    /** Solo dígitos y máximo 6 en las casillas; un código con letras pasa al modo respaldo. */
    private final class Filtro extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String texto, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, texto, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int largo, String texto, AttributeSet attr) throws BadLocationException {
            if (texto == null) {
                texto = "";
            }
            if (!respaldo && permiteRespaldo && texto.matches(".*[^\\d\\s].*")) {
                respaldo = true;
                SwingUtilities.invokeLater(() -> { aplicarModo(); alCambiarModo.run(); });
            }
            if (respaldo) {
                int cabe = LARGO_RESPALDO - (fb.getDocument().getLength() - largo);
                fb.replace(offset, largo, texto.substring(0, Math.max(0, Math.min(cabe, texto.length()))), attr);
                return;
            }
            String digitos = texto.replaceAll("\\D", "");
            int antes = fb.getDocument().getLength() - largo;
            int cabe = N - antes;
            digitos = digitos.substring(0, Math.max(0, Math.min(cabe, digitos.length())));
            fb.replace(offset, largo, digitos, attr);
            for (int i = 0; i < digitos.length(); i++) {
                pulsar(offset + i);
            }
        }
    }
}
