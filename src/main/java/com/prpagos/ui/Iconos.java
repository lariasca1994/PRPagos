package com.prpagos.ui;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.Image;
import java.io.File;
import java.io.IOException;

/**
 * Aplica el ícono de la aplicación a cualquier ventana. Antes el proyecto
 * no tenía ningún ícono propio (se veía con el ícono genérico de Java) —
 * ahora todas las ventanas usan resources/icono.png.
 */
public final class Iconos {

    private Iconos() {
    }

    public static void aplicarIcono(JFrame ventana) {
        try {
            Image icono = ImageIO.read(new File("resources/icono.png"));
            ventana.setIconImage(icono);
        } catch (IOException e) {
            // Si no se encuentra el ícono, la ventana sigue funcionando
            // normal, solo se ve con el ícono genérico de Java.
            System.err.println("No se pudo cargar resources/icono.png: " + e.getMessage());
        }
    }
}
