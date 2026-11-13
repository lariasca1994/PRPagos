package com.prpagos.ui;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Corre el acceso a la base (y el PBKDF2, que tarda ~medio segundo a
 * propósito) fuera del hilo de la interfaz, para que la ventana no se
 * congele. Mientras tanto muestra el cursor de espera y bloquea la ventana.
 */
public final class Tarea {

    private Tarea() {
    }

    public static <T> void ejecutar(Component origen, Callable<T> trabajo, Consumer<T> alTerminar,
                                    Consumer<Exception> alFallar) {
        Window ventana = SwingUtilities.getWindowAncestor(origen);
        if (ventana != null) {
            ventana.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            ventana.setEnabled(false);
        }
        // El botón que lanzó la tarea muestra "enviando", como en la web.
        BotonAnimado boton = BotonAnimado.reciente();
        if (boton != null) {
            boton.setOcupado(true);
        }
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return trabajo.call();
            }

            @Override
            protected void done() {
                if (ventana != null) {
                    ventana.setEnabled(true);
                    ventana.setCursor(Cursor.getDefaultCursor());
                    ventana.toFront();
                }
                if (boton != null) {
                    boton.setOcupado(false);
                }
                try {
                    alTerminar.accept(get());
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause();
                    if (boton != null) {
                        boton.temblar();
                    }
                    // Servidor en pausa (503) o sin conexión: pantalla propia, como la web.
                    if (causa instanceof com.prpagos.cliente.Api.ServicioEnPausa
                            || causa instanceof com.prpagos.cliente.Api.SinConexion) {
                        PantallaEstado.mostrar(ventana, causa instanceof com.prpagos.cliente.Api.SinConexion);
                    }
                    alFallar.accept(causa instanceof Exception ex ? ex : new Exception(causa));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
