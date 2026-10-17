"""
Reglas del control de ingresos y gastos: formato de moneda, periodos de los
resúmenes, plan 50/30/20 y armado del correo. Gemelo exacto de
escritorio/src/main/java/com/prpagos/finanzas/Finanzas.java: si se cambia
una regla aquí, hay que cambiarla allá.

Las fechas de los resúmenes se calculan en hora de Colombia (UTC-5, sin
horario de verano), para que el "hoy" sea el mismo en Cloud Run (UTC) y en
el escritorio.
"""
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone

from .compartido import Crudo, plantilla, rellenar

ZONA = timezone(timedelta(hours=-5))
MESES = [
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
]
MESES_CORTOS = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"]
GRUPOS = ["NECESIDADES", "DESEOS", "AHORRO"]
NOMBRE_GRUPO = {
    "NECESIDADES": "Necesidades",
    "DESEOS": "Deseos",
    "AHORRO": "Ahorro y deudas",
    "SIN_GRUPO": "Sin clasificar",
}
# Colores de las gráficas (los mismos en Chart.js y JFreeChart).
COLOR_INGRESOS = "#1A73E8"
COLOR_GASTOS = "#C74634"
COLOR_GRUPO = {
    "NECESIDADES": "#1A73E8",
    "DESEOS": "#E8A33D",
    "AHORRO": "#2E9E6A",
    "SIN_GRUPO": "#9AA3B5",
}
PALETA_CATEGORIAS = [
    "#C74634", "#1A73E8", "#E8A33D", "#2E9E6A", "#8E5BD6",
    "#3BB2C4", "#E0607E", "#7A8B2E", "#A0522D", "#5F6B7A",
]
COLOR_BIEN = "#067647"
COLOR_MAL = "#A8221A"
COLOR_ALERTA = "#B54708"


def hoy() -> date:
    return datetime.now(ZONA).date()


def moneda(valor: float) -> str:
    """$ 1.850.000,00 (formato colombiano, siempre con 2 decimales)."""
    signo = "-" if valor < 0 else ""
    entero, decimales = f"{abs(valor):,.2f}".split(".")
    return f"{signo}$ {entero.replace(',', '.')},{decimales}"


def porcentaje(parte: float, total: float) -> str:
    return "0 %" if total <= 0 else f"{round(parte * 100 / total)} %"


def texto_pct(valor: float) -> str:
    """50 -> "50", 33.5 -> "33,5" (porcentajes del plan)."""
    return f"{valor:g}".replace(".", ",")


# ───────────────────────── Periodos ─────────────────────────


@dataclass(frozen=True)
class Periodo:
    tipo: str          # "semanal" o "mensual"
    desde: date        # incluido
    hasta: date        # excluido
    clave: str         # identifica el periodo para no enviar dos veces

    @property
    def ultimo_dia(self) -> date:
        return self.hasta - timedelta(days=1)


def periodo_semanal(dia: date) -> Periodo:
    """Los 7 días que terminan en `dia` (el viernes: de sábado a viernes)."""
    return Periodo("semanal", dia - timedelta(days=6), dia + timedelta(days=1), dia.isoformat())


def periodo_mensual(dia: date) -> Periodo:
    """Del día 1 del mes hasta `dia` incluido."""
    return Periodo("mensual", dia.replace(day=1), dia + timedelta(days=1), dia.strftime("%Y-%m"))


def es_dia_semanal(dia: date) -> bool:
    return dia.weekday() == 4  # viernes


def es_dia_mensual(dia: date) -> bool:
    """El 29 de cada mes; en febrero, el último día (28 o 29)."""
    if dia.month == 2:
        return (dia + timedelta(days=1)).month == 3
    return dia.day == 29


def texto_periodo(p: Periodo) -> str:
    d, h = p.desde, p.ultimo_dia
    if d.year != h.year:
        return f"Del {d.day} de {MESES[d.month - 1]} de {d.year} al {h.day} de {MESES[h.month - 1]} de {h.year}"
    if d.month != h.month:
        return f"Del {d.day} de {MESES[d.month - 1]} al {h.day} de {MESES[h.month - 1]} de {h.year}"
    return f"Del {d.day} al {h.day} de {MESES[h.month - 1]} de {h.year}"


def meses_atras(mes: date, n: int) -> date:
    total = mes.year * 12 + (mes.month - 1) - n
    return date(total // 12, total % 12 + 1, 1)


def nombre_mes(mes: date) -> str:
    return f"{MESES[mes.month - 1].capitalize()} {mes.year}"


def etiqueta_mes_corta(mes: date) -> str:
    return f"{MESES_CORTOS[mes.month - 1]} {mes.year % 100:02d}"


# ───────────────────────── Plan 50/30/20 ─────────────────────────


@dataclass(frozen=True)
class FilaPlan:
    grupo: str
    pct: float
    presupuesto: float
    real: float
    estado: str
    color: str

    @property
    def usado(self) -> float:
        return 0 if self.presupuesto <= 0 else self.real * 100 / self.presupuesto


def calcular_plan(ingresos: float, pcts: dict[str, float], gastos_grupo: dict[str, float]) -> list[FilaPlan]:
    """Presupuesto de cada grupo = ingresos x porcentaje. En Necesidades y
    Deseos lo bueno es no pasarse; en Ahorro, llegar a la meta."""
    filas = []
    for grupo in GRUPOS:
        pct = float(pcts[grupo])
        presupuesto = round(ingresos * pct / 100, 2)
        real = round(float(gastos_grupo.get(grupo, 0)), 2)
        if presupuesto <= 0:
            estado, color = "Sin ingresos registrados", COLOR_ALERTA
        elif grupo == "AHORRO":
            estado, color = ("Meta cumplida", COLOR_BIEN) if real >= presupuesto else ("Por debajo de la meta", COLOR_ALERTA)
        else:
            estado, color = ("Dentro del plan", COLOR_BIEN) if real <= presupuesto else ("Excedido", COLOR_MAL)
        filas.append(FilaPlan(grupo, pct, presupuesto, real, estado, color))
    return filas


def completar_meses(fin: date, filas: list[tuple[date, float, float]], cantidad: int = 6) -> list[tuple[date, float, float]]:
    """Los `cantidad` meses que terminan en `fin`, con 0 donde no hubo movimientos."""
    por_mes = {f[0].replace(day=1): (float(f[1]), float(f[2])) for f in filas}
    meses = [meses_atras(fin, i) for i in range(cantidad - 1, -1, -1)]
    return [(m, *por_mes.get(m, (0.0, 0.0))) for m in meses]


# ───────────────────────── Correo de resumen ─────────────────────────


@dataclass(frozen=True)
class DatosResumen:
    nombre: str
    periodo: Periodo
    ingresos: float
    gastos: float
    movimientos: int
    categorias: list[tuple[str, str, float]]      # (categoría, grupo, total)
    pcts: dict[str, float]
    gastos_grupo: dict[str, float]


def asunto_resumen(p: Periodo) -> str:
    if p.tipo == "semanal":
        return f"Tu resumen semanal de PRPagos · {texto_periodo(p)}"
    return f"Tu resumen de {MESES[p.desde.month - 1]} en PRPagos"


def html_resumen(d: DatosResumen, enlace: str) -> str:
    balance = d.ingresos - d.gastos
    if d.categorias:
        filas = "".join(
            rellenar(plantilla("fila_categoria.html"), {
                "categoria": cat,
                "grupo": NOMBRE_GRUPO.get(grupo, grupo),
                "total": moneda(total),
                "porcentaje": porcentaje(total, d.gastos),
            })
            for cat, grupo, total in d.categorias
        )
    else:
        filas = plantilla("sin_gastos.html")
    bloque_plan = ""
    if d.periodo.tipo == "mensual":
        plan = calcular_plan(d.ingresos, d.pcts, d.gastos_grupo)
        filas_plan = "".join(
            rellenar(plantilla("fila_plan.html"), {
                "grupo": NOMBRE_GRUPO[f.grupo],
                "pct": texto_pct(f.pct),
                "presupuesto": moneda(f.presupuesto),
                "real": moneda(f.real),
                "color": f.color,
                "estado": f.estado,
            })
            for f in plan
        )
        bloque_plan = rellenar(plantilla("bloque_plan.html"), {
            "plan": "/".join(texto_pct(d.pcts[g]) for g in GRUPOS),
            "filas_plan": Crudo(filas_plan),
        })
    return rellenar(plantilla("resumen.html"), {
        "titulo": "Tu resumen semanal" if d.periodo.tipo == "semanal" else f"Tu resumen de {MESES[d.periodo.desde.month - 1]}",
        "periodo": texto_periodo(d.periodo),
        "nombre": d.nombre,
        "ingresos": moneda(d.ingresos),
        "gastos": moneda(d.gastos),
        "balance": moneda(balance),
        "color_balance": COLOR_BIEN if balance >= 0 else COLOR_MAL,
        "movimientos": d.movimientos,
        "bloque_plan": Crudo(bloque_plan),
        "filas_categorias": Crudo(filas),
        "enlace": enlace,
    })


def html_codigo(nombre: str, codigo: str, minutos: int) -> str:
    return rellenar(plantilla("codigo.html"), {"nombre": nombre, "codigo": codigo, "minutos": minutos})
