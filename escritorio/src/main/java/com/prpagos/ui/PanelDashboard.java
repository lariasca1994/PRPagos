package com.prpagos.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Paint;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;

/**
 * Dashboard: misma composición y colores que dashboard.html de la web (allá
 * con Chart.js, aquí con JFreeChart). Las cifras, las series, el plan y sus
 * textos los calcula el servidor (servicios/dashboard.py) y son exactamente
 * los mismos que ve la web; aquí solo se dibujan.
 */
class PanelDashboard extends JPanel {

    /** Opción del selector de mes ("2026-09" / "Septiembre 2026"). */
    private record OpcionMes(String valor, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

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
    /** La última vista recibida: se conserva para redibujar al cambiar de tema. */
    private JsonObject ultima;
    private boolean cargandoMeses;
    private final VentanaPrincipal ventana;
    private final JTextArea ayudaCategorias = ayuda("Toca una categoría para ver sus registros.");

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

        JPanel meses = tarjetaGrafica(Ui.titulo("Ingresos vs. gastos · últimos 6 meses", 16f), graficaMeses);
        meses.add(ayuda("Toca un mes para verlo."), 2);
        add(meses);
        add(Ui.espacio(14));
        JPanel fila = new JPanel(new GridLayout(1, 2, 14, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        JPanel categorias = tarjetaGrafica(tituloCategorias, graficaCategorias);
        categorias.add(ayudaCategorias, 2);
        fila.add(categorias);
        fila.add(tarjetaGrafica(tituloPlanGrafica, graficaPlan));
        add(fila);
        add(Ui.espacio(14));

        JPanel plan = Ui.tarjeta();
        plan.add(tituloPlanTabla);
        plan.add(Ui.espacio(6));
        JTextArea ayudaPlan = Ui.suave("Presupuesto calculado sobre los ingresos del mes. Los porcentajes se cambian en Perfil.");
        ayudaPlan.setFont(ayudaPlan.getFont().deriveFont(12.5f));
        plan.add(ayudaPlan);
        plan.add(Ui.espacio(6));
        plan.add(Ui.enlace("Ver el presupuesto detallado por categoría", e -> {
            if (ultima != null) {
                JsonObject p = Json.objeto(ultima, "enlace_presupuesto");
                ventana.verPresupuesto((int) Json.entero(p, "anio"), (int) Json.entero(p, "mes"));
            }
        }));
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
            if (ultima != null) {
                dibujar(ultima);
            }
        });
    }

    // ───────────────────────── Piezas ─────────────────────────

    static JLabel cifra() {
        JLabel l = new JLabel(" ");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        return l;
    }

    static JPanel tarjetaCifra(String titulo, JLabel valor, JLabel pie) {
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

    static JPanel contenedorGrafica(int alto) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setPreferredSize(new Dimension(400, alto));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, alto));
        return p;
    }

    static JPanel tarjetaGrafica(JLabel titulo, JPanel grafica) {
        JPanel t = Ui.tarjeta();
        t.add(titulo);
        t.add(Ui.espacio(8));
        t.add(grafica);
        return t;
    }

    // ───────────────────────── Datos ─────────────────────────

    void recargar() {
        aviso.ocultar();
        OpcionMes elegido = (OpcionMes) selectorMes.getSelectedItem();
        String mes = elegido == null ? "" : elegido.valor();
        Tarea.ejecutar(this, () -> Api.get("/api/dashboard", Map.of("mes", mes)), this::dibujar,
                e -> aviso.error(Ui.mensajeError("cargar el dashboard").apply(e)));
    }

    /** Clic en un mes de las barras: ese mes en el selector (como ?mes= en la web). */
    private void elegirMes(String valor) {
        for (int i = 0; i < selectorMes.getItemCount(); i++) {
            if (selectorMes.getItemAt(i).valor().equals(valor)) {
                selectorMes.setSelectedIndex(i);
                return;
            }
        }
    }

    private void dibujar(JsonObject v) {
        ultima = v;
        if (selectorMes.getItemCount() == 0) {
            cargandoMeses = true;
            DefaultComboBoxModel<OpcionMes> modelo = new DefaultComboBoxModel<>();
            for (JsonObject m : Json.lista(v, "meses")) {
                OpcionMes opcion = new OpcionMes(Json.texto(m, "valor"), Json.texto(m, "texto"));
                modelo.addElement(opcion);
                if (opcion.valor().equals(Json.texto(v, "mes"))) {
                    modelo.setSelectedItem(opcion);
                }
            }
            selectorMes.setModel(modelo);
            cargandoMeses = false;
        }
        boolean correoListo = Json.logico(v, "correo_disponible");
        semanal.setEnabled(correoListo);
        mensual.setEnabled(correoListo);

        Tema.Paleta p = Tema.c();
        JsonObject t = Json.objeto(v, "tarjetas");
        ingresos.setText(Json.texto(t, "ingresos"));
        ingresos.setForeground(p.plataforma());
        gastos.setText(Json.texto(t, "gastos"));
        gastos.setForeground(p.acento());
        balance.setText(Json.texto(t, "balance"));
        balance.setForeground(Json.logico(t, "balance_positivo") ? p.exitoTexto() : p.acento());
        ahorro.setText(Json.texto(t, "ahorro"));
        movimientos.setText(Json.texto(t, "movimientos"));

        tituloCategorias.setText(Json.texto(v, "titulo_categorias"));
        tituloPlanGrafica.setText(Json.texto(v, "titulo_plan_mes"));
        tituloPlanTabla.setText(Json.texto(v, "titulo_plan"));

        List<JsonObject> serie = Json.lista(v, "serie");
        alHacerClic(mostrar(graficaMeses, graficaMeses(v)), i -> elegirMes(Json.texto(serie.get(i), "mes")));
        List<JsonObject> categorias = Json.lista(v, "categorias");
        ayudaCategorias.setVisible(!categorias.isEmpty());
        if (categorias.isEmpty()) {
            graficaCategorias.removeAll();
            JLabel vacio = new JLabel("No hay gastos en este mes.", JLabel.CENTER);
            vacio.setForeground(p.textoSuave());
            graficaCategorias.add(vacio);
            graficaCategorias.revalidate();
            graficaCategorias.repaint();
        } else {
            alHacerClic(mostrar(graficaCategorias, graficaCategorias(categorias)),
                    i -> ventana.verRegistros(Json.objeto(categorias.get(i), "filtro")));
        }
        mostrar(graficaPlan, graficaPlan(Json.lista(v, "plan")));
        llenarTablaPlan(Json.lista(v, "plan"));
    }

    static ChartPanel mostrar(JPanel contenedor, JFreeChart grafica) {
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
        return panel;
    }

    /** Ayuda pequeña bajo el título de una gráfica (.texto-ayuda). */
    static JTextArea ayuda(String texto) {
        JTextArea a = Ui.suave(texto);
        a.setFont(a.getFont().deriveFont(12.5f));
        return a;
    }

    /** Al hacer clic en un elemento de la gráfica (barra o porción), su posición en los datos. */
    static void alHacerClic(ChartPanel panel, java.util.function.IntConsumer accion) {
        panel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        panel.addChartMouseListener(new org.jfree.chart.ChartMouseListener() {
            @Override
            public void chartMouseClicked(org.jfree.chart.ChartMouseEvent e) {
                if (e.getEntity() instanceof org.jfree.chart.entity.CategoryItemEntity c) {
                    accion.accept(c.getDataset().getColumnIndex(c.getColumnKey()));
                } else if (e.getEntity() instanceof org.jfree.chart.entity.PieSectionEntity p) {
                    accion.accept(p.getSectionIndex());
                }
            }

            @Override
            public void chartMouseMoved(org.jfree.chart.ChartMouseEvent e) {
            }
        });
    }

    // ───────────────────────── Gráficas ─────────────────────────

    static Color color(String hex) {
        return Color.decode(hex);
    }

    /** "$ 1.500.000" en los ejes, como dashboard.js de la web (solo presentación). */
    static final NumberFormat FORMATO_EJE = new NumberFormat() {
        private final DecimalFormat miles = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(new Locale("es", "CO")));

        @Override
        public StringBuffer format(double numero, StringBuffer destino, FieldPosition pos) {
            String texto = miles.format(Math.abs(Math.round(numero))).replace(',', '.').replace(' ', '.');
            return destino.append(numero < 0 ? "-$ " : "$ ").append(texto);
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

    static void estilo(JFreeChart g) {
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

    private static JFreeChart graficaMeses(JsonObject v) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        for (JsonObject m : Json.lista(v, "serie")) {
            String etiqueta = Json.texto(m, "etiqueta");
            datos.addValue(Json.numero(m, "ingresos"), "Ingresos", etiqueta);
            datos.addValue(Json.numero(m, "gastos"), "Gastos", etiqueta);
        }
        JFreeChart g = ChartFactory.createBarChart(null, null, null, datos, PlotOrientation.VERTICAL, true, true, false);
        estilo(g);
        g.getLegend().setPosition(RectangleEdge.TOP);
        BarRenderer r = (BarRenderer) g.getCategoryPlot().getRenderer();
        JsonObject colores = Json.objeto(v, "colores");
        r.setSeriesPaint(0, color(Json.texto(colores, "ingresos")));
        r.setSeriesPaint(1, color(Json.texto(colores, "gastos")));
        r.setMaximumBarWidth(0.06);
        return g;
    }

    private static JFreeChart graficaCategorias(List<JsonObject> categorias) {
        DefaultPieDataset<String> datos = new DefaultPieDataset<>();
        for (JsonObject c : categorias) {
            datos.setValue(Json.texto(c, "nombre"), Json.numero(c, "total"));
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
        for (JsonObject c : categorias) {
            plot.setSectionPaint(Json.texto(c, "nombre"), color(Json.texto(c, "color")));
        }
        return g;
    }

    private static JFreeChart graficaPlan(List<JsonObject> plan) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        List<String> colores = new ArrayList<>();
        for (JsonObject f : plan) {
            String grupo = Json.texto(f, "grupo");
            datos.addValue(Json.numero(f, "presupuesto"), "Presupuesto", grupo);
            datos.addValue(Json.numero(f, "real"), "Real", grupo);
            colores.add(Json.texto(f, "color_grupo"));
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
        r.setSeriesPaint(1, colores.isEmpty() ? presupuesto : color(colores.get(0)));
        plot.setRenderer(r);
        return g;
    }

    // ───────────────────────── Tabla del plan ─────────────────────────

    private void llenarTablaPlan(List<JsonObject> plan) {
        tablaPlan.removeAll();
        Tema.Paleta p = Tema.c();
        for (String encabezado : new String[] {"Grupo", "Presupuesto", "Real", "Usado", "Estado"}) {
            tablaPlan.add(celda(encabezado, p.textoSuave(), Font.BOLD));
        }
        for (JsonObject f : plan) {
            JLabel grupo = celda(Json.texto(f, "texto_grupo"), p.texto(), Font.PLAIN);
            grupo.setIcon(new Punto(color(Json.texto(f, "color_grupo"))));
            tablaPlan.add(grupo);
            tablaPlan.add(celda(Json.texto(f, "texto_presupuesto"), p.texto(), Font.PLAIN));
            tablaPlan.add(celda(Json.texto(f, "texto_real"), p.texto(), Font.PLAIN));
            tablaPlan.add(celda(Json.texto(f, "usado"), p.texto(), Font.PLAIN));
            tablaPlan.add(celda(Json.texto(f, "estado"), colorEstado(Json.texto(f, "clase_estado")), Font.BOLD));
        }
        tablaPlan.revalidate();
        tablaPlan.repaint();
    }

    /** Mismo tono que .estado-bien / .estado-mal / .estado-alerta de la web, en claro y oscuro. */
    static Color colorEstado(String clase) {
        boolean oscuro = Tema.esOscuro();
        return switch (clase) {
            case "bien" -> oscuro ? color("#75e0a7") : color("#067647");
            case "mal" -> oscuro ? color("#fda29b") : color("#a8221a");
            case "neutro" -> Tema.c().textoSuave();
            default -> oscuro ? color("#f7b27a") : color("#b54708");
        };
    }

    static JLabel celda(String texto, Color color, int estilo) {
        JLabel l = new JLabel(texto);
        l.setForeground(color);
        l.setFont(l.getFont().deriveFont(estilo, 13f));
        l.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        return l;
    }

    /** El punto de color del grupo (.punto en la web). */
    record Punto(Color color) implements javax.swing.Icon {
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

    /** "Enviarme el resumen ahora": lo arma y lo envía el servidor, igual que desde la web. */
    private void enviarResumen(String tipo) {
        Tarea.ejecutar(this, () -> Api.post("/api/dashboard/resumen", Json.de("tipo", tipo)),
                r -> aviso.exito(Json.texto(r, "mensaje")),
                e -> aviso.error(Ui.mensajeError("enviar el resumen").apply(e)));
    }
}
