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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.prpagos.datos.Pago;
import com.prpagos.datos.RepositorioPagos;
import com.prpagos.datos.Sesion;

/**
 * "Consultar registros": misma pantalla que registros_lista.html de la web
 * (filtros, total, tabla con "Modificar" y exportar a CSV). Para el admin,
 * igual que en la web: ve los pagos de todos con su dueño, puede eliminar y
 * el CSV lleva las columnas Usuario y Nombre.
 */
class PanelConsulta extends JPanel {

    private static final String MODIFICAR = "Modificar";
    private static final String ELIMINAR = "Eliminar";

    private final VentanaPrincipal ventana;
    private final JTextField concepto = Ui.campo(null);
    private final JTextField montoMin = Ui.campo(null);
    private final JTextField montoMax = Ui.campo(null);
    private final SelectorFecha desde = SelectorFecha.opcional();
    private final SelectorFecha hasta = SelectorFecha.opcional();
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
    private boolean comoAdmin;

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

    private Sesion sesion() {
        return ventana.sesion();
    }

    private JPanel filtros() {
        JPanel tarjeta = Ui.tarjeta();
        tarjeta.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));
        JPanel fila1 = new JPanel(new GridLayout(1, 3, 14, 0));
        fila1.setOpaque(false);
        fila1.add(campoConEtiqueta("Concepto contiene", concepto));
        fila1.add(campoConEtiqueta("Monto mínimo", montoMin));
        fila1.add(campoConEtiqueta("Monto máximo", montoMax));
        JPanel fila2 = new JPanel(new GridLayout(1, 3, 14, 0));
        fila2.setOpaque(false);
        fila2.add(campoConEtiqueta("Desde", desde));
        fila2.add(campoConEtiqueta("Hasta", hasta));
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
        concepto.setText("");
        montoMin.setText("");
        montoMax.setText("");
        desde.setFechaSeleccionada(null);
        hasta.setFechaSeleccionada(null);
        aviso.ocultar();
        buscar();
    }

    private void buscar() {
        String textoConcepto = concepto.getText().trim();
        // Igual que la web: campo vacío = sin filtro, monto inválido se ignora.
        Double min = monto(montoMin.getText());
        Double max = monto(montoMax.getText());
        LocalDate fDesde = desde.getFechaSeleccionada();
        LocalDate fHasta = hasta.getFechaSeleccionada();
        String filtroConcepto = textoConcepto.isEmpty() ? null : textoConcepto;
        boolean admin = sesion().admin();
        long usuarioId = sesion().usuarioId();
        Tarea.ejecutar(this, () -> admin
                ? RepositorioPagos.buscarTodos(fDesde, fHasta, filtroConcepto, min, max)
                : RepositorioPagos.buscar(usuarioId, fDesde, fHasta, filtroConcepto, min, max),
                resultado -> mostrar(resultado, admin),
                e -> aviso.error(Ui.mensajeError("consultar los registros").apply(e)));
    }

    private static Double monto(String texto) {
        try {
            return texto.trim().isEmpty() ? null : Double.parseDouble(texto.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void mostrar(List<Pago> resultado, boolean admin) {
        pagos = resultado;
        comoAdmin = admin;
        notaAdmin.setVisible(admin);
        modelo.setRowCount(0);
        modelo.setColumnIdentifiers(admin
                ? new Object[] {"ID", "Usuario", "Monto", "Fecha", "Concepto", "", ""}
                : new Object[] {"ID", "Monto", "Fecha", "Concepto", ""});
        double suma = 0;
        for (Pago p : resultado) {
            String monto = String.format(Locale.ROOT, "%.2f", p.monto());
            if (admin) {
                modelo.addRow(new Object[] {p.idPago(), p.duenoNombre() + " (" + p.duenoCorreo() + ")", monto,
                    p.fecha().toString(), p.concepto(), MODIFICAR, ELIMINAR});
            } else {
                modelo.addRow(new Object[] {p.idPago(), monto, p.fecha().toString(), p.concepto(), MODIFICAR});
            }
            suma += p.monto();
        }
        if (resultado.isEmpty()) {
            Object[] vacia = new Object[modelo.getColumnCount()];
            vacia[0] = "No hay registros para los filtros seleccionados.";
            modelo.addRow(vacia);
        }
        tabla.getColumnModel().getColumn(0).setPreferredWidth(resultado.isEmpty() ? 600 : 60);
        tabla.getColumnModel().getColumn(modelo.getColumnCount() - 1).setPreferredWidth(90);
        total.setText("<html>Total: <b>" + String.format(Locale.ROOT, "%.2f", suma) + "</b></html>");
        revalidate();
    }

    private void eliminar(Pago pago) {
        long adminId = sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            // Como exigir_admin en la web: la cuenta debe seguir activa y con rol admin.
            if (!ventana.verificarCuenta() || !ventana.sesion().admin()) {
                return Boolean.FALSE;
            }
            RepositorioPagos.eliminar(adminId, pago.idPago());
            return Boolean.TRUE;
        }, ok -> {
            if (!ok) {
                ventana.ir(VentanaPrincipal.CONSULTA);  // revisa la cuenta: suspendida o sin rol admin
                return;
            }
            recargar();
            aviso.exito("Registro #" + pago.idPago() + " eliminado");
        }, e -> aviso.error(Ui.mensajeError("eliminar el registro").apply(e)));
    }

    private void exportarCsv() {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File("registros.csv"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        // Mismas columnas que /registros/exportar.csv de la web.
        try (PrintWriter w = new PrintWriter(archivo, StandardCharsets.UTF_8)) {
            w.println(comoAdmin ? "ID,Usuario,Nombre,Monto,Fecha,Concepto" : "ID,Monto,Fecha,Concepto");
            for (Pago p : pagos) {
                String fijo = p.monto() + "," + p.fecha() + "," + csv(p.concepto());
                w.println(comoAdmin
                        ? p.idPago() + "," + csv(p.duenoCorreo()) + "," + csv(p.duenoNombre()) + "," + fijo
                        : p.idPago() + "," + fijo);
            }
        } catch (IOException e) {
            aviso.error("No se pudo exportar: " + e.getMessage());
        }
    }

    private static String csv(String texto) {
        if (texto == null) {
            return "";
        }
        if (texto.contains(",") || texto.contains("\"") || texto.contains("\n")) {
            return "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
