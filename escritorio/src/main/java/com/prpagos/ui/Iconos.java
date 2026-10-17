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
}
