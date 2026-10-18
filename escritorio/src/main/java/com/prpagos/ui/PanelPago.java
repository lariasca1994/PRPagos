package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.datos.Pago;

/**
 * "Nuevo registro" y "Modificar registro #N": la misma tarjeta, los mismos
 * campos (tipo, monto, fecha, categoría, concepto) y textos que
 * registros_nuevo.html y registros_editar.html de la web. Las categorías, la
 * validación y los mensajes vienen del servidor (la misma lógica de la web).
 */
class PanelPago extends JPanel {

    private static final String NOTA_USUARIO = "Solo puedes modificar registros que te pertenecen; si el registro "
            + "fue creado por otro usuario, el sistema rechaza el cambio.";
    private static final String NOTA_ADMIN = "Como admin puedes modificar el registro de cualquier usuario.";

    /** Opción del combo de categorías (null = "Sin categoría"). */
    private record OpcionCategoria(Long id, String texto, String tipo) {
        @Override
        public String toString() {
            return texto;
        }
    }

    private final VentanaPrincipal ventana;
    private final boolean edicion;
    private final JLabel titulo;
    private final JTextArea gestionando = Ui.suave("Gestionando como admin.");
    private final JTextArea nota = Ui.suave(NOTA_USUARIO);
    private final JToggleButton gasto = new JToggleButton("Gasto");
    private final JToggleButton ingreso = new JToggleButton("Ingreso");
    private final JTextField monto = Ui.campo(null);
    private final SelectorFecha fecha = new SelectorFecha();
    private final JComboBox<OpcionCategoria> categoria = new JComboBox<>();
    private final JTextField concepto = Ui.campo(null);
    private final Ui.Aviso aviso = new Ui.Aviso();
    private List<OpcionCategoria> todas = new ArrayList<>();
    private Pago actual;

    static PanelPago nuevo(VentanaPrincipal ventana) {
        return new PanelPago(ventana, false);
    }

    static PanelPago edicion(VentanaPrincipal ventana) {
        return new PanelPago(ventana, true);
    }

    private PanelPago(VentanaPrincipal ventana, boolean edicion) {
        this.ventana = ventana;
        this.edicion = edicion;
        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
        titulo = Ui.titulo(edicion ? "Modificar registro" : "Nuevo registro", 26f);
        JButton guardar = Ui.primario(edicion ? "Guardar cambios" : "Guardar", e -> guardar());
        guardar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        concepto.addActionListener(e -> guardar());
        gestionando.setFont(gestionando.getFont().deriveFont(12.5f));
        gestionando.setVisible(false);
        nota.setFont(nota.getFont().deriveFont(12f));
        categoria.setAlignmentX(LEFT_ALIGNMENT);
        categoria.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        for (Component c : new Component[] {
            titulo, Ui.espacio(6), gestionando, Ui.espacio(6), aviso,
            Ui.etiqueta("Tipo"), selectorTipo(),
            Ui.etiqueta("Monto"), monto,
            Ui.etiqueta("Fecha"), fecha,
            Ui.etiqueta("Categoría"), categoria,
            Ui.etiqueta("Concepto"), concepto,
            Ui.espacio(20), guardar, Ui.espacio(14),
            Ui.enlace("Volver a consultar registros", e -> ventana.ir(VentanaPrincipal.CONSULTA)),
        }) {
            tarjeta.add(c);
        }
        if (edicion) {
            tarjeta.add(Ui.espacio(12));
            tarjeta.add(nota);
        }

        // Ancho de .tarjeta-formulario (420 px) centrado.
        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.X_AXIS));
        tarjeta.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));
        tarjeta.setPreferredSize(new Dimension(440, tarjeta.getPreferredSize().height + 60));
        centro.add(javax.swing.Box.createHorizontalGlue());
        centro.add(tarjeta);
        centro.add(javax.swing.Box.createHorizontalGlue());
        add(centro, BorderLayout.NORTH);
    }

    /** Dos botones como .selector-tipo de la web: Gasto en rojo, Ingreso en azul. */
    private JPanel selectorTipo() {
        JPanel fila = new JPanel(new GridLayout(1, 2, 8, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        javax.swing.ButtonGroup grupo = new javax.swing.ButtonGroup();
        for (JToggleButton b : new JToggleButton[] {gasto, ingreso}) {
            grupo.add(b);
            b.setFocusPainted(false);
            b.addActionListener(e -> {
                pintarTipo();
                filtrarCategorias(null);
            });
            fila.add(b);
        }
        gasto.setSelected(true);
        Tema.alCambiar(this::pintarTipo);
        return fila;
    }

    private void pintarTipo() {
        Tema.Paleta p = Tema.c();
        estiloTipo(gasto, gasto.isSelected(), p.acento(), p.acentoSuave());
        estiloTipo(ingreso, ingreso.isSelected(), p.plataforma(), p.plataformaSuave());
    }

    private static void estiloTipo(JToggleButton b, boolean activo, java.awt.Color color, java.awt.Color suave) {
        String hex = String.format("#%06x", color.getRGB() & 0xFFFFFF);
        String fondo = String.format("#%06x", suave.getRGB() & 0xFFFFFF);
        b.putClientProperty(FlatClientProperties.STYLE, activo
                ? "background: " + fondo + "; selectedBackground: " + fondo + "; foreground: " + hex
                  + "; selectedForeground: " + hex + "; borderColor: " + hex + "; font: bold"
                : "font: bold");
    }

    private String tipo() {
        return ingreso.isSelected() ? "INGRESO" : "GASTO";
    }

    /** Las categorías del dueño del registro (las da la API) y la seleccionada. */
    private void cargarCategorias(Long idPago, Long seleccion) {
        String ruta = "/api/registros/formulario" + (idPago == null ? "" : "?id=" + idPago);
        Tarea.ejecutar(this, () -> Api.get(ruta), datos -> {
            JsonObject grupos = Json.objeto(datos, "grupos");
            todas = new ArrayList<>();
            todas.add(new OpcionCategoria(null, "Sin categoría", "TODOS"));
            for (JsonObject c : Json.lista(datos, "categorias")) {
                String grupo = Json.textoONulo(c, "grupo");
                String texto = Json.texto(c, "nombre") + (grupo == null ? "" : " · " + Json.texto(grupos, grupo));
                todas.add(new OpcionCategoria(Json.entero(c, "id"), texto, Json.texto(c, "tipo")));
            }
            if (idPago == null && fecha.getFechaSeleccionada() == null) {
                fecha.setFechaSeleccionada(LocalDate.parse(Json.texto(datos, "hoy")));
            }
            filtrarCategorias(seleccion);
        }, e -> aviso.error(Ui.mensajeError("cargar las categorías").apply(e)));
    }

    private void filtrarCategorias(Long seleccion) {
        DefaultComboBoxModel<OpcionCategoria> modelo = new DefaultComboBoxModel<>();
        OpcionCategoria elegida = null;
        for (OpcionCategoria o : todas) {
            if ("TODOS".equals(o.tipo()) || tipo().equals(o.tipo())) {
                modelo.addElement(o);
                if (seleccion != null && seleccion.equals(o.id())) {
                    elegida = o;
                }
            }
        }
        categoria.setModel(modelo);
        if (elegida != null) {
            categoria.setSelectedItem(elegida);
        }
    }

    void limpiar() {
        actual = null;
        gasto.setSelected(true);
        pintarTipo();
        monto.setText("");
        fecha.setFechaSeleccionada(null);
        concepto.setText("");
        aviso.ocultar();
        cargarCategorias(null, null);
    }

    void cargar(Pago pago) {
        actual = pago;
        boolean admin = ventana.sesion().admin();
        titulo.setText("Modificar registro #" + pago.idPago());
        gestionando.setVisible(admin);
        nota.setText(admin ? NOTA_ADMIN : NOTA_USUARIO);
        (pago.esIngreso() ? ingreso : gasto).setSelected(true);
        pintarTipo();
        monto.setText(String.format(Locale.ROOT, "%s", pago.monto()));
        fecha.setFechaSeleccionada(pago.fecha());
        concepto.setText(pago.concepto());
        aviso.ocultar();
        cargarCategorias(pago.idPago(), pago.categoriaId());
    }

    private void guardar() {
        OpcionCategoria op = (OpcionCategoria) categoria.getSelectedItem();
        LocalDate dia = fecha.getFechaSeleccionada();
        // El monto y el concepto van como se escribieron: el servidor los valida (igual que en la web).
        JsonObject datos = Json.de("tipo", tipo(), "monto", monto.getText(), "fecha", dia == null ? "" : dia.toString(),
                "categoria_id", op == null ? null : op.id(), "concepto", concepto.getText());
        if (edicion && actual != null) {
            long id = actual.idPago();
            Tarea.ejecutar(this, () -> Api.put("/api/registros/" + id, datos),
                    r -> ventana.volverAConsulta(Json.texto(r, "mensaje"), true), e -> {
                        String m = Ui.mensajeError("guardar el registro").apply(e);
                        if (m.startsWith("No se pudo modificar")) {
                            ventana.volverAConsulta(m, false);
                        } else {
                            aviso.error(m);
                        }
                    });
        } else {
            Tarea.ejecutar(this, () -> Api.post("/api/registros", datos),
                    r -> ventana.volverAConsulta(Json.texto(r, "mensaje"), true),
                    e -> aviso.error(Ui.mensajeError("guardar el registro").apply(e)));
        }
    }
}
