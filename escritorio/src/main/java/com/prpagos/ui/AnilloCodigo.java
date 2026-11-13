package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Tiempo que le queda al código (fragmentos/anillo.html en la web): en modo
 * app, cuánto falta para que la app muestre otro (cambia cada 30 s); en modo
 * correo, la cuenta regresiva hasta que vence.
 */
final class AnilloCodigo extends JPanel {

    private final JLabel texto = new JLabel();
    private final Arco arco = new Arco();
    private long vence;
    private int minutos;
    private final Timer reloj = new Timer(1000, e -> actualizar());

    AnilloCodigo() {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        texto.setFont(texto.getFont().deriveFont(13f));
        Tema.alCambiar(() -> texto.setForeground(Tema.c().textoSuave()));
        add(arco);
        add(texto);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, -8, 0, 0));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        modoApp();
        reloj.start();
    }

    void modoApp() {
        vence = 0;
        actualizar();
    }

    void modoCorreo(int minutosVigencia) {
        minutos = minutosVigencia <= 0 ? 10 : minutosVigencia;
        vence = System.currentTimeMillis() + minutos * 60_000L;
        actualizar();
    }

    /** "Vence en 10 minutos" → 10 (si el mensaje no lo dice, 10). */
    static int minutosDe(String mensaje) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*minuto").matcher(mensaje == null ? "" : mensaje);
        return m.find() ? Integer.parseInt(m.group(1)) : 10;
    }

    private void actualizar() {
        int resto;
        int total;
        if (vence > 0) {
            total = minutos * 60;
            resto = (int) Math.max(0, Math.round((vence - System.currentTimeMillis()) / 1000.0));
            texto.setText(resto > 0 ? String.format("El código vence en %d:%02d", resto / 60, resto % 60)
                    : "El código venció. Vuelve a ingresar para recibir otro.");
            arco.urgente = resto <= 60;
        } else {
            total = 30;
            resto = 30 - (int) (System.currentTimeMillis() / 1000 % 30);
            texto.setText("El código de tu app cambia en " + resto + " s");
            arco.urgente = resto <= 5;
        }
        arco.valor = resto / (double) total;
        arco.repaint();
    }

    private static final class Arco extends JComponent {
        double valor = 1;
        boolean urgente;

        Arco() {
            setPreferredSize(new Dimension(22, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Tema.c().borde());
            g2.drawOval(2, 2, 18, 18);
            Color c = urgente ? Tema.c().acento() : Tema.c().plataforma();
            g2.setColor(c);
            g2.draw(new Arc2D.Double(2, 2, 18, 18, 90, 360 * valor, Arc2D.OPEN));
            g2.dispose();
        }
    }
}
