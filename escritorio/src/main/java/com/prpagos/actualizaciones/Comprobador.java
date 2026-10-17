package com.prpagos.actualizaciones;

import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Comprueba si hay versión nueva al abrir la aplicación y luego cada hora (como
 * el actualizador de SysPulse). Cuando una versión queda descargada y
 * verificada, avisa: "Reiniciar ahora" la instala y vuelve a abrir PRPagos;
 * "Al cerrar" la instala cuando se cierre la aplicación. Las comprobaciones
 * corren en segundo plano y nunca bloquean la interfaz.
 */
public final class Comprobador {

    private static final long HORAS_ENTRE_COMPROBACIONES = 1;
    private static Path lista;
    private static boolean instalacionProgramada;

    private Comprobador() {
    }

    public static synchronized void iniciar() {
        if (Actualizador.carpetaInstalada() == null) {
            return; // Desde el código: no hay nada que actualizar.
        }
        ScheduledExecutorService tareas = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "prpagos-actualizaciones");
            hilo.setDaemon(true);
            return hilo;
        });
        tareas.scheduleWithFixedDelay(Comprobador::comprobar, 5, HORAS_ENTRE_COMPROBACIONES * 3600,
                TimeUnit.SECONDS);
        // Si se descargó una versión y el aviso quedó en "Al cerrar", se instala al salir.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> programar(false), "prpagos-instalar"));
    }

    private static void comprobar() {
        synchronized (Comprobador.class) {
            if (lista != null) {
                return;
            }
        }
        Path nueva = Actualizador.prepararSiHayNueva();
        if (nueva == null) {
            return;
        }
        synchronized (Comprobador.class) {
            lista = nueva;
        }
        SwingUtilities.invokeLater(Comprobador::avisar);
    }

    private static void avisar() {
        Window ventana = null;
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) {
                ventana = w;
            }
        }
        Object[] opciones = {"Reiniciar ahora", "Al cerrar"};
        int eleccion = JOptionPane.showOptionDialog(ventana,
                "Hay una versión nueva de PRPagos, ya descargada y verificada.\n"
                        + "Puedes reiniciar ahora para instalarla (se cerrará la sesión) o se instalará\n"
                        + "automáticamente cuando cierres la aplicación.",
                "Actualización disponible", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opciones, opciones[1]);
        if (eleccion == 0 && programar(true)) {
            System.exit(0);
        }
    }

    /** Lanza el instalador una sola vez; con {@code abrir}, la aplicación vuelve a abrirse al terminar. */
    private static synchronized boolean programar(boolean abrir) {
        if (lista == null || instalacionProgramada) {
            return false;
        }
        try {
            Actualizador.instalarAlCerrar(lista, abrir);
            instalacionProgramada = true;
            return true;
        } catch (IOException | RuntimeException e) {
            Actualizador.registrar("No se pudo programar la instalación: " + e.getMessage());
            return false;
        }
    }
}
