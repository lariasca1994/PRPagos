package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.sql.SQLException;
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
import com.prpagos.datos.Pago;
import com.prpagos.datos.RepositorioFinanzas;
import com.prpagos.datos.RepositorioPagos;
import com.prpagos.datos.Sesion;
import com.prpagos.finanzas.Finanzas;

/**
 * "Nuevo registro" y "Modificar registro #N": la misma tarjeta, los mismos
 * campos (tipo, monto, fecha, categoría, concepto), textos y mensajes que
 * registros_nuevo.html y registros_editar.html de la web.
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

    private Sesion sesion() {
        return ventana.sesion();
    }

    /** Carga las categorías del dueño del registro y deja seleccionada la indicada. */
    private void cargarCategorias(long duenoId, Long seleccion) {
        Tarea.ejecutar(this, () -> RepositorioFinanzas.categorias(duenoId), lista -> {
            todas = new ArrayList<>();
            todas.add(new OpcionCategoria(null, "Sin categoría", "TODOS"));
            for (RepositorioFinanzas.Categoria c : lista) {
                String grupo = c.grupo() == null ? "" : " · " + Finanzas.NOMBRE_GRUPO.get(c.grupo());
                todas.add(new OpcionCategoria(c.id(), c.nombre() + grupo, c.tipo()));
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
        fecha.setFechaSeleccionada(Finanzas.hoy());
        concepto.setText("");
        aviso.ocultar();
        cargarCategorias(sesion().usuarioId(), null);
    }

    void cargar(Pago pago) {
        actual = pago;
        boolean admin = sesion().admin();
        titulo.setText("Modificar registro #" + pago.idPago());
        gestionando.setVisible(admin);
        nota.setText(admin ? NOTA_ADMIN : NOTA_USUARIO);
        (pago.esIngreso() ? ingreso : gasto).setSelected(true);
        pintarTipo();
        monto.setText(String.format(Locale.ROOT, "%s", pago.monto()));
        fecha.setFechaSeleccionada(pago.fecha());
        concepto.setText(pago.concepto());
        aviso.ocultar();
        if (admin) {
            Tarea.ejecutar(this, () -> RepositorioPagos.dueno(pago.idPago()),
                    dueno -> cargarCategorias(dueno == null ? sesion().usuarioId() : dueno, pago.categoriaId()),
                    e -> aviso.error(Ui.mensajeError("cargar las categorías").apply(e)));
        } else {
            cargarCategorias(sesion().usuarioId(), pago.categoriaId());
        }
    }

    private void guardar() {
        double valor;
        try {
            // Coma o punto decimal, igual que la web. Debe ser mayor que 0.
            valor = Double.parseDouble(monto.getText().trim().replace(",", "."));
        } catch (NumberFormatException e) {
            valor = 0;
        }
        if (valor <= 0) {
            aviso.error("Monto inválido");
            return;
        }
        String texto = concepto.getText().trim();
        if (texto.isEmpty()) {
            aviso.error("Escribe el concepto");
            return;
        }
        String elTipo = tipo();
        OpcionCategoria op = (OpcionCategoria) categoria.getSelectedItem();
        Long categoriaId = op == null ? null : op.id();
        LocalDate dia = fecha.getFechaSeleccionada();
        long usuarioId = sesion().usuarioId();
        double monto = valor;
        if (edicion && actual != null) {
            long id = actual.idPago();
            Tarea.ejecutar(this, () -> {
                if (!ventana.verificarCuenta()) {
                    return null;
                }
                return ventana.sesion().admin()
                        ? RepositorioPagos.actualizarComoAdmin(usuarioId, id, elTipo, monto, dia, texto, categoriaId)
                        : RepositorioPagos.actualizar(usuarioId, id, elTipo, monto, dia, texto, categoriaId);
            }, ok -> {
                if (ok == null) {
                    ventana.cuentaNoVigente();
                } else if (ok) {
                    ventana.volverAConsulta("Registro #" + id + " actualizado", true);
                } else {
                    ventana.volverAConsulta("No se pudo modificar: este registro no te pertenece.", false);
                }
            }, this::errorGuardar);
        } else {
            Tarea.ejecutar(this, () -> {
                if (!ventana.verificarCuenta()) {
                    return Boolean.FALSE;
                }
                RepositorioPagos.insertar(usuarioId, elTipo, monto, dia, texto, categoriaId);
                return Boolean.TRUE;
            }, ok -> {
                if (ok) {
                    ventana.volverAConsulta("Registro guardado", true);
                } else {
                    ventana.cuentaNoVigente();
                }
            }, this::errorGuardar);
        }
    }

    /** ORA-20010 (procedimiento): la categoría no corresponde al tipo o al usuario. */
    private void errorGuardar(Exception e) {
        if (e instanceof SQLException s && s.getErrorCode() == 20010) {
            aviso.error("Categoría no válida para este registro");
        } else {
            aviso.error(Ui.mensajeError("guardar el registro").apply(e));
        }
    }
}
