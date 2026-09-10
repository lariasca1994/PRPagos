package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import com.prpagos.conexion.ConexionBD;

public class VentanaConsultaPagos extends JFrame {
    private JTable tablaPagos;
    private JLabel lblTotalPagado;
    private JButton btnModificar;
    private JButton btnVolver;
    private final long usuarioId;

    // Filtros de búsqueda
    private JCheckBox chkFiltrarFecha;
    private SelectorFecha selectorDesde;
    private SelectorFecha selectorHasta;
    private JTextField txtConcepto;
    private JTextField txtMontoMin;
    private JTextField txtMontoMax;
    private JButton btnBuscar;
    private JButton btnLimpiar;
    private JButton btnExportarCsv;

    public VentanaConsultaPagos(long usuarioId) {
        super("Consulta de Pagos");
        this.usuarioId = usuarioId;
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        Iconos.aplicarIcono(this);

        tablaPagos = new JTable();
        JScrollPane scrollPane = new JScrollPane(tablaPagos);

        lblTotalPagado = new JLabel("Total Pagado: ");

        btnModificar = new JButton("Modificar Pago");
        btnModificar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                modificarPago();
            }
        });

        btnVolver = new JButton("Volver");
        btnVolver.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        JPanel panelFiltros = construirPanelFiltros();

        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(lblTotalPagado, BorderLayout.WEST);
        JPanel panelBotonesInferior = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotonesInferior.add(btnModificar);
        panelBotonesInferior.add(btnVolver);
        panelInferior.add(panelBotonesInferior, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(panelFiltros, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(panelInferior, BorderLayout.SOUTH);

        getContentPane().add(panel);

        cargarDatosDePagos(null, null, null, null, null);
    }

    private JPanel construirPanelFiltros() {
        JPanel panelFiltros = new JPanel();
        panelFiltros.setLayout(new BoxLayout(panelFiltros, BoxLayout.Y_AXIS));

        // Fila 1: concepto y rango de monto
        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fila1.add(new JLabel("Concepto contiene:"));
        txtConcepto = new JTextField(12);
        fila1.add(txtConcepto);
        fila1.add(new JLabel("Monto mín:"));
        txtMontoMin = new JTextField(6);
        fila1.add(txtMontoMin);
        fila1.add(new JLabel("Monto máx:"));
        txtMontoMax = new JTextField(6);
        fila1.add(txtMontoMax);

        // Fila 2: rango de fechas (opcional, con checkbox)
        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        chkFiltrarFecha = new JCheckBox("Filtrar por fecha");
        fila2.add(chkFiltrarFecha);
        fila2.add(new JLabel("Desde:"));
        selectorDesde = new SelectorFecha();
        fila2.add(selectorDesde);
        fila2.add(new JLabel("Hasta:"));
        selectorHasta = new SelectorFecha();
        fila2.add(selectorHasta);

        // Fila 3: acciones
        JPanel fila3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnBuscar = new JButton("Buscar");
        btnBuscar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buscarConFiltros();
            }
        });
        btnLimpiar = new JButton("Limpiar filtros");
        btnLimpiar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                limpiarFiltros();
            }
        });
        btnExportarCsv = new JButton("Exportar a CSV");
        btnExportarCsv.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportarCsv();
            }
        });
        fila3.add(btnBuscar);
        fila3.add(btnLimpiar);
        fila3.add(btnExportarCsv);

        panelFiltros.add(fila1);
        panelFiltros.add(fila2);
        panelFiltros.add(fila3);
        return panelFiltros;
    }

    private void buscarConFiltros() {
        java.sql.Date fechaDesde = null;
        java.sql.Date fechaHasta = null;
        if (chkFiltrarFecha.isSelected()) {
            fechaDesde = selectorDesde.getFechaSql();
            fechaHasta = selectorHasta.getFechaSql();
        }

        String concepto = txtConcepto.getText().trim();
        if (concepto.isEmpty()) {
            concepto = null;
        }

        Double montoMin = parsearMontoOpcional(txtMontoMin.getText());
        Double montoMax = parsearMontoOpcional(txtMontoMax.getText());

        cargarDatosDePagos(fechaDesde, fechaHasta, concepto, montoMin, montoMax);
    }

    private Double parsearMontoOpcional(String texto) {
        texto = texto.trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(texto);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "El monto '" + texto + "' no es un número válido — se ignora ese filtro.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private void limpiarFiltros() {
        chkFiltrarFecha.setSelected(false);
        txtConcepto.setText("");
        txtMontoMin.setText("");
        txtMontoMax.setText("");
        cargarDatosDePagos(null, null, null, null, null);
    }

    private void cargarDatosDePagos(java.sql.Date fechaDesde, java.sql.Date fechaHasta, String concepto, Double montoMin, Double montoMax) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            CallableStatement stmt = conn.prepareCall("{CALL buscar_tbpagos(?, ?, ?, ?, ?, ?, ?)}");
            stmt.setLong(1, usuarioId);
            setDateOrNull(stmt, 2, fechaDesde);
            setDateOrNull(stmt, 3, fechaHasta);
            if (concepto != null) {
                stmt.setString(4, concepto);
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            setDoubleOrNull(stmt, 5, montoMin);
            setDoubleOrNull(stmt, 6, montoMax);
            stmt.registerOutParameter(7, Types.REF_CURSOR);
            stmt.execute();

            ResultSet rs = (ResultSet) stmt.getObject(7);

            DefaultTableModel modelo = new DefaultTableModel();
            modelo.addColumn("ID Pago");
            modelo.addColumn("Monto");
            modelo.addColumn("Fecha");
            modelo.addColumn("Concepto");

            double totalPagado = 0.0;
            while (rs.next()) {
                Vector<Object> fila = new Vector<>();
                fila.add(rs.getInt("id_pago"));
                fila.add(rs.getDouble("monto"));
                fila.add(rs.getDate("fecha"));
                fila.add(rs.getString("concepto"));
                modelo.addRow(fila);
                totalPagado += rs.getDouble("monto");
            }

            tablaPagos.setModel(modelo);
            actualizarTotalPagado(totalPagado);

            rs.close();
            stmt.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error al consultar los pagos: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setDateOrNull(CallableStatement stmt, int index, java.sql.Date fecha) throws SQLException {
        if (fecha != null) {
            stmt.setDate(index, fecha);
        } else {
            stmt.setNull(index, Types.DATE);
        }
    }

    private void setDoubleOrNull(CallableStatement stmt, int index, Double valor) throws SQLException {
        if (valor != null) {
            stmt.setDouble(index, valor);
        } else {
            stmt.setNull(index, Types.NUMERIC);
        }
    }

    private void exportarCsv() {
        DefaultTableModel modelo = (DefaultTableModel) tablaPagos.getModel();
        if (modelo.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "No hay datos para exportar con los filtros actuales.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("pagos.csv"));
        int seleccion = fileChooser.showSaveDialog(this);
        if (seleccion != JFileChooser.APPROVE_OPTION) {
            return;
        }

        java.io.File archivo = fileChooser.getSelectedFile();
        try (FileWriter writer = new FileWriter(archivo)) {
            // Encabezados
            for (int col = 0; col < modelo.getColumnCount(); col++) {
                writer.append(modelo.getColumnName(col));
                if (col < modelo.getColumnCount() - 1) {
                    writer.append(",");
                }
            }
            writer.append("\n");

            // Filas
            for (int row = 0; row < modelo.getRowCount(); row++) {
                for (int col = 0; col < modelo.getColumnCount(); col++) {
                    Object valor = modelo.getValueAt(row, col);
                    String texto = valor != null ? valor.toString() : "";
                    // Si el valor tiene una coma, se envuelve en comillas
                    // para que siga siendo una sola celda al abrir el CSV.
                    if (texto.contains(",")) {
                        texto = "\"" + texto + "\"";
                    }
                    writer.append(texto);
                    if (col < modelo.getColumnCount() - 1) {
                        writer.append(",");
                    }
                }
                writer.append("\n");
            }

            JOptionPane.showMessageDialog(this, "Exportado correctamente a " + archivo.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error al exportar: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void modificarPago() {
        int selectedRow = tablaPagos.getSelectedRow();
        if (selectedRow != -1) {
            int idPago = (int) tablaPagos.getValueAt(selectedRow, 0);
            double montoActual = (double) tablaPagos.getValueAt(selectedRow, 1);
            java.sql.Date fechaActual = (java.sql.Date) tablaPagos.getValueAt(selectedRow, 2);
            String conceptoActual = (String) tablaPagos.getValueAt(selectedRow, 3);

            VentanaModificarPago ventanaModificarPago = new VentanaModificarPago(
                idPago, montoActual, fechaActual, conceptoActual, usuarioId, this);
            ventanaModificarPago.setVisible(true);
        }
    }

    public void actualizarListaDePagos() {
        cargarDatosDePagos(null, null, null, null, null);
    }

    public void actualizarTotalPagado(double total) {
        lblTotalPagado.setText("Total Pagado: " + total);
    }
}
