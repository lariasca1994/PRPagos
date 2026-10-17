package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.prpagos.datos.Cuenta;
import com.prpagos.datos.RepositorioUsuarios;

/**
 * "Usuarios (admin)": misma pantalla y mismas reglas que admin_usuarios.html
 * y routers/usuarios.py de la web. Suspender, reactivar y eliminar cuentas;
 * la propia cuenta no se puede suspender ni eliminar.
 */
class PanelUsuarios extends JPanel {

    private static final String SUSPENDER = "Suspender";
    private static final String REACTIVAR = "Reactivar";
    private static final String ELIMINAR = "Eliminar";

    private final VentanaPrincipal ventana;
    private final Ui.Aviso aviso = new Ui.Aviso();
    private final DefaultTableModel modelo = new DefaultTableModel(
            new Object[] {"Nombre", "Usuario", "Rol", "Estado", "", ""}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private List<Cuenta> cuentas = new ArrayList<>();

    PanelUsuarios(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));
        add(Ui.titulo("Usuarios", 26f));
        add(Ui.espacio(18));
        add(aviso);
        add(Ui.espacio(4));
        add(tablaEnTarjeta());
        add(Ui.espacio(14));
        JTextArea nota = Ui.suave("Eliminar solo funciona si esa cuenta no tiene pagos ni auditoría asociados; "
                + "en ese caso, suspéndela en vez de eliminarla.");
        nota.setFont(nota.getFont().deriveFont(12f));
        add(nota);
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
                boolean accion = esAccion(v);
                setForeground(accion ? Tema.c().plataforma()
                        : "(tú)".equals(v) ? Tema.c().textoSuave() : Tema.c().texto());
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
                if (fila >= 0 && col >= 0 && fila < cuentas.size() && esAccion(modelo.getValueAt(fila, col))) {
                    accion((String) modelo.getValueAt(fila, col), cuentas.get(fila));
                }
            }
        });
        tabla.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int fila = tabla.rowAtPoint(e.getPoint());
                int col = tabla.columnAtPoint(e.getPoint());
                Object valor = fila >= 0 && col >= 0 ? modelo.getValueAt(fila, col) : null;
                tabla.setCursor(esAccion(valor) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
        JScrollPane scroll = new JScrollPane(tabla) {
            @Override
            public Dimension getPreferredSize() {
                int filas = Math.max(1, tabla.getRowCount());
                int alto = tabla.getTableHeader().getPreferredSize().height + filas * tabla.getRowHeight() + 4;
                return new Dimension(800, Math.min(alto, 520));
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

    private static boolean esAccion(Object valor) {
        return SUSPENDER.equals(valor) || REACTIVAR.equals(valor) || ELIMINAR.equals(valor);
    }

    void recargar() {
        aviso.ocultar();
        cargar();
    }

    private void cargar() {
        Tarea.ejecutar(this, RepositorioUsuarios::listar, this::mostrar,
                e -> aviso.error(Ui.mensajeError("listar los usuarios").apply(e)));
    }

    private void mostrar(List<Cuenta> lista) {
        cuentas = lista;
        modelo.setRowCount(0);
        long yo = ventana.sesion().usuarioId();
        for (Cuenta c : lista) {
            boolean propia = c.id() == yo;
            modelo.addRow(new Object[] {c.nombre(), c.usuario(), c.rol(), c.activo() ? "Activo" : "Suspendido",
                propia ? "(tú)" : (c.activo() ? SUSPENDER : REACTIVAR), propia ? "" : ELIMINAR});
        }
        if (lista.isEmpty()) {
            modelo.addRow(new Object[] {"No hay usuarios registrados.", "", "", "", "", ""});
        }
        revalidate();
    }

    private void accion(String accion, Cuenta cuenta) {
        long yo = ventana.sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            // Como exigir_admin: la cuenta del admin debe seguir activa y con rol admin.
            if (!ventana.verificarCuenta() || !ventana.sesion().admin()) {
                return null;
            }
            if (cuenta.id() == yo) {
                return new String[] {"error", SUSPENDER.equals(accion)
                        ? "No puedes suspender tu propia cuenta" : "No puedes eliminar tu propia cuenta"};
            }
            switch (accion) {
                case SUSPENDER -> {
                    RepositorioUsuarios.suspender(cuenta.id());
                    return new String[] {"ok", "Cuenta suspendida"};
                }
                case REACTIVAR -> {
                    RepositorioUsuarios.reactivar(cuenta.id());
                    return new String[] {"ok", "Cuenta reactivada"};
                }
                default -> {
                    String motivo = RepositorioUsuarios.eliminar(cuenta.id());
                    return motivo == null ? new String[] {"ok", "Cuenta eliminada"} : new String[] {"error", motivo};
                }
            }
        }, resultado -> {
            if (resultado == null) {
                ventana.ir(VentanaPrincipal.USUARIOS);  // revisa la cuenta: suspendida o sin rol admin
                return;
            }
            cargar();
            if ("ok".equals(resultado[0])) {
                aviso.exito(resultado[1]);
            } else {
                aviso.error(resultado[1]);
            }
        }, e -> aviso.error(Ui.mensajeError("actualizar la cuenta").apply(e)));
    }
}
