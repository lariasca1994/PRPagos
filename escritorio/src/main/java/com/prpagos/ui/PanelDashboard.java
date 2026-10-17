package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Paint;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import com.prpagos.datos.RepositorioFinanzas;
import com.prpagos.finanzas.Correo;
import com.prpagos.finanzas.Finanzas;
import com.prpagos.finanzas.Resumenes;

/**
 * Dashboard: misma composición, datos y colores que dashboard.html de la web
 * (allá con Chart.js, aquí con JFreeChart). Tarjetas del mes, ingresos vs
 * gastos de 6 meses, gastos por categoría, plan 50/30/20 y resúmenes por correo.
 */
class PanelDashboard extends JPanel {

    /** Opción del selector de mes. */
    private record OpcionMes(LocalDate mes) {
        @Override
        public String toString() {
            return Finanzas.nombreMes(mes);
        }
    }

    /** Lo que se dibuja: se conserva para redibujar al cambiar de tema. */
    private record Datos(LocalDate mes, RepositorioFinanzas.Totales totales, List<Finanzas.Mes> serie,
                         List<Finanzas.Categoria> categorias, Map<String, Double> pcts, List<Finanzas.FilaPlan> plan) {
    }

    private final VentanaPrincipal ventana;
    private final JComboBox<OpcionMes> selectorMes = new JComboBox<>();
    private final Ui.Aviso aviso = new Ui.Aviso();
    private final JLabel ingresos = cifra();
    private final JLabel gastos = cifra();
    private final JLabel balance = cifra();
    private final JLabel ahorro = cifra();
    private final JLabel movimientos = new JLabel();
    private final JPanel graficaMeses = contenedorGrafica(300);
    private final JPanel graficaCategorias = contenedorGrafica(280);
    private final JPanel graficaPlan = contenedorGrafica(280);
    private final JLabel tituloCategorias = Ui.titulo("", 16f);
    private final JLabel tituloPlanGrafica = Ui.titulo("", 16f);
    private final JLabel tituloPlanTabla = Ui.titulo("", 16f);
    private final JPanel tablaPlan = new JPanel(new GridLayout(0, 5, 0, 0));
    private final JButton semanal = Ui.secundario("Enviarme el resumen semanal", e -> enviarResumen("semanal"));
    private final JButton mensual = Ui.secundario("Enviarme el resumen del mes", e -> enviarResumen("mensual"));
    private Datos ultimo;
    private boolean cargandoMeses;

    PanelDashboard(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Ui.titulo("Dashboard", 26f), BorderLayout.WEST);
        selectorMes.setPreferredSize(new Dimension(200, 36));
        selectorMes.addActionListener(e -> {
            if (!cargandoMeses) {
                recargar();
            }
        });
        JPanel derecha = Ui.fila(0);
        derecha.add(selectorMes);
        cabecera.add(derecha, BorderLayout.EAST);
        cabecera.setAlignmentX(LEFT_ALIGNMENT);
        cabecera.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        add(cabecera);
        add(Ui.espacio(14));
        add(aviso);
        add(Ui.espacio(4));

        JPanel tarjetas = new JPanel(new GridLayout(1, 4, 14, 0));
        tarjetas.setOpaque(false);
        tarjetas.setAlignmentX(LEFT_ALIGNMENT);
        tarjetas.add(tarjetaCifra("Ingresos", ingresos, null));
        tarjetas.add(tarjetaCifra("Gastos", gastos, null));
        tarjetas.add(tarjetaCifra("Balance", balance, null));
        tarjetas.add(tarjetaCifra("Ahorro del mes", ahorro, movimientos));
        tarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        add(tarjetas);
        add(Ui.espacio(14));

        add(tarjetaGrafica(Ui.titulo("Ingresos vs. gastos · últimos 6 meses", 16f), graficaMeses));
        add(Ui.espacio(14));
        JPanel fila = new JPanel(new GridLayout(1, 2, 14, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.add(tarjetaGrafica(tituloCategorias, graficaCategorias));
        fila.add(tarjetaGrafica(tituloPlanGrafica, graficaPlan));
        add(fila);
        add(Ui.espacio(14));

        JPanel plan = Ui.tarjeta();
        plan.add(tituloPlanTabla);
        plan.add(Ui.espacio(6));
        JTextArea ayudaPlan = Ui.suave("Presupuesto calculado sobre los ingresos del mes. Los porcentajes se cambian en Perfil.");
        ayudaPlan.setFont(ayudaPlan.getFont().deriveFont(12.5f));
        plan.add(ayudaPlan);
        plan.add(Ui.espacio(8));
        tablaPlan.setOpaque(false);
        tablaPlan.setAlignmentX(LEFT_ALIGNMENT);
        plan.add(tablaPlan);
        add(plan);
        add(Ui.espacio(14));

        JPanel correos = Ui.tarjeta();
        correos.add(Ui.titulo("Resúmenes por correo", 16f));
        correos.add(Ui.espacio(6));
        JTextArea ayuda = Ui.suave("Cada viernes recibes el resumen de la semana y cada día 29 (en febrero, el último "
                + "día) el resumen general del mes. Puedes pedirlos ahora:");
        ayuda.setFont(ayuda.getFont().deriveFont(12.5f));
        correos.add(ayuda);
        correos.add(Ui.espacio(10));
        JPanel botones = Ui.fila(12);
        botones.add(semanal);
        botones.add(mensual);
        correos.add(botones);
        add(correos);

        Tema.alCambiar(() -> {
            if (ultimo != null) {
                dibujar(ultimo);
            }
        });
    }

    // ───────────────────────── Piezas ─────────────────────────

    private static JLabel cifra() {
        JLabel l = new JLabel(" ");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        return l;
    }

    private static JPanel tarjetaCifra(String titulo, JLabel valor, JLabel pie) {
        JPanel t = Ui.tarjeta();
        t.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        JLabel l = new JLabel(titulo);
        l.setFont(l.getFont().deriveFont(12.5f));
        Tema.alCambiar(() -> l.setForeground(Tema.c().textoSuave()));
        t.add(l);
        t.add(Ui.espacio(4));
        t.add(valor);
        if (pie != null) {
            pie.setFont(pie.getFont().deriveFont(11.5f));
            Tema.alCambiar(() -> pie.setForeground(Tema.c().textoSuave()));
            t.add(Ui.espacio(2));
            t.add(pie);
        }
        return t;
    }

    private static JPanel contenedorGrafica(int alto) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setPreferredSize(new Dimension(400, alto));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
        return p;
    }

    private static JPanel tarjetaGrafica(JLabel titulo, JPanel grafica) {
        JPanel t = Ui.tarjeta();
        t.add(titulo);
        t.add(Ui.espacio(8));
        t.add(grafica);
        return t;
    }

    // ───────────────────────── Datos ─────────────────────────

    void recargar() {
        aviso.ocultar();
        boolean correoListo = Correo.disponible();
        semanal.setEnabled(correoListo);
        mensual.setEnabled(correoListo);
        if (selectorMes.getItemCount() == 0) {
            cargandoMeses = true;
            DefaultComboBoxModel<OpcionMes> modelo = new DefaultComboBoxModel<>();
            LocalDate hoy = Finanzas.hoy().withDayOfMonth(1);
            for (int i = 0; i < 12; i++) {
                modelo.addElement(new OpcionMes(Finanzas.mesesAtras(hoy, i)));
            }
            selectorMes.setModel(modelo);
            cargandoMeses = false;
        }
        LocalDate mes = ((OpcionMes) selectorMes.getSelectedItem()).mes();
        long usuarioId = ventana.sesion().usuarioId();
        Tarea.ejecutar(this, () -> {
            LocalDate siguiente = Finanzas.mesesAtras(mes, -1);
            RepositorioFinanzas.Totales t = RepositorioFinanzas.totales(usuarioId, mes, siguiente);
            List<Finanzas.Mes> serie = Finanzas.completarMeses(mes,
                    RepositorioFinanzas.serieMeses(usuarioId, Finanzas.mesesAtras(mes, 5), siguiente), 6);
            List<Finanzas.Categoria> cats = RepositorioFinanzas.gastosPorCategoria(usuarioId, mes, siguiente);
            Map<String, Double> pcts = RepositorioFinanzas.plan(usuarioId);
            List<Finanzas.FilaPlan> plan = Finanzas.calcularPlan(t.ingresos(), pcts,
                    RepositorioFinanzas.gastosPorGrupo(usuarioId, mes, siguiente));
            return new Datos(mes, t, serie, cats, pcts, plan);
        }, this::dibujar, e -> aviso.error(Ui.mensajeError("cargar el dashboard").apply(e)));
    }

    private void dibujar(Datos d) {
        ultimo = d;
        Tema.Paleta p = Tema.c();
        double bal = d.totales().ingresos() - d.totales().gastos();
        ingresos.setText(Finanzas.moneda(d.totales().ingresos()));
        ingresos.setForeground(p.plataforma());
        gastos.setText(Finanzas.moneda(d.totales().gastos()));
        gastos.setForeground(p.acento());
        balance.setText(Finanzas.moneda(bal));
        balance.setForeground(bal >= 0 ? p.exitoTexto() : p.acento());
        ahorro.setText(Finanzas.porcentaje(Math.max(bal, 0), d.totales().ingresos()));
        movimientos.setText(d.totales().movimientos() + " movimientos");

        String nombreMes = Finanzas.nombreMes(d.mes());
        String planTexto = Finanzas.textoPct(d.pcts().get("NECESIDADES")) + "/" + Finanzas.textoPct(d.pcts().get("DESEOS"))
                + "/" + Finanzas.textoPct(d.pcts().get("AHORRO"));
        tituloCategorias.setText("Gastos por categoría · " + nombreMes);
        tituloPlanGrafica.setText("Plan " + planTexto + " · " + nombreMes);
        tituloPlanTabla.setText("Plan " + planTexto);

        mostrar(graficaMeses, graficaMeses(d));
        if (d.categorias().isEmpty()) {
            graficaCategorias.removeAll();
            JLabel vacio = new JLabel("No hay gastos en este mes.", JLabel.CENTER);
            vacio.setForeground(p.textoSuave());
            graficaCategorias.add(vacio);
            graficaCategorias.revalidate();
            graficaCategorias.repaint();
        } else {
            mostrar(graficaCategorias, graficaCategorias(d));
        }
        mostrar(graficaPlan, graficaPlan(d));
        llenarTablaPlan(d);
    }

    private static void mostrar(JPanel contenedor, JFreeChart grafica) {
        contenedor.removeAll();
        ChartPanel panel = new ChartPanel(grafica, false, false, false, false, true);
        panel.setOpaque(false);
        panel.setMouseWheelEnabled(false);
        panel.setPopupMenu(null);
        panel.setMinimumDrawWidth(200);
        panel.setMinimumDrawHeight(150);
        panel.setMaximumDrawWidth(4000);
        panel.setMaximumDrawHeight(3000);
        contenedor.add(panel);
        contenedor.revalidate();
        contenedor.repaint();
    }

    // ───────────────────────── Gráficas ─────────────────────────

    private static Color color(String hex) {
        return Color.decode(hex);
    }

    /** "$ 1.500.000" en los ejes, como la web. */
    private static final NumberFormat FORMATO_EJE = new NumberFormat() {
        @Override
        public StringBuffer format(double numero, StringBuffer destino, FieldPosition pos) {
            return destino.append(Finanzas.moneda(numero).replace(",00", ""));
        }

        @Override
        public StringBuffer format(long numero, StringBuffer destino, FieldPosition pos) {
            return format((double) numero, destino, pos);
        }

        @Override
        public Number parse(String fuente, ParsePosition pos) {
            return null;
        }
    };

    private static void estilo(JFreeChart g) {
        Tema.Paleta p = Tema.c();
        Font fuente = new JLabel().getFont().deriveFont(11.5f);
        g.setBackgroundPaint(p.superficie());
        g.setAntiAlias(true);
        g.setTextAntiAlias(true);
        g.setBorderVisible(false);
        LegendTitle leyenda = g.getLegend();
        if (leyenda != null) {
            leyenda.setBackgroundPaint(p.superficie());
            leyenda.setItemPaint(p.textoSuave());
            leyenda.setItemFont(fuente);
            leyenda.setFrame(org.jfree.chart.block.BlockBorder.NONE);
        }
        if (g.getPlot() instanceof CategoryPlot plot) {
            plot.setBackgroundPaint(p.superficie());
            plot.setOutlineVisible(false);
            plot.setRangeGridlinePaint(p.borde());
            plot.setRangeGridlineStroke(new BasicStroke(1f));
            plot.setDomainGridlinesVisible(false);
            CategoryAxis ejeX = plot.getDomainAxis();
            ejeX.setTickLabelPaint(p.textoSuave());
            ejeX.setTickLabelFont(fuente);
            ejeX.setAxisLineVisible(false);
            ejeX.setTickMarksVisible(false);
            NumberAxis ejeY = (NumberAxis) plot.getRangeAxis();
            ejeY.setTickLabelPaint(p.textoSuave());
            ejeY.setTickLabelFont(fuente);
            ejeY.setAxisLineVisible(false);
            ejeY.setTickMarksVisible(false);
            ejeY.setNumberFormatOverride(FORMATO_EJE);
            BarRenderer r = (BarRenderer) plot.getRenderer();
            r.setBarPainter(new StandardBarPainter());
            r.setShadowVisible(false);
            r.setItemMargin(0.08);
            r.setMaximumBarWidth(0.12);
        }
    }

    private static JFreeChart graficaMeses(Datos d) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        for (Finanzas.Mes m : d.serie()) {
            String etiqueta = Finanzas.etiquetaMesCorta(m.mes());
            datos.addValue(m.ingresos(), "Ingresos", etiqueta);
            datos.addValue(m.gastos(), "Gastos", etiqueta);
        }
        JFreeChart g = ChartFactory.createBarChart(null, null, null, datos, PlotOrientation.VERTICAL, true, true, false);
        estilo(g);
        g.getLegend().setPosition(RectangleEdge.TOP);
        BarRenderer r = (BarRenderer) g.getCategoryPlot().getRenderer();
        r.setSeriesPaint(0, color(Finanzas.COLOR_INGRESOS));
        r.setSeriesPaint(1, color(Finanzas.COLOR_GASTOS));
        r.setMaximumBarWidth(0.06);
        return g;
    }

    private static JFreeChart graficaCategorias(Datos d) {
        DefaultPieDataset<String> datos = new DefaultPieDataset<>();
        for (Finanzas.Categoria c : d.categorias()) {
            datos.setValue(c.nombre(), c.total());
        }
        JFreeChart g = ChartFactory.createRingChart(null, datos, true, true, false);
        estilo(g);
        g.getLegend().setPosition(RectangleEdge.RIGHT);
        RingPlot plot = (RingPlot) g.getPlot();
        plot.setBackgroundPaint(Tema.c().superficie());
        plot.setOutlineVisible(false);
        plot.setSectionDepth(0.40);
        plot.setSeparatorsVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelGenerator(null);
        plot.setDefaultSectionOutlinePaint(Tema.c().superficie());
        plot.setDefaultSectionOutlineStroke(new BasicStroke(2f));
        for (int i = 0; i < d.categorias().size(); i++) {
            plot.setSectionPaint(d.categorias().get(i).nombre(),
                    color(Finanzas.PALETA_CATEGORIAS.get(i % Finanzas.PALETA_CATEGORIAS.size())));
        }
        return g;
    }

    private static JFreeChart graficaPlan(Datos d) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        List<String> colores = new ArrayList<>();
        for (Finanzas.FilaPlan f : d.plan()) {
            String grupo = Finanzas.NOMBRE_GRUPO.get(f.grupo());
            datos.addValue(f.presupuesto(), "Presupuesto", grupo);
            datos.addValue(f.real(), "Real", grupo);
            colores.add(Finanzas.COLOR_GRUPO.get(f.grupo()));
        }
        Color presupuesto = Tema.esOscuro() ? color("#3A4157") : color("#C9CED8");
        JFreeChart g = ChartFactory.createBarChart(null, null, null, datos, PlotOrientation.HORIZONTAL, true, true, false);
        estilo(g);
        g.getLegend().setPosition(RectangleEdge.TOP);
        CategoryPlot plot = g.getCategoryPlot();
        BarRenderer r = new BarRenderer() {
            @Override
            public Paint getItemPaint(int fila, int columna) {
                // "Real" con el color de su grupo; "Presupuesto" en gris (como la web).
                return fila == 1 ? color(colores.get(columna)) : presupuesto;
            }
        };
        r.setBarPainter(new StandardBarPainter());
        r.setShadowVisible(false);
        r.setItemMargin(0.08);
        r.setSeriesPaint(0, presupuesto);
        r.setSeriesPaint(1, color(Finanzas.COLOR_INGRESOS));
        plot.setRenderer(r);
        return g;
    }

    // ───────────────────────── Tabla del plan ─────────────────────────

    private void llenarTablaPlan(Datos d) {
        tablaPlan.removeAll();
        Tema.Paleta p = Tema.c();
        for (String encabezado : new String[] {"Grupo", "Presupuesto", "Real", "Usado", "Estado"}) {
            JLabel l = celda(encabezado, p.textoSuave(), Font.BOLD);
            tablaPlan.add(l);
        }
        for (Finanzas.FilaPlan f : d.plan()) {
            JLabel grupo = celda(Finanzas.NOMBRE_GRUPO.get(f.grupo()) + " (" + Finanzas.textoPct(f.pct()) + "%)",
                    p.texto(), Font.PLAIN);
            grupo.setIcon(new Punto(color(Finanzas.COLOR_GRUPO.get(f.grupo()))));
            tablaPlan.add(grupo);
            tablaPlan.add(celda(Finanzas.moneda(f.presupuesto()), p.texto(), Font.PLAIN));
            tablaPlan.add(celda(Finanzas.moneda(f.real()), p.texto(), Font.PLAIN));
            tablaPlan.add(celda(Math.round(f.usado()) + " %", p.texto(), Font.PLAIN));
            tablaPlan.add(celda(f.estado(), colorEstado(f.color()), Font.BOLD));
        }
        tablaPlan.revalidate();
        tablaPlan.repaint();
    }

    /** Mismo tono que la web: el del correo en claro, más luminoso en oscuro (.estado-bien/mal/alerta). */
    private static Color colorEstado(String color) {
        boolean oscuro = Tema.esOscuro();
        if (Finanzas.COLOR_BIEN.equals(color)) {
            return oscuro ? color("#75e0a7") : color("#067647");
        }
        if (Finanzas.COLOR_MAL.equals(color)) {
            return oscuro ? color("#fda29b") : color("#a8221a");
        }
        return oscuro ? color("#f7b27a") : color("#b54708");
    }

    private static JLabel celda(String texto, Color color, int estilo) {
        JLabel l = new JLabel(texto);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(estilo, 13f));
        l.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        return l;
    }

    /** El punto de color del grupo (.punto en la web). */
    private record Punto(Color color) implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + 2, 10, 10);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 14;
        }
    }

    // ───────────────────────── Resumen por correo ─────────────────────────

    /** "Enviarme el resumen ahora": el semanal (últimos 7 días) o el del mes en curso. */
    private void enviarResumen(String tipo) {
        LocalDate dia = Finanzas.hoy();
        Finanzas.Periodo periodo = "semanal".equals(tipo) ? Finanzas.periodoSemanal(dia) : Finanzas.periodoMensual(dia);
        var s = ventana.sesion();
        Tarea.ejecutar(this, () -> {
            try {
                Resumenes.enviar(s.usuarioId(), s.correo(), s.nombre(), periodo);
                return null;
            } catch (Correo.ErrorCorreo e) {
                return e.getMessage();
            }
        }, error -> {
            if (error != null) {
                aviso.error(error);
            } else {
                aviso.exito("Te enviamos el resumen a " + s.correo());
            }
        }, e -> aviso.error(Ui.mensajeError("enviar el resumen").apply(e)));
    }
}
