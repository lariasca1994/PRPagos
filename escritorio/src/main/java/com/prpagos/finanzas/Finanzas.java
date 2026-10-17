package com.prpagos.finanzas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.prpagos.compartido.Compartido;

/**
 * Reglas del control de ingresos y gastos: formato de moneda, periodos de los
 * resúmenes, plan 50/30/20 y armado del correo. Gemelo exacto de
 * web/app/finanzas.py: si se cambia una regla allá, hay que cambiarla aquí.
 * Una prueba compara que ambos generan el mismo HTML, carácter por carácter.
 *
 * Los redondeos imitan a Python (mitad al par sobre el valor exacto del double).
 */
public final class Finanzas {

    public static final ZoneOffset ZONA = ZoneOffset.ofHours(-5);
    public static final String[] MESES = {
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
    };
    private static final String[] MESES_CORTOS = {
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic",
    };
    public static final List<String> GRUPOS = List.of("NECESIDADES", "DESEOS", "AHORRO");
    public static final Map<String, String> NOMBRE_GRUPO = Map.of(
            "NECESIDADES", "Necesidades",
            "DESEOS", "Deseos",
            "AHORRO", "Ahorro y deudas",
            "SIN_GRUPO", "Sin clasificar");
    public static final String COLOR_INGRESOS = "#1A73E8";
    public static final String COLOR_GASTOS = "#C74634";
    public static final Map<String, String> COLOR_GRUPO = Map.of(
            "NECESIDADES", "#1A73E8",
            "DESEOS", "#E8A33D",
            "AHORRO", "#2E9E6A",
            "SIN_GRUPO", "#9AA3B5");
    public static final List<String> PALETA_CATEGORIAS = List.of(
            "#C74634", "#1A73E8", "#E8A33D", "#2E9E6A", "#8E5BD6",
            "#3BB2C4", "#E0607E", "#7A8B2E", "#A0522D", "#5F6B7A");
    public static final String COLOR_BIEN = "#067647";
    public static final String COLOR_MAL = "#A8221A";
    public static final String COLOR_ALERTA = "#B54708";

    private Finanzas() {
    }

    public static LocalDate hoy() {
        return ZonedDateTime.now(ZONA).toLocalDate();
    }

    private static BigDecimal redondear(double valor, int decimales) {
        return new BigDecimal(valor).setScale(decimales, RoundingMode.HALF_EVEN);
    }

    /** $ 1.850.000,00 (formato colombiano, siempre con 2 decimales). */
    public static String moneda(double valor) {
        String plano = redondear(Math.abs(valor), 2).toPlainString();
        String entero = plano.substring(0, plano.indexOf('.'));
        String decimales = plano.substring(plano.indexOf('.') + 1);
        StringBuilder conPuntos = new StringBuilder();
        for (int i = 0; i < entero.length(); i++) {
            if (i > 0 && (entero.length() - i) % 3 == 0) {
                conPuntos.append('.');
            }
            conPuntos.append(entero.charAt(i));
        }
        return (valor < 0 ? "-" : "") + "$ " + conPuntos + "," + decimales;
    }

    /** Un número como lo escribe Python con str(float): 3000000.0, 250000.5 (CSV y auditoría). */
    public static String numero(double valor) {
        String plano = new BigDecimal(Double.toString(valor)).toPlainString();
        return plano.contains(".") ? plano : plano + ".0";
    }

    public static String porcentaje(double parte, double total) {
        return total <= 0 ? "0 %" : redondear(parte * 100 / total, 0).toPlainString() + " %";
    }

    /** 50 -> "50", 33.5 -> "33,5" (porcentajes del plan). */
    public static String textoPct(double valor) {
        return BigDecimal.valueOf(valor).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    // ───────────────────────── Periodos ─────────────────────────

    /** desde incluido, hasta excluido; clave identifica el periodo para no enviarlo dos veces. */
    public record Periodo(String tipo, LocalDate desde, LocalDate hasta, String clave) {
        public LocalDate ultimoDia() {
            return hasta.minusDays(1);
        }
    }

    public static Periodo periodoSemanal(LocalDate dia) {
        return new Periodo("semanal", dia.minusDays(6), dia.plusDays(1), dia.toString());
    }

    public static Periodo periodoMensual(LocalDate dia) {
        return new Periodo("mensual", dia.withDayOfMonth(1), dia.plusDays(1),
                String.format("%04d-%02d", dia.getYear(), dia.getMonthValue()));
    }

    public static boolean esDiaSemanal(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.FRIDAY;
    }

    /** El 29 de cada mes; en febrero, el último día (28 o 29). */
    public static boolean esDiaMensual(LocalDate dia) {
        if (dia.getMonthValue() == 2) {
            return dia.plusDays(1).getMonthValue() == 3;
        }
        return dia.getDayOfMonth() == 29;
    }

    public static String textoPeriodo(Periodo p) {
        LocalDate d = p.desde();
        LocalDate h = p.ultimoDia();
        String mesD = MESES[d.getMonthValue() - 1];
        String mesH = MESES[h.getMonthValue() - 1];
        if (d.getYear() != h.getYear()) {
            return "Del " + d.getDayOfMonth() + " de " + mesD + " de " + d.getYear() + " al " + h.getDayOfMonth()
                    + " de " + mesH + " de " + h.getYear();
        }
        if (d.getMonthValue() != h.getMonthValue()) {
            return "Del " + d.getDayOfMonth() + " de " + mesD + " al " + h.getDayOfMonth() + " de " + mesH + " de "
                    + h.getYear();
        }
        return "Del " + d.getDayOfMonth() + " al " + h.getDayOfMonth() + " de " + mesH + " de " + h.getYear();
    }

    public static LocalDate mesesAtras(LocalDate mes, int n) {
        return mes.withDayOfMonth(1).minusMonths(n);
    }

    public static String nombreMes(LocalDate mes) {
        String nombre = MESES[mes.getMonthValue() - 1];
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1) + " " + mes.getYear();
    }

    public static String etiquetaMesCorta(LocalDate mes) {
        return MESES_CORTOS[mes.getMonthValue() - 1] + " " + String.format("%02d", mes.getYear() % 100);
    }

    /** Ingresos y gastos de un mes. */
    public record Mes(LocalDate mes, double ingresos, double gastos) {
    }

    /** Los `cantidad` meses que terminan en `fin`, con 0 donde no hubo movimientos. */
    public static List<Mes> completarMeses(LocalDate fin, List<Mes> filas, int cantidad) {
        Map<LocalDate, Mes> porMes = new LinkedHashMap<>();
        for (Mes m : filas) {
            porMes.put(m.mes().withDayOfMonth(1), m);
        }
        List<Mes> meses = new ArrayList<>();
        for (int i = cantidad - 1; i >= 0; i--) {
            LocalDate m = mesesAtras(fin, i);
            Mes f = porMes.get(m);
            meses.add(new Mes(m, f == null ? 0 : f.ingresos(), f == null ? 0 : f.gastos()));
        }
        return meses;
    }

    // ───────────────────────── Plan 50/30/20 ─────────────────────────

    public record FilaPlan(String grupo, double pct, double presupuesto, double real, String estado, String color) {
        public double usado() {
            return presupuesto <= 0 ? 0 : real * 100 / presupuesto;
        }
    }

    /** Presupuesto = ingresos x porcentaje. Necesidades/Deseos: no pasarse; Ahorro: llegar a la meta. */
    public static List<FilaPlan> calcularPlan(double ingresos, Map<String, Double> pcts, Map<String, Double> gastosGrupo) {
        List<FilaPlan> filas = new ArrayList<>();
        for (String grupo : GRUPOS) {
            double pct = pcts.get(grupo);
            double presupuesto = redondear(ingresos * pct / 100, 2).doubleValue();
            double real = redondear(gastosGrupo.getOrDefault(grupo, 0.0), 2).doubleValue();
            String estado;
            String color;
            if (presupuesto <= 0) {
                estado = "Sin ingresos registrados";
                color = COLOR_ALERTA;
            } else if ("AHORRO".equals(grupo)) {
                boolean cumple = real >= presupuesto;
                estado = cumple ? "Meta cumplida" : "Por debajo de la meta";
                color = cumple ? COLOR_BIEN : COLOR_ALERTA;
            } else {
                boolean dentro = real <= presupuesto;
                estado = dentro ? "Dentro del plan" : "Excedido";
                color = dentro ? COLOR_BIEN : COLOR_MAL;
            }
            filas.add(new FilaPlan(grupo, pct, presupuesto, real, estado, color));
        }
        return filas;
    }

    // ───────────────────────── Correo de resumen ─────────────────────────

    public record Categoria(String nombre, String grupo, double total) {
    }

    public record DatosResumen(String nombre, Periodo periodo, double ingresos, double gastos, int movimientos,
                               List<Categoria> categorias, Map<String, Double> pcts, Map<String, Double> gastosGrupo) {
    }

    public static String asuntoResumen(Periodo p) {
        if ("semanal".equals(p.tipo())) {
            return "Tu resumen semanal de PRPagos · " + textoPeriodo(p);
        }
        return "Tu resumen de " + MESES[p.desde().getMonthValue() - 1] + " en PRPagos";
    }

    public static String htmlResumen(DatosResumen d, String enlace) {
        double balance = d.ingresos() - d.gastos();
        StringBuilder filas = new StringBuilder();
        if (d.categorias().isEmpty()) {
            filas.append(Compartido.plantilla("sin_gastos.html"));
        } else {
            for (Categoria c : d.categorias()) {
                filas.append(Compartido.rellenar(Compartido.plantilla("fila_categoria.html"), Map.of(
                        "categoria", c.nombre(),
                        "grupo", NOMBRE_GRUPO.getOrDefault(c.grupo(), c.grupo()),
                        "total", moneda(c.total()),
                        "porcentaje", porcentaje(c.total(), d.gastos())), Set.of()));
            }
        }
        String bloquePlan = "";
        if ("mensual".equals(d.periodo().tipo())) {
            StringBuilder filasPlan = new StringBuilder();
            for (FilaPlan f : calcularPlan(d.ingresos(), d.pcts(), d.gastosGrupo())) {
                filasPlan.append(Compartido.rellenar(Compartido.plantilla("fila_plan.html"), Map.of(
                        "grupo", NOMBRE_GRUPO.get(f.grupo()),
                        "pct", textoPct(f.pct()),
                        "presupuesto", moneda(f.presupuesto()),
                        "real", moneda(f.real()),
                        "color", f.color(),
                        "estado", f.estado()), Set.of()));
            }
            bloquePlan = Compartido.rellenar(Compartido.plantilla("bloque_plan.html"), Map.of(
                    "plan", textoPct(d.pcts().get("NECESIDADES")) + "/" + textoPct(d.pcts().get("DESEOS")) + "/"
                            + textoPct(d.pcts().get("AHORRO")),
                    "filas_plan", filasPlan.toString()), Set.of("filas_plan"));
        }
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("titulo", "semanal".equals(d.periodo().tipo()) ? "Tu resumen semanal"
                : "Tu resumen de " + MESES[d.periodo().desde().getMonthValue() - 1]);
        valores.put("periodo", textoPeriodo(d.periodo()));
        valores.put("nombre", d.nombre());
        valores.put("ingresos", moneda(d.ingresos()));
        valores.put("gastos", moneda(d.gastos()));
        valores.put("balance", moneda(balance));
        valores.put("color_balance", balance >= 0 ? COLOR_BIEN : COLOR_MAL);
        valores.put("movimientos", String.valueOf(d.movimientos()));
        valores.put("bloque_plan", bloquePlan);
        valores.put("filas_categorias", filas.toString());
        valores.put("enlace", enlace);
        return Compartido.rellenar(Compartido.plantilla("resumen.html"), valores,
                Set.of("bloque_plan", "filas_categorias"));
    }

    public static String htmlCodigo(String nombre, String codigo, int minutos) {
        return Compartido.rellenar(Compartido.plantilla("codigo.html"),
                Map.of("nombre", nombre, "codigo", codigo, "minutos", String.valueOf(minutos)), Set.of());
    }
}
