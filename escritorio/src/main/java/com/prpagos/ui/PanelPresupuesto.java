package com.prpagos.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import com.google.gson.JsonObject;
import com.prpagos.cliente.Api;
import com.prpagos.cliente.Json;
import com.prpagos.datos.Pago;

/**
 * Presupuesto: misma pantalla que presupuesto.html de la web. Cuánto se piensa
 * recibir y gastar en cada categoría cada mes, frente a los registros reales.
 * La vista (cifras, estados, gráficas y textos) la calcula el servidor
 * (servicios/presupuesto.py) y es exactamente la misma que ve la web; aquí solo
 * se dibuja. Es interactiva como la web: un mes de las barras abre ese mes, una
 * categoría abre sus registros y un gasto grande abre el registro.
 */
class PanelPresupuesto extends JPanel {

    private record Opcion(String valor, String texto) {
        @Override
        public String toString() {
            return texto;
        }
    }

    private final VentanaPrincipal ventana;
    private final JComboBox<Opcion> selectorMes = new JComboBox<>();
    private final JComboBox<Opcion> selectorAnio = new JComboBox<>();
    private final JComboBox<Opcion> alcance = new JComboBox<>();
    private final Ui.Aviso aviso = new Ui.Aviso();
    private final JLabel ingresos = PanelDashboard.cifra();
    private final JLabel gastos = PanelDashboard.cifra();
    private final JLabel quedan = PanelDashboard.cifra();
    private final JLabel usado = PanelDashboard.cifra();
    private final JLabel ingresosPlan = new JLabel();
    private final JLabel gastosPlan = new JLabel();
    private final JLabel quedanPlan = new JLabel();
    private final JLabel usadoEstado = new JLabel();
    private final JLabel tituloSerie = Ui.titulo("", 16f);
    private final JLabel tituloDistribucion = Ui.titulo("", 16f);
    private final JLabel tituloMayores = Ui.titulo("", 16f);
    private final JLabel tituloTabla = Ui.titulo("", 16f);
    private final JLabel tituloAnual = Ui.titulo("", 16f);
    private final JPanel graficaAnual = PanelDashboard.contenedorGrafica(300);
    private final JPanel graficaDistribucion = PanelDashboard.contenedorGrafica(280);
    private final JPanel mayores = Ui.columna();
    private final JPanel tablaMes = new JPanel(new GridBagLayout());
    private final JPanel tablaAnual = new JPanel(new GridBagLayout());
    private final Map<String, JTextField> campos = new LinkedHashMap<>();
    private JsonObject ultima;
    private boolean llenando;

    PanelPresupuesto(VentanaPrincipal ventana) {
        this.ventana = ventana;
        setOpaque(false);
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setOpaque(false);
        cabecera.add(Ui.titulo("Presupuesto", 26f), BorderLayout.WEST);
        selectorMes.setPreferredSize(new Dimension(150, 36));
        selectorAnio.setPreferredSize(new Dimension(110, 36));
        for (JComboBox<Opcion> c : List.of(selectorMes, selectorAnio)) {
            c.addActionListener(e -> {
                if (!llenando) {
                    recargar();
                }
            });
        }
        JPanel derecha = Ui.fila(8);
        derecha.add(selectorMes);
        derecha.add(selectorAnio);
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
        tarjetas.add(PanelDashboard.tarjetaCifra("Ingresos", ingresos, ingresosPlan));
        tarjetas.add(PanelDashboard.tarjetaCifra("Gastos", gastos, gastosPlan));
        tarjetas.add(PanelDashboard.tarjetaCifra("Quedan", quedan, quedanPlan));
        tarjetas.add(PanelDashboard.tarjetaCifra("Presupuesto de gastos usado", usado, usadoEstado));
        tarjetas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        add(tarjetas);
        add(Ui.espacio(14));

        JPanel serie = PanelDashboard.tarjetaGrafica(tituloSerie, graficaAnual);
        serie.add(PanelDashboard.ayuda("Toca un mes para ver su detalle."), 2);
        add(serie);
        add(Ui.espacio(14));
        JPanel fila = new JPanel(new GridLayout(1, 2, 14, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.add(PanelDashboard.tarjetaGrafica(tituloDistribucion, graficaDistribucion));
        JPanel tarjetaMayores = Ui.tarjeta();
        tarjetaMayores.add(tituloMayores);
        tarjetaMayores.add(Ui.espacio(8));
        tarjetaMayores.add(mayores);
        fila.add(tarjetaMayores);
        add(fila);
        add(Ui.espacio(14));

        JPanel mes = Ui.tarjeta();
        mes.add(tituloTabla);
        mes.add(Ui.espacio(6));
        mes.add(PanelDashboard.ayuda("Escribe cuánto piensas recibir o gastar en cada categoría y compáralo con tus "
                + "registros. Toca una categoría para ver sus movimientos. Deja la casilla vacía para quitar el "
                + "presupuesto."));
        mes.add(Ui.espacio(10));
        tablaMes.setOpaque(false);
        tablaMes.setAlignmentX(LEFT_ALIGNMENT);
        mes.add(tablaMes);
        mes.add(Ui.espacio(14));
        JPanel acciones = Ui.fila(10);
        JLabel aplicar = new JLabel("Aplicar a");
        Tema.alCambiar(() -> aplicar.setForeground(Tema.c().textoSuave()));
        acciones.add(aplicar);
        alcance.setPreferredSize(new Dimension(220, 36));
        acciones.add(alcance);
        acciones.add(Ui.primario("Guardar presupuesto", e -> guardar()));
        mes.add(acciones);
        mes.add(Ui.espacio(12));
        mes.add(Ui.secundario("Copiar el presupuesto del mes anterior", e -> copiar()));
        add(mes);
        add(Ui.espacio(14));

        JPanel anual = Ui.tarjeta();
        anual.add(tituloAnual);
        anual.add(Ui.espacio(6));
        anual.add(PanelDashboard.ayuda("Presupuestado frente a lo registrado cada mes. Toca un mes para abrirlo."));
        anual.add(Ui.espacio(10));
        tablaAnual.setOpaque(false);
        tablaAnual.setAlignmentX(LEFT_ALIGNMENT);
        anual.add(tablaAnual);
        add(anual);

        Tema.alCambiar(() -> {
            if (ultima != null) {
                dibujar(ultima, false);
            }
        });
    }

    // ───────────────────────── Datos ─────────────────────────

    void recargar() {
        aviso.ocultar();
        cargar(valor(selectorAnio), valor(selectorMes), null);
    }

    /** Abre un año y mes concretos (desde el dashboard o un clic en las barras). */
    void abrir(int anio, int mes) {
        aviso.ocultar();
        cargar(String.valueOf(anio), String.valueOf(mes), null);
    }

    private static String valor(JComboBox<Opcion> c) {
        return c.getSelectedItem() instanceof Opcion o ? o.valor() : "";
    }

    /** Pide la vista; con mensaje, lo muestra después (como ?ok= en la web tras guardar). */
    private void cargar(String anio, String mes, String exito) {
        Tarea.ejecutar(this, () -> Api.get("/api/presupuesto", Map.of("anio", anio, "mes", mes)), v -> {
            dibujar(v, true);
            if (exito != null) {
                aviso.exito(exito);
            }
        }, e -> aviso.error(Ui.mensajeError("cargar el presupuesto").apply(e)));
    }

    private void guardar() {
        if (ultima == null) {
            return;
        }
        JsonObject montos = new JsonObject();
        campos.forEach((id, campo) -> montos.addProperty(id, campo.getText()));
        String anio = Json.texto(ultima, "anio");
        String mes = Json.texto(ultima, "mes");
        Tarea.ejecutar(this, () -> Api.put("/api/presupuesto",
                Json.de("anio", anio, "mes", mes, "alcance", valor(alcance), "montos", montos)),
                r -> cargar(anio, mes, Json.texto(r, "mensaje")),
                e -> aviso.error(Ui.mensajeError("guardar el presupuesto").apply(e)));
    }

    private void copiar() {
        if (ultima == null) {
            return;
        }
        String anio = Json.texto(ultima, "anio");
        String mes = Json.texto(ultima, "mes");
        Tarea.ejecutar(this, () -> Api.post("/api/presupuesto/copiar", Json.de("anio", anio, "mes", mes)),
                r -> cargar(anio, mes, Json.texto(r, "mensaje")),
                e -> aviso.error(Ui.mensajeError("copiar el presupuesto").apply(e)));
    }

    /** Un gasto grande abre su registro para modificarlo (como /registros/{id}/editar). */
    private void abrirRegistro(long id) {
        Tarea.ejecutar(this, () -> Api.get("/api/registros/formulario?id=" + id),
                r -> ventana.editar(Pago.de(Json.objeto(r, "registro"), Json.objeto(r, "tipos"))),
                e -> aviso.error(Ui.mensajeError("abrir el registro").apply(e)));
    }

    // ───────────────────────── Dibujo ─────────────────────────

    private void dibujar(JsonObject v, boolean nuevosDatos) {
        ultima = v;
        llenarSelectores(v);
        Tema.Paleta p = Tema.c();
        JsonObject t = Json.objeto(v, "tarjetas");
        ingresos.setText(Json.texto(t, "ingresos"));
        ingresos.setForeground(p.plataforma());
        ingresosPlan.setText(Json.texto(t, "ingresos_plan"));
        gastos.setText(Json.texto(t, "gastos"));
        gastos.setForeground(p.acento());
        gastosPlan.setText(Json.texto(t, "gastos_plan"));
        quedan.setText(Json.texto(t, "quedan"));
        quedan.setForeground(Json.logico(t, "quedan_positivo") ? p.exitoTexto() : p.acento());
        quedanPlan.setText(Json.texto(t, "quedan_plan"));
        usado.setText(Json.texto(t, "usado"));
        usadoEstado.setText(Json.texto(t, "usado_estado"));
        usadoEstado.setForeground(PanelDashboard.colorEstado(Json.texto(t, "usado_clase")));
        usadoEstado.setFont(usadoEstado.getFont().deriveFont(Font.BOLD));

        tituloSerie.setText(Json.texto(v, "titulo_serie"));
        tituloDistribucion.setText(Json.texto(v, "titulo_distribucion"));
        tituloMayores.setText(Json.texto(v, "titulo_mayores"));
        tituloTabla.setText(Json.texto(v, "titulo_tabla"));
        tituloAnual.setText(Json.texto(v, "titulo_anual"));

        List<JsonObject> serie = Json.lista(v, "serie");
        int anio = (int) Json.entero(v, "anio");
        PanelDashboard.alHacerClic(PanelDashboard.mostrar(graficaAnual, graficaAnual(v)),
                i -> abrir(anio, (int) Json.entero(serie.get(i), "mes")));
        List<JsonObject> distribucion = Json.lista(v, "distribucion");
        if (distribucion.isEmpty()) {
            vacio(graficaDistribucion, "Aún no hay presupuesto de gastos para este mes.");
        } else {
            PanelDashboard.mostrar(graficaDistribucion, graficaDistribucion(distribucion));
        }
        llenarMayores(v);
        // Los campos se rehacen solo con datos nuevos: al cambiar de tema se conserva lo escrito.
        if (nuevosDatos || campos.isEmpty()) {
            llenarTablaMes(Json.lista(v, "secciones"));
        } else {
            Map<String, String> escritos = new LinkedHashMap<>();
            campos.forEach((id, c) -> escritos.put(id, c.getText()));
            llenarTablaMes(Json.lista(v, "secciones"));
            escritos.forEach((id, texto) -> {
                if (campos.containsKey(id)) {
                    campos.get(id).setText(texto);
                }
            });
        }
        llenarTablaAnual(v);
        revalidate();
        repaint();
    }

    private void llenarSelectores(JsonObject v) {
        llenando = true;
        String mes = Json.texto(v, "mes");
        String anio = Json.texto(v, "anio");
        DefaultComboBoxModel<Opcion> meses = new DefaultComboBoxModel<>();
        for (JsonObject m : Json.lista(v, "meses")) {
            Opcion o = new Opcion(Json.texto(m, "valor"), Json.texto(m, "texto"));
            meses.addElement(o);
            if (o.valor().equals(mes)) {
                meses.setSelectedItem(o);
            }
        }
        selectorMes.setModel(meses);
        DefaultComboBoxModel<Opcion> anios = new DefaultComboBoxModel<>();
        for (String a : Json.textos(v, "anios")) {
            Opcion o = new Opcion(a, a);
            anios.addElement(o);
            if (a.equals(anio)) {
                anios.setSelectedItem(o);
            }
        }
        selectorAnio.setModel(anios);
        if (alcance.getItemCount() == 0) {
            DefaultComboBoxModel<Opcion> alcances = new DefaultComboBoxModel<>();
            for (JsonObject a : Json.lista(v, "alcances")) {
                alcances.addElement(new Opcion(Json.texto(a, "valor"), Json.texto(a, "texto")));
            }
            alcance.setModel(alcances);
        }
        llenando = false;
    }

    private static void vacio(JPanel contenedor, String texto) {
        contenedor.removeAll();
        JLabel l = new JLabel(texto, JLabel.CENTER);
        l.setForeground(Tema.c().textoSuave());
        contenedor.add(l);
        contenedor.revalidate();
        contenedor.repaint();
    }

    // ───────────────────────── Gráficas ─────────────────────────

    private static JFreeChart graficaAnual(JsonObject v) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        List<JsonObject> serie = Json.lista(v, "serie");
        for (JsonObject m : serie) {
            String etiqueta = Json.texto(m, "etiqueta");
            datos.addValue(Json.numero(m, "gastos_plan"), "Presupuestado", etiqueta);
            datos.addValue(Json.numero(m, "gastos_real"), "Real", etiqueta);
        }
        long elegido = Json.entero(v, "mes");
        Color presupuesto = Tema.esOscuro() ? PanelDashboard.color("#3A4157") : PanelDashboard.color("#C9CED8");
        Color gasto = PanelDashboard.color(Json.texto(Json.objeto(v, "colores"), "gastos"));
        // El mes elegido en rojo pleno y los demás al 70 % (como el "B3" de presupuesto.js).
        Color suave = new Color(gasto.getRed(), gasto.getGreen(), gasto.getBlue(), 0xB3);
        JFreeChart g = ChartFactory.createBarChart(null, null, null, datos, PlotOrientation.VERTICAL, true, true, false);
        PanelDashboard.estilo(g);
        g.getLegend().setPosition(RectangleEdge.TOP);
        BarRenderer r = new BarRenderer() {
            @Override
            public Paint getItemPaint(int fila, int columna) {
                if (fila == 0) {
                    return presupuesto;
                }
                return Json.entero(serie.get(columna), "mes") == elegido ? gasto : suave;
            }
        };
        r.setBarPainter(new StandardBarPainter());
        r.setShadowVisible(false);
        r.setItemMargin(0.08);
        r.setSeriesPaint(0, presupuesto);
        r.setSeriesPaint(1, suave);
        g.getCategoryPlot().setRenderer(r);
        return g;
    }

    private static JFreeChart graficaDistribucion(List<JsonObject> distribucion) {
        DefaultPieDataset<String> datos = new DefaultPieDataset<>();
        for (JsonObject d : distribucion) {
            datos.setValue(Json.texto(d, "etiqueta"), Json.numero(d, "total"));
        }
        JFreeChart g = ChartFactory.createRingChart(null, datos, true, true, false);
        PanelDashboard.estilo(g);
        g.getLegend().setPosition(RectangleEdge.RIGHT);
        RingPlot plot = (RingPlot) g.getPlot();
        plot.setBackgroundPaint(Tema.c().superficie());
        plot.setOutlineVisible(false);
        plot.setSectionDepth(0.40);
        plot.setSeparatorsVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelGenerator(null);
        plot.setDefaultSectionOutlinePaint(Tema.c().superficie());
        plot.setDefaultSectionOutlineStroke(new java.awt.BasicStroke(2f));
        for (JsonObject d : distribucion) {
            plot.setSectionPaint(Json.texto(d, "etiqueta"), PanelDashboard.color(Json.texto(d, "color")));
        }
        return g;
    }

    // ───────────────────────── Mayores gastos ─────────────────────────

    private void llenarMayores(JsonObject v) {
        mayores.removeAll();
        Tema.Paleta p = Tema.c();
        List<JsonObject> lista = Json.lista(v, "mayores_gastos");
        if (lista.isEmpty()) {
            JLabel l = new JLabel("No hay gastos en este mes.");
            l.setForeground(p.textoSuave());
            mayores.add(l);
        }
        for (int i = 0; i < lista.size(); i++) {
            JsonObject gasto = lista.get(i);
            JPanel fila = new JPanel(new BorderLayout(12, 0));
            fila.setOpaque(false);
            fila.setAlignmentX(LEFT_ALIGNMENT);
            fila.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, i < lista.size() - 1 ? 1 : 0, 0, p.borde()),
                    BorderFactory.createEmptyBorder(7, 0, 7, 0)));
            JPanel izquierda = Ui.columna();
            JLabel concepto = new JLabel(Json.texto(gasto, "concepto"));
            concepto.setFont(concepto.getFont().deriveFont(Font.BOLD, 13.5f));
            concepto.setForeground(p.texto());
            JLabel detalle = new JLabel(Json.texto(gasto, "categoria") + " · " + Json.texto(gasto, "fecha"));
            detalle.setFont(detalle.getFont().deriveFont(12f));
            detalle.setForeground(p.textoSuave());
            izquierda.add(concepto);
            izquierda.add(detalle);
            JPanel derecha = Ui.columna();
            JLabel monto = new JLabel(Json.texto(gasto, "monto"), SwingConstants.RIGHT);
            monto.setFont(monto.getFont().deriveFont(Font.BOLD, 13.5f));
            monto.setForeground(p.acento());
            monto.setAlignmentX(RIGHT_ALIGNMENT);
            JLabel parte = new JLabel(Json.texto(gasto, "parte") + " del gasto", SwingConstants.RIGHT);
            parte.setFont(parte.getFont().deriveFont(11.5f));
            parte.setForeground(p.textoSuave());
            parte.setAlignmentX(RIGHT_ALIGNMENT);
            derecha.add(monto);
            derecha.add(parte);
            fila.add(izquierda, BorderLayout.CENTER);
            fila.add(derecha, BorderLayout.EAST);
            fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, fila.getPreferredSize().height));
            long id = Json.entero(gasto, "id_pago");
            clic(fila, () -> abrirRegistro(id));
            mayores.add(fila);
        }
        if (!lista.isEmpty()) {
            mayores.add(Ui.espacio(8));
            JsonObject filtro = Json.objeto(v, "filtro_mes");
            mayores.add(Ui.enlace("Ver todos los registros del mes", e -> ventana.verRegistros(filtro)));
        }
        mayores.revalidate();
        mayores.repaint();
    }

    private static void clic(JComponent c, Runnable accion) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                accion.run();
            }
        });
    }

    // ───────────────────────── Tabla del mes ─────────────────────────

    private static final String[] ENCABEZADOS = {"Categoría", "Presupuesto", "Real", "Usado", "Estado"};
    private static final double[] PESOS = {0.30, 0.20, 0.18, 0.16, 0.22};

    private void llenarTablaMes(List<JsonObject> secciones) {
        tablaMes.removeAll();
        campos.clear();
        Tema.Paleta p = Tema.c();
        int fila = 0;
        for (int c = 0; c < ENCABEZADOS.length; c++) {
            tablaMes.add(PanelDashboard.celda(ENCABEZADOS[c], p.textoSuave(), Font.BOLD), celda(c, fila, 1, null));
        }
        fila++;
        for (JsonObject s : secciones) {
            JLabel titulo = new JLabel("<html><b>" + html(Json.texto(s, "titulo")) + "</b>"
                    + (Json.texto(s, "meta").isEmpty() ? "" : "&nbsp;&nbsp;<span style='font-size:9px;color:"
                    + hex(p.textoSuave()) + "'>" + html(Json.texto(s, "meta")) + "</span>") + "</html>");
            titulo.setIcon(new PanelDashboard.Punto(PanelDashboard.color(Json.texto(s, "color"))));
            titulo.setForeground(p.texto());
            titulo.setOpaque(true);
            titulo.setBackground(p.fondo());
            titulo.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
            tablaMes.add(titulo, celda(0, fila++, ENCABEZADOS.length, null));
            for (JsonObject f : Json.lista(s, "filas")) {
                agregarFila(f, fila++, false);
            }
            agregarFila(Json.objeto(s, "total"), fila++, true);
        }
        tablaMes.revalidate();
        tablaMes.repaint();
    }

    private void agregarFila(JsonObject f, int fila, boolean total) {
        Tema.Paleta p = Tema.c();
        Color fondo = total ? p.fondo() : null;
        int estilo = total ? Font.BOLD : Font.PLAIN;
        JComponent nombre;
        if (total) {
            nombre = PanelDashboard.celda(Json.texto(f, "nombre"), p.texto(), Font.BOLD);
        } else {
            JLabel enlace = PanelDashboard.celda(Json.texto(f, "nombre"), p.plataforma(), Font.BOLD);
            JsonObject filtro = Json.objeto(f, "filtro");
            clic(enlace, () -> ventana.verRegistros(filtro));
            nombre = enlace;
        }
        JComponent presupuesto;
        if (total) {
            presupuesto = PanelDashboard.celda(Json.texto(f, "texto_presupuesto"), p.texto(), estilo);
        } else if (Json.logico(f, "editable")) {
            JTextField campo = new JTextField(Json.texto(f, "valor_campo"), 9);
            campo.setHorizontalAlignment(JTextField.RIGHT);
            campo.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "0");
            campo.getAccessibleContext().setAccessibleName("Presupuesto de " + Json.texto(f, "nombre"));
            campo.addActionListener(e -> guardar());
            campos.put(Json.texto(f, "categoria_id"), campo);
            JPanel envoltura = new JPanel(new BorderLayout());
            envoltura.setOpaque(false);
            envoltura.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 12));
            envoltura.add(campo, BorderLayout.WEST);
            presupuesto = envoltura;
        } else {
            presupuesto = PanelDashboard.celda("—", p.textoSuave(), Font.PLAIN);
        }
        JLabel real = PanelDashboard.celda(Json.texto(f, "texto_real"), p.texto(), estilo);
        JPanel uso = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        uso.setOpaque(false);
        uso.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        uso.add(new BarraUso((int) Json.entero(f, "barra"), Json.texto(f, "clase_estado")));
        uso.add(PanelDashboard.celda(Json.texto(f, "usado"), p.texto(), estilo));
        String clase = Json.texto(f, "clase_estado");
        JLabel estado = PanelDashboard.celda(Json.texto(f, "estado"), PanelDashboard.colorEstado(clase),
                "neutro".equals(clase) ? Font.PLAIN : Font.BOLD);
        JComponent[] celdas = {nombre, presupuesto, real, uso, estado};
        for (int c = 0; c < celdas.length; c++) {
            JComponent x = celdas[c];
            JPanel caja = new JPanel(new BorderLayout());
            caja.setOpaque(fondo != null);
            if (fondo != null) {
                caja.setBackground(fondo);
            }
            caja.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, p.borde()));
            caja.add(x, BorderLayout.CENTER);
            if (c == 0) {
                x.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 4));
            }
            tablaMes.add(caja, celda(c, fila, 1, null));
        }
    }

    private static GridBagConstraints celda(int x, int y, int ancho, Insets margen) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.gridwidth = ancho;
        g.weightx = ancho > 1 ? 1 : PESOS[x];
        g.fill = GridBagConstraints.BOTH;
        g.anchor = GridBagConstraints.WEST;
        if (margen != null) {
            g.insets = margen;
        }
        return g;
    }

    /** Barra de uso del presupuesto (.barra-uso de la web), con el color del estado. */
    private static final class BarraUso extends JComponent {
        private final int porcentaje;
        private final String clase;

        BarraUso(int porcentaje, String clase) {
            this.porcentaje = porcentaje;
            this.clase = clase;
            setPreferredSize(new Dimension(80, 30));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ancho = 72;
            int alto = 7;
            int y = (getHeight() - alto) / 2;
            g2.setColor(Tema.c().borde());
            g2.fillRoundRect(0, y, ancho, alto, alto, alto);
            if (porcentaje > 0 && !"neutro".equals(clase)) {
                g2.setColor(PanelDashboard.colorEstado(clase));
                g2.fillRoundRect(0, y, Math.max(alto, ancho * porcentaje / 100), alto, alto, alto);
            }
            g2.dispose();
        }
    }

    // ───────────────────────── Resumen del año ─────────────────────────

    private void llenarTablaAnual(JsonObject v) {
        tablaAnual.removeAll();
        Tema.Paleta p = Tema.c();
        GridBagConstraints g;
        String[] grupos = {"Mes", "Presupuestado", "Real"};
        int[] anchos = {1, 3, 3};
        int x = 0;
        for (int i = 0; i < grupos.length; i++) {
            JLabel l = PanelDashboard.celda(grupos[i], p.textoSuave(), Font.BOLD);
            l.setHorizontalAlignment(i == 0 ? SwingConstants.LEFT : SwingConstants.CENTER);
            g = celdaAnual(x, 0, anchos[i]);
            tablaAnual.add(l, g);
            x += anchos[i];
        }
        String[] columnas = {"", "Ingresos", "Gastos", "Quedan", "Ingresos", "Gastos", "Quedan"};
        for (int c = 0; c < columnas.length; c++) {
            JLabel l = PanelDashboard.celda(columnas[c], p.textoSuave(), Font.BOLD);
            l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, p.borde()),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)));
            tablaAnual.add(l, celdaAnual(c, 1, 1));
        }
        int anio = (int) Json.entero(v, "anio");
        List<JsonObject> meses = Json.lista(v, "anual");
        for (int i = 0; i < meses.size(); i++) {
            int numero = i + 1;
            filaAnual(meses.get(i), i + 2, () -> abrir(anio, numero));
        }
        filaAnual(Json.objeto(v, "total_anual"), meses.size() + 2, null);
        tablaAnual.revalidate();
        tablaAnual.repaint();
    }

    private void filaAnual(JsonObject f, int fila, Runnable abrir) {
        Tema.Paleta p = Tema.c();
        boolean total = abrir == null;
        Color fondo = total ? p.fondo() : Json.logico(f, "actual") ? p.plataformaSuave() : null;
        int estilo = total ? Font.BOLD : Font.PLAIN;
        String[] claves = {"mes", "ingresos_plan", "gastos_plan", "quedan_plan", "ingresos_real", "gastos_real",
            "quedan_real"};
        for (int c = 0; c < claves.length; c++) {
            Color color = p.texto();
            int estiloCelda = estilo;
            if (c == 0 && !total) {
                color = p.plataforma();
                estiloCelda = Font.BOLD;
            } else if (c == 6) {
                color = Json.logico(f, "quedan_real_positivo") ? p.exitoTexto() : p.acento();
            }
            JLabel l = PanelDashboard.celda(Json.texto(f, claves[c]), color, estiloCelda);
            l.setFont(l.getFont().deriveFont(12.5f));
            l.setOpaque(fondo != null);
            if (fondo != null) {
                l.setBackground(fondo);
            }
            l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, p.borde()),
                    BorderFactory.createEmptyBorder(7, 6, 7, 6)));
            if (c == 0 && !total) {
                clic(l, abrir);
            }
            tablaAnual.add(l, celdaAnual(c, fila, 1));
        }
    }

    private static GridBagConstraints celdaAnual(int x, int y, int ancho) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.gridwidth = ancho;
        g.weightx = x == 0 ? 0.10 : 0.15;
        g.fill = GridBagConstraints.BOTH;
        return g;
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private static String html(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }
}
