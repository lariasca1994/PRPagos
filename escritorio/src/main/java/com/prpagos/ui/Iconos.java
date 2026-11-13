package com.prpagos.ui;

import java.awt.Image;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * Ícono de la aplicación (el mismo de la web). En el ejecutable empaquetado
 * viene dentro del .jar (/recursos/icono.png); al correr desde el código se
 * lee de resources/icono.png.
 */
public final class Iconos {

    private static Image icono;

    private Iconos() {
    }

    static synchronized Image icono() {
        if (icono == null) {
            try (InputStream in = Iconos.class.getResourceAsStream("/recursos/icono.png")) {
                icono = in != null ? ImageIO.read(in) : ImageIO.read(new File("resources/icono.png"));
            } catch (Exception e) {
                System.err.println("No se pudo cargar el ícono: " + e.getMessage());
            }
        }
        return icono;
    }

    public static void aplicarIcono(JFrame ventana) {
        Image base = icono();
        if (base == null) {
            return;
        }
        // Varios tamaños: Windows usa el más nítido para la barra de tareas y la ventana.
        List<Image> tamanos = new ArrayList<>();
        for (int lado : new int[] {16, 24, 32, 48, 64, 128, 256}) {
            tamanos.add(base.getScaledInstance(lado, lado, Image.SCALE_SMOOTH));
        }
        ventana.setIconImages(tamanos);
    }

    // ───────────── Íconos del menú (mismos trazos que fragmentos/iconos.html) ─────────────

    /** Dibuja el ícono {@code nombre} en un cuadro de {@code lado} px con esquina en (x, y). */
    static void dibujar(java.awt.Graphics2D g, String nombre, double x, double y, double lado, java.awt.Color color) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        g2.scale(lado / 24.0, lado / 24.0);
        g2.setColor(color);
        float grosor = "mas".equals(nombre) ? 3f : 1.8f;
        g2.setStroke(new java.awt.BasicStroke(grosor, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        switch (nombre) {
            case "dashboard" -> {
                g2.draw(new java.awt.geom.Rectangle2D.Double(4, 4, 6, 9));
                g2.draw(new java.awt.geom.Rectangle2D.Double(14, 11, 6, 9));
                g2.draw(new java.awt.geom.Rectangle2D.Double(14, 4, 6, 4));
                g2.draw(new java.awt.geom.Rectangle2D.Double(4, 17, 6, 3));
            }
            case "presupuesto" -> {
                p.moveTo(12, 3);
                p.lineTo(12, 12);
                p.lineTo(21, 12);
                g2.draw(p);
                g2.draw(new java.awt.geom.Arc2D.Double(3, 3, 18, 18, -19.4, -231.2, java.awt.geom.Arc2D.OPEN));
            }
            case "registros" -> {
                for (int fila : new int[] {6, 12, 18}) {
                    p.moveTo(8, fila);
                    p.lineTo(21, fila);
                    p.moveTo(3.5, fila);
                    p.lineTo(3.51, fila);
                }
                g2.draw(p);
            }
            case "nuevo" -> {
                g2.draw(new java.awt.geom.Ellipse2D.Double(3, 3, 18, 18));
                p.moveTo(12, 8);
                p.lineTo(12, 16);
                p.moveTo(8, 12);
                p.lineTo(16, 12);
                g2.draw(p);
            }
            case "perfil" -> {
                g2.draw(new java.awt.geom.Ellipse2D.Double(8, 3, 8, 8));
                g2.draw(new java.awt.geom.Arc2D.Double(4, 13, 16, 16, 0, 180, java.awt.geom.Arc2D.OPEN));
            }
            case "usuarios" -> {
                g2.draw(new java.awt.geom.Ellipse2D.Double(7, 3, 8, 8));
                g2.draw(new java.awt.geom.Arc2D.Double(5, 15, 12, 12, 0, 180, java.awt.geom.Arc2D.OPEN));
                p.moveTo(21, 21);
                p.quadTo(21, 16.6, 17, 16.1);
                p.moveTo(16, 3.2);
                p.quadTo(19.4, 7, 16, 10.8);
                g2.draw(p);
            }
            case "salir" -> {
                p.moveTo(15, 4);
                p.lineTo(18, 4);
                p.quadTo(20, 4, 20, 6);
                p.lineTo(20, 18);
                p.quadTo(20, 20, 18, 20);
                p.lineTo(15, 20);
                p.moveTo(10, 17);
                p.lineTo(5, 12);
                p.lineTo(10, 7);
                p.moveTo(5, 12);
                p.lineTo(16, 12);
                g2.draw(p);
            }
            case "alternar" -> {
                p.moveTo(11, 17);
                p.lineTo(6, 12);
                p.lineTo(11, 7);
                p.moveTo(18, 17);
                p.lineTo(13, 12);
                p.lineTo(18, 7);
                g2.draw(p);
            }
            case "mas" -> {
                for (int cx : new int[] {5, 12, 19}) {
                    p.moveTo(cx, 12);
                    p.lineTo(cx + 0.01, 12);
                }
                g2.draw(p);
            }
            case "tema" -> {
                java.awt.geom.Area luna = new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(3, 3, 18, 18));
                luna.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(9.5, 1, 14, 14)));
                g2.draw(luna);
            }
            default -> { }
        }
        g2.dispose();
    }
}
