package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.prpagos.datos.Pago;
import com.prpagos.datos.RepositorioPagos;
import com.prpagos.datos.Sesion;

/**
 * "Nuevo registro" y "Modificar registro #N": la misma tarjeta de
 * formulario de registros_nuevo.html y registros_editar.html de la web.
 */
class PanelPago extends JPanel {

    private final VentanaPrincipal ventana;
    private final Sesion sesion;
    private final boolean edicion;
    private final JLabel titulo;
    private final JTextField monto = Ui.campo(null);
    private final SelectorFecha fecha = new SelectorFecha();
    private final JTextField concepto = Ui.campo(null);
    private final Ui.Aviso aviso = new Ui.Aviso();
    private Pago actual;

    static PanelPago nuevo(VentanaPrincipal ventana, Sesion sesion) {
        return new PanelPago(ventana, sesion, false);
    }

    static PanelPago edicion(VentanaPrincipal ventana, Sesion sesion) {
        return new PanelPago(ventana, sesion, true);
    }

    private PanelPago(VentanaPrincipal ventana, Sesion sesion, boolean edicion) {
        this.ventana = ventana;
        this.sesion = sesion;
        this.edicion = edicion;
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(javax.swing.BorderFactory.createEmptyBorder(30, 32, 30, 32));
        titulo = Ui.titulo(edicion ? "Modificar registro" : "Nuevo registro", 26f);
        JButton guardar = Ui.primario(edicion ? "Guardar cambios" : "Guardar", e -> guardar());
        guardar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        concepto.addActionListener(e -> guardar());

        for (Component c : new Component[] {
            titulo, Ui.espacio(10), aviso,
            Ui.etiqueta("Monto"), monto,
            Ui.etiqueta("Fecha"), fecha,
            Ui.etiqueta("Concepto"), concepto,
            Ui.espacio(20), guardar, Ui.espacio(14),
            Ui.enlace("Volver a consultar registros", e -> ventana.ir(VentanaPrincipal.CONSULTA)),
        }) {
            tarjeta.add(c);
        }
        if (edicion) {
            JTextArea nota = Ui.suave("Solo puedes modificar registros que te pertenecen; si el registro fue "
                    + "creado por otro usuario, el sistema rechaza el cambio.");
            nota.setFont(nota.getFont().deriveFont(12f));
            tarjeta.add(Ui.espacio(12));
            tarjeta.add(nota);
        }

        // Ancho de .tarjeta-formulario (420 px) centrado.
        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.X_AXIS));
        tarjeta.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));
        tarjeta.setPreferredSize(new Dimension(440, tarjeta.getPreferredSize().height));
        centro.add(javax.swing.Box.createHorizontalGlue());
        centro.add(tarjeta);
        centro.add(javax.swing.Box.createHorizontalGlue());
        add(centro, BorderLayout.NORTH);
    }

    void limpiar() {
        actual = null;
        monto.setText("");
        fecha.setFechaSeleccionada(LocalDate.now());
        concepto.setText("");
        aviso.ocultar();
    }

    void cargar(Pago pago) {
        actual = pago;
        titulo.setText("Modificar registro #" + pago.idPago());
        monto.setText(String.format(Locale.ROOT, "%.2f", pago.monto()));
        fecha.setFechaSeleccionada(pago.fecha());
        concepto.setText(pago.concepto());
        aviso.ocultar();
    }

    private void guardar() {
        double valor;
        try {
            valor = Double.parseDouble(monto.getText().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            aviso.error("El monto debe ser un número");
            return;
        }
        String texto = concepto.getText().trim();
        if (texto.isEmpty()) {
            aviso.error("Escribe el concepto");
            return;
        }
        LocalDate dia = fecha.getFechaSeleccionada();
        if (edicion && actual != null) {
            long id = actual.idPago();
            Tarea.ejecutar(this, () -> RepositorioPagos.actualizar(sesion.usuarioId(), id, valor, dia, texto),
                    ok -> {
                        if (ok) {
                            ventana.volverAConsulta("Registro #" + id + " actualizado");
                        } else {
                            aviso.error("No se pudo modificar: este registro no te pertenece.");
                        }
                    },
                    e -> aviso.error(Ui.mensajeError("guardar los cambios").apply(e)));
        } else {
            Tarea.ejecutar(this, () -> {
                RepositorioPagos.insertar(sesion.usuarioId(), valor, dia, texto);
                return null;
            }, r -> ventana.volverAConsulta("Registro guardado"),
                    e -> aviso.error(Ui.mensajeError("guardar el registro").apply(e)));
        }
    }
}
