package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.datos.Pago;

/**
 * "Consultar registros": misma pantalla que registros_lista.html de la web
 * (filtros, totales, tabla con "Modificar" y exportar a CSV). Los filtros se
 * envían tal cual a la API: el servidor los interpreta, calcula los totales y
 * arma el CSV, igual que para la web. El admin ve los registros de todos.
 */
class PanelConsulta extends JPanel {

    private static final String MODIFICAR = "Modificar";
    private static final String ELIMINAR = "Eliminar";

    private final VentanaPrincipal ventana;
    private final javax.swing.JComboBox<String> tipoFiltro =
            new javax.swing.JComboBox<>(new String[] {"Todos", "Gastos", "Ingresos"});
    private final JTextField concepto = Ui.campo(null);
    private final JTextField montoMin = Ui.campo(null);
    private final JTextField montoMax = Ui.campo(null);
    private final SelectorFecha desde = SelectorFecha.opcional();
    private final SelectorFecha hasta = SelectorFecha.opcional();
    /** Categoría ("" = todas, "0" = sin categoría): las opciones llegan con cada consulta. */
    private final javax.swing.JComboBox<Opcion> categoriaFiltro = new javax.swing.JComboBox<>();
    private String categoriaElegida = "";
    private boolean llenandoCategorias;
    private final JLabel total = new JLabel();
    private final JLabel notaAdmin = new JLabel("Viendo los registros de todos los usuarios");
    private final Ui.Aviso aviso = new Ui.Aviso();
    private final DefaultTableModel modelo = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private List<Pago> pagos = new ArrayList<>();

    /** Opción de la lista de categorías (valor que va a la API / texto visible). */
    private record Opcion(String valor, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

    PanelConsulta(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        JPanel cabecera = new JPanel(new BorderLayout(16, 0));
        cabecera.setOpaque(false);
        cabecera.add(Ui.titulo("Consultar registros", 26f), BorderLayout.WEST);
        notaAdmin.setFont(notaAdmin.getFont().deriveFont(12.5f));
        Tema.alCambiar(() -> notaAdmin.setForeground(Tema.c().textoSuave()));
        JPanel centro = new JPanel(new java.awt.GridBagLayout());
        centro.setOpaque(false);
        centro.add(notaAdmin);
        cabecera.add(centro, BorderLayout.CENTER);
        cabecera.add(Ui.primario("Nuevo registro", e -> ventana.ir(VentanaPrincipal.NUEVO)), BorderLayout.EAST);
        cabecera.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        add(cabecera);
        add(Ui.espacio(18));
        add(aviso);
        add(Ui.espacio(4));
        add(filtros());
        add(Ui.espacio(18));

        total.setFont(total.getFont().deriveFont(15f));
        total.setAlignmentX(LEFT_ALIGNMENT);
        add(total);
        add(Ui.espacio(12));
        add(tablaEnTarjeta());
        add(Ui.espacio(16));
        add(Ui.secundario("Exportar a CSV", e -> exportarCsv()));
    }

    private JPanel filtros() {
        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));
        JPanel fila1 = Ui.adaptable(new JPanel(new GridLayout(1, 4, 14, 0)), 4, 140);
        fila1.setOpaque(false);
        tipoFiltro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        tipoFiltro.setAlignmentX(LEFT_ALIGNMENT);
        fila1.add(campoConEtiqueta("Tipo", tipoFiltro));
        fila1.add(campoConEtiqueta("Concepto contiene", concepto));
        fila1.add(campoConEtiqueta("Monto mínimo", montoMin));
        fila1.add(campoConEtiqueta("Monto máximo", montoMax));
        JPanel fila2 = Ui.adaptable(new JPanel(new GridLayout(1, 4, 14, 0)), 4, 140);
        fila2.setOpaque(false);
        fila2.add(campoConEtiqueta("Desde", desde));
        fila2.add(campoConEtiqueta("Hasta", hasta));
        categoriaFiltro.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        categoriaFiltro.setAlignmentX(LEFT_ALIGNMENT);
        categoriaFiltro.addActionListener(e -> {
            if (!llenandoCategorias && categoriaFiltro.getSelectedItem() instanceof Opcion o) {
                categoriaElegida = o.valor();
            }
        });
        fila2.add(campoConEtiqueta("Categoría", categoriaFiltro));
        JPanel botones = Ui.fila(10);
        botones.setBorder(BorderFactory.createEmptyBorder(31, 0, 0, 0));
        botones.add(Ui.primario("Buscar", e -> buscar()));
        botones.add(Ui.secundario("Limpiar", e -> limpiar()));
        fila2.add(botones);
        for (JPanel f : new JPanel[] {fila1, fila2}) {
            f.setAlignmentX(LEFT_ALIGNMENT);
            tarjeta.add(f);
        }
        tarjeta.setMaximumSize(new Dimension(Integer.MAX_VALUE, tarjeta.getPreferredSize().height + 20));
        for (JTextField t : new JTextField[] {concepto, montoMin, montoMax}) {
            t.addActionListener(e -> buscar());
        }
        return tarjeta;
    }

    private JPanel campoConEtiqueta(String texto, Component campo) {
        JPanel p = Ui.columna();
        JLabel l = Ui.etiqueta(texto);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12.5f));
        Tema.alCambiar(() -> l.setForeground(Tema.c().textoSuave()));
        p.add(l);
        p.add(campo);
        return p;
    }

    private JPanel tablaEnTarjeta() {
        tabla.setFillsViewportHeight(true);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getTableHeader().setFont(tabla.getTableHeader().getFont().deriveFont(Font.BOLD, 12.5f));
        tabla.setIntercellSpacing(new Dimension(0, 1));
        ((DefaultTableCellRenderer) tabla.getTableHeader().getDefaultRenderer())
                .setHorizontalAlignment(JLabel.LEFT);
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foco,
                                                           int fila, int col) {
                super.getTableCellRendererComponent(t, v, sel, false, fila, col);
                setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
                boolean accion = MODIFICAR.equals(v) || ELIMINAR.equals(v);
                setForeground(accion ? Tema.c().plataforma() : Tema.c().texto());
                if (accion) {
                    setText("<html><u>" + v + "</u></html>");
                }
                // Tipo con el color de la web: Ingreso en azul, Gasto en rojo (.etiqueta-tipo).
                if ("Ingreso".equals(v) || "Gasto".equals(v)) {
                    setForeground("Ingreso".equals(v) ? Tema.c().plataforma() : Tema.c().acento());
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                }
                return this;
            }
        });
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int fila = tabla.rowAtPoint(e.getPoint());
                int col = tabla.columnAtPoint(e.getPoint());
                if (fila < 0 || col < 0 || fila >= pagos.size()) {
                    return;
                }
                Object valor = modelo.getValueAt(fila, col);
                if (MODIFICAR.equals(valor)) {
                    ventana.editar(pagos.get(fila));
                } else if (ELIMINAR.equals(valor)) {
                    eliminar(pagos.get(fila));
                }
            }
        });
        tabla.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int fila = tabla.rowAtPoint(e.getPoint());
                int col = tabla.columnAtPoint(e.getPoint());
                Object valor = fila >= 0 && col >= 0 ? modelo.getValueAt(fila, col) : null;
                tabla.setCursor(MODIFICAR.equals(valor) || ELIMINAR.equals(valor)
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
        JScrollPane scroll = new JScrollPane(tabla) {
            // Alto según las filas (como la tabla de la web), con un tope y barra de desplazamiento.
            @Override
            public Dimension getPreferredSize() {
                int filas = Math.max(1, tabla.getRowCount());
                int alto = tabla.getTableHeader().getPreferredSize().height + filas * tabla.getRowHeight() + 4;
                return new Dimension(800, Math.min(alto, 460));
            }
        };
        scroll.setBorder(BorderFactory.createEmptyBorder());
        Ui.Redondeado marco = new Ui.Redondeado(12, true);
        marco.setLayout(new BorderLayout());
        marco.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        marco.add(scroll);
        marco.setAlignmentX(LEFT_ALIGNMENT);
        return marco;
    }

    void avisar(String mensaje, boolean exito) {
        if (exito) {
            aviso.exito(mensaje);
        } else {
            aviso.error(mensaje);
        }
    }

    void recargar() {
        aviso.ocultar();
        buscar();
    }

    private void limpiar() {
        tipoFiltro.setSelectedIndex(0);
        concepto.setText("");
        montoMin.setText("");
        montoMax.setText("");
        desde.setFechaSeleccionada(null);
        hasta.setFechaSeleccionada(null);
        categoriaElegida = "";
        aviso.ocultar();
        buscar();
    }

    /**
     * Deja puestos los filtros que llegan de otra pantalla (una categoría del
     * presupuesto o del dashboard), como los enlaces /registros?... de la web.
     */
    void prepararFiltros(JsonObject f) {
        String tipo = Json.texto(f, "tipo");
        tipoFiltro.setSelectedIndex("GASTO".equals(tipo) ? 1 : "INGRESO".equals(tipo) ? 2 : 0);
        concepto.setText("");
        montoMin.setText("");
        montoMax.setText("");
        String d = Json.texto(f, "fecha_desde");
        String h = Json.texto(f, "fecha_hasta");
        desde.setFechaSeleccionada(d.isEmpty() ? null : LocalDate.parse(d));
        hasta.setFechaSeleccionada(h.isEmpty() ? null : LocalDate.parse(h));
        categoriaElegida = Json.texto(f, "categoria");
    }

    /** Los filtros tal como los escribió la persona; el servidor los interpreta (igual que en la web). */
    private Map<String, String> valoresFiltros() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("tipo", switch (tipoFiltro.getSelectedIndex()) {
            case 1 -> "GASTO";
            case 2 -> "INGRESO";
            default -> "";
        });
        f.put("concepto", concepto.getText());
        f.put("monto_min", montoMin.getText());
        f.put("monto_max", montoMax.getText());
        LocalDate fDesde = desde.getFechaSeleccionada();
        LocalDate fHasta = hasta.getFechaSeleccionada();
        f.put("fecha_desde", fDesde == null ? "" : fDesde.toString());
        f.put("fecha_hasta", fHasta == null ? "" : fHasta.toString());
        f.put("categoria", categoriaElegida);
        return f;
    }

    private void buscar() {
        Map<String, String> f = valoresFiltros();
        Tarea.ejecutar(this, () -> Api.get("/api/registros", f), this::mostrar,
                e -> aviso.error(Ui.mensajeError("consultar los registros").apply(e)));
    }

    private void mostrar(JsonObject respuesta) {
        boolean admin = Json.logico(respuesta, "admin");
        JsonObject tipos = Json.objeto(respuesta, "tipos");
        List<Pago> resultado = new ArrayList<>();
        for (JsonObject r : Json.lista(respuesta, "registros")) {
            resultado.add(Pago.de(r, tipos));
        }
        pagos = resultado;
        notaAdmin.setVisible(admin);
        llenarCategorias(Json.lista(respuesta, "categorias"));
        modelo.setRowCount(0);
        modelo.setColumnIdentifiers(admin
                ? new Object[] {"ID", "Usuario", "Tipo", "Monto", "Fecha", "Categoría", "Concepto", "", ""}
                : new Object[] {"ID", "Tipo", "Monto", "Fecha", "Categoría", "Concepto", ""});
        for (Pago p : resultado) {
            String monto = String.format(Locale.ROOT, "%.2f", p.monto());
            if (admin) {
                modelo.addRow(new Object[] {p.idPago(), p.duenoNombre() + " (" + p.duenoCorreo() + ")", p.tipoTexto(),
                    monto, p.fecha().toString(), p.categoria(), p.concepto(), MODIFICAR, ELIMINAR});
            } else {
                modelo.addRow(new Object[] {p.idPago(), p.tipoTexto(), monto, p.fecha().toString(), p.categoria(),
                    p.concepto(), MODIFICAR});
            }
        }
        double ingresos = Json.numero(respuesta, "ingresos");
        double gastos = Json.numero(respuesta, "gastos");
        double balance = Json.numero(respuesta, "balance");
        if (resultado.isEmpty()) {
            Object[] vacia = new Object[modelo.getColumnCount()];
            vacia[0] = "No hay registros para los filtros seleccionados.";
            modelo.addRow(vacia);
        }
        tabla.getColumnModel().getColumn(0).setPreferredWidth(resultado.isEmpty() ? 600 : 60);
        tabla.getColumnModel().getColumn(modelo.getColumnCount() - 1).setPreferredWidth(90);
        Tema.Paleta pal = Tema.c();
        total.setText("<html>Ingresos: <b style='color:" + hex(pal.plataforma()) + "'>"
                + String.format(Locale.ROOT, "%.2f", ingresos) + "</b> &middot; Gastos: <b style='color:"
                + hex(pal.acento()) + "'>" + String.format(Locale.ROOT, "%.2f", gastos)
                + "</b> &middot; Balance: <b>" + String.format(Locale.ROOT, "%.2f", balance) + "</b></html>");
        revalidate();
    }

    private void llenarCategorias(List<JsonObject> categorias) {
        llenandoCategorias = true;
        javax.swing.DefaultComboBoxModel<Opcion> m = new javax.swing.DefaultComboBoxModel<>();
        m.addElement(new Opcion("", "Todas"));
        m.addElement(new Opcion("0", "Sin categoría"));
        for (JsonObject c : categorias) {
            m.addElement(new Opcion(Json.texto(c, "id"), Json.texto(c, "nombre") + " · "
                    + ("INGRESO".equals(Json.texto(c, "tipo")) ? "Ingreso" : "Gasto")));
        }
        categoriaFiltro.setModel(m);
        for (int i = 0; i < m.getSize(); i++) {
            if (m.getElementAt(i).valor().equals(categoriaElegida)) {
                categoriaFiltro.setSelectedIndex(i);
            }
        }
        llenandoCategorias = false;
    }

    private void eliminar(Pago pago) {
        Tarea.ejecutar(this, () -> Api.delete("/api/registros/" + pago.idPago()), r -> {
            recargar();
            aviso.exito(Json.texto(r, "mensaje"));
        }, e -> aviso.error(Ui.mensajeError("eliminar el registro").apply(e)));
    }

    /** El mismo CSV que /registros/exportar.csv de la web: lo arma el servidor. */
    private void exportarCsv() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("registros.csv"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        Map<String, String> f = valoresFiltros();
        Tarea.ejecutar(this, () -> {
            Files.writeString(archivo.toPath(), Api.texto("/api/registros/exportar.csv", f), StandardCharsets.UTF_8);
            return archivo.getName();
        }, nombre -> aviso.exito("Exportado: " + nombre), e -> aviso.error(e instanceof IOException
                ? "No se pudo exportar: " + e.getMessage() : Ui.mensajeError("exportar").apply(e)));
    }

    private static String hex(java.awt.Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

}
