package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;

import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Candado pequeño sobre el título del paso del código (fragmentos/candado.html
 * en la web): flota despacio sobre una ficha con relieve; con el código
 * correcto el arco sube y se abre; con uno equivocado se sacude. Es
 * decorativo: el resultado siempre va también en texto.
 */
final class Candado extends JComponent {

    private double flota;
    private double apertura;
    private double sacudida;
    private boolean error;
    private boolean verificando;
    private final Timer ambiente = new Timer(33, e -> {
        flota += 0.033;
        if (isShowing()) {
            repaint();
        }
    });

    Candado() {
        setPreferredSize(new Dimension(64, 64));
        setMaximumSize(new Dimension(64, 64));
        setAlignmentX(LEFT_ALIGNMENT);
        if (!Efectos.reducir()) {
            ambiente.start();
        }
    }

    void reiniciar() {
        apertura = 0;
        sacudida = 0;
        error = false;
        verificando = false;
        repaint();
    }

    void verificando(boolean si) {
        verificando = si;
        repaint();
    }

    void abrir() {
        verificando = false;
        error = false;
        Efectos.animar(500, Efectos.REBOTE, t -> { apertura = t; repaint(); }, null);
    }

    void sacudir() {
        verificando = false;
        error = true;
        Efectos.animar(450, Efectos.SUAVE, t -> { sacudida = t; repaint(); }, () -> {
            sacudida = 0;
            Timer fin = new Timer(900, e -> { error = false; repaint(); });
            fin.setRepeats(false);
            fin.start();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Tema.Paleta p = Tema.c();
        double dy = apertura > 0 || Efectos.reducir() ? 0 : -3 * Math.sin(Math.PI * ((flota / 3.6) % 1.0));
        g2.translate(2, 2 + dy + 1);
        int lado = 56;
        // Ficha con relieve (sombra oscura abajo a la derecha, clara arriba a la izquierda).
        for (int i = 6; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, Tema.esOscuro() ? 14 : 6));
            g2.fillRoundRect(i, i, lado, lado, 34, 34);
        }
        g2.setColor(p.superficie());
        g2.fillRoundRect(0, 0, lado, lado, 34, 34);
        g2.setColor(p.borde());
        g2.drawRoundRect(0, 0, lado, lado, 34, 34);

        Color color = error ? p.errorTexto() : apertura > 0 ? p.exitoTexto() : (Tema.esOscuro() ? p.enlace() : p.acento());
        double giro = sacudida > 0 ? Math.sin(sacudida * Math.PI * 4) * 12 * (1 - sacudida) : 0;
        g2.rotate(Math.toRadians(giro), lado / 2.0, lado / 2.0);
        // Escala del dibujo (48 unidades → 36 px), centrado.
        g2.translate(10, 9);
        g2.scale(0.75, 0.75);
        g2.setColor(color);
        Graphics2D arco = (Graphics2D) g2.create();
        arco.translate(0, -6 * apertura);
        arco.rotate(Math.toRadians(28 * apertura), 33, 21);
        arco.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D a = new Path2D.Double();
        a.moveTo(15, 22);
        a.lineTo(15, 15);
        a.append(new Arc2D.Double(15, 6, 18, 18, 180, -180, Arc2D.OPEN), true);
        a.lineTo(33, 22);
        arco.draw(a);
        arco.dispose();
        g2.fillRoundRect(9, 21, 30, 21, 12, 12);
        double r = 3.4 * (verificando ? 0.6 + 0.4 * Math.abs(Math.cos(flota * Math.PI)) : 1 - 0.4 * apertura);
        g2.setColor(p.superficie());
        g2.fill(new java.awt.geom.Ellipse2D.Double(24 - r, 31.5 - r, r * 2, r * 2));
        g2.dispose();
    }
}
