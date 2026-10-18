"""
Presupuesto mensual por categoría: cuánto se piensa recibir y gastar en cada
categoría cada mes del año, comparado con los registros reales del mismo mes.
Los gastos se agrupan como en el plan 50/30/20 (Necesidades, Deseos, Ahorro
y deudas) y cada grupo muestra qué parte de los ingresos presupuestados se
lleva frente a la meta del plan. "Quedan" = ingresos − gastos (el ahorro
cuenta como gasto: es dinero que ya tiene destino).

Devuelve la vista calculada y con los textos formateados: la página web
(Chart.js) y el escritorio (JFreeChart) muestran exactamente estos valores.
"""
import re
from datetime import date, timedelta

from .. import finanzas, repositorio
from . import ErrorServicio

SECCIONES = ["INGRESO", "NECESIDADES", "DESEOS", "AHORRO"]
TITULO_SECCION = {"INGRESO": "Ingresos", **{g: finanzas.NOMBRE_GRUPO[g] for g in finanzas.GRUPOS},
                  "SIN_GRUPO": "Sin clasificar"}
COLOR_SECCION = {"INGRESO": finanzas.COLOR_INGRESOS, **finanzas.COLOR_GRUPO}
COLOR_SIN_ASIGNAR = "#9AA3B5"
ALCANCES = {"mes": "Solo este mes", "resto": "De este mes a diciembre", "anio": "Todo el año"}
MAYORES_GASTOS = 5
MONTO_MAXIMO = 999_999_999_999.99
_MILES = re.compile(r"^\d{1,3}(\.\d{3})+(,\d{1,2})?$")


def anio_elegido(texto: str | None) -> int:
    """El año pedido si está entre el anterior y el siguiente; si no, el actual."""
    actual = finanzas.hoy().year
    try:
        anio = int(texto or "")
    except ValueError:
        return actual
    return anio if actual - 1 <= anio <= actual + 1 else actual


def mes_elegido(texto: str | None, anio: int) -> int:
    """1 a 12; sin mes válido, el actual (o enero si el año no es el actual)."""
    try:
        mes = int(texto or "")
    except ValueError:
        mes = 0
    if 1 <= mes <= 12:
        return mes
    hoy = finanzas.hoy()
    return hoy.month if anio == hoy.year else 1


def monto(texto: str) -> float | None:
    """"1.500.000", "1500000", "1500000,50" o "1500000.50"; vacío = 0. None si no es válido."""
    texto = (texto or "").strip().replace("$", "").replace(" ", "")
    if not texto:
        return 0.0
    if _MILES.match(texto):
        texto = texto.replace(".", "")
    texto = texto.replace(",", ".")
    try:
        valor = round(float(texto), 2)
    except ValueError:
        return None
    return valor if 0 <= valor <= MONTO_MAXIMO else None


def valor_campo(valor: float) -> str:
    """1500000.0 -> "1500000"; 1500000.5 -> "1500000,5" (lo que muestra la casilla)."""
    if valor <= 0:
        return ""
    return f"{valor:.2f}".rstrip("0").rstrip(".").replace(".", ",")


def _usado(plan: float, real: float) -> float | None:
    return None if plan <= 0 else real * 100 / plan


def _estado(seccion: str, plan: float, real: float) -> tuple[str, str]:
    """(texto, clase): bien, mal, alerta o neutro."""
    if plan <= 0:
        if real > 0 and seccion not in ("INGRESO", "AHORRO"):
            return "Sin presupuesto", "alerta"
        return "Sin presupuesto", "neutro"
    if seccion == "INGRESO":
        return ("Cumplido", "bien") if real >= plan else ("Por debajo de lo previsto", "alerta")
    if seccion == "AHORRO":
        return ("Meta cumplida", "bien") if real >= plan else ("Por debajo de la meta", "alerta")
    return ("Dentro del presupuesto", "bien") if real <= plan else ("Excedido", "mal")


def _filtro(seccion: str, categoria_id: int | None, mes: date) -> dict:
    """Filtros de Consultar registros para ver los movimientos de esa fila."""
    fin = finanzas.meses_atras(mes, -1) - timedelta(days=1)
    return {"tipo": "INGRESO" if seccion == "INGRESO" else "GASTO",
            "categoria": "" if categoria_id is None and seccion != "SIN_GRUPO" else str(categoria_id or 0),
            "fecha_desde": mes.isoformat(), "fecha_hasta": fin.isoformat()}


def _fila(seccion: str, nombre: str, plan: float, real: float, categoria_id: int | None,
          mes: date) -> dict:
    usado = _usado(plan, real)
    estado, clase = _estado(seccion, plan, real)
    return {
        "categoria_id": categoria_id,
        "nombre": nombre,
        "presupuesto": round(plan, 2),
        "real": round(real, 2),
        "valor_campo": valor_campo(plan),
        "texto_presupuesto": finanzas.moneda(plan),
        "texto_real": finanzas.moneda(real),
        "usado": "—" if usado is None else f"{int(usado + 0.5)} %",
        "barra": 0 if usado is None else min(int(usado + 0.5), 100),
        "estado": estado,
        "clase_estado": clase,
        "filtro": _filtro(seccion, categoria_id, mes),
        "editable": bool(categoria_id),
    }


def _datos_anio(usuario_id: int, anio: int):
    desde, hasta = date(anio, 1, 1), date(anio + 1, 1, 1)
    return (repositorio.categorias(usuario_id), repositorio.presupuesto_anual(usuario_id, desde, hasta),
            repositorio.real_por_categoria_mes(usuario_id, desde, hasta))


def _totales_mes(cats: list[dict], plan: dict, real: dict, mes: date) -> dict[str, float]:
    t = {"ingresos_plan": 0.0, "gastos_plan": 0.0, "ingresos_real": 0.0, "gastos_real": 0.0}
    for c in cats:
        clave = "ingresos" if c["tipo"] == "INGRESO" else "gastos"
        t[f"{clave}_plan"] += plan.get((c["id"], mes), 0.0)
    for (_, m, tipo), total in real.items():
        if m == mes:
            t["ingresos_real" if tipo == "INGRESO" else "gastos_real"] += total
    return t


def vista(usuario_id: int, anio: int, numero_mes: int) -> dict:
    cats, plan, real = _datos_anio(usuario_id, anio)
    mes = date(anio, numero_mes, 1)
    pcts = repositorio.plan(usuario_id)
    nombre = finanzas.nombre_mes(mes)

    # ── Tabla del mes: una sección por grupo, una fila por categoría ──
    secciones = []
    for seccion in SECCIONES:
        filas = [
            _fila(seccion, c["nombre"], plan.get((c["id"], mes), 0.0),
                  real.get((c["id"], mes, c["tipo"]), 0.0), c["id"], mes)
            for c in cats
            if (seccion == "INGRESO" and c["tipo"] == "INGRESO") or (c["tipo"] == "GASTO" and c["grupo"] == seccion)
        ]
        tipo = "INGRESO" if seccion == "INGRESO" else "GASTO"
        sin_categoria = real.get((None, mes, tipo), 0.0)
        if seccion == "INGRESO" and sin_categoria > 0:
            filas.append(_fila(seccion, "Sin categoría", 0.0, sin_categoria, 0, mes))
        secciones.append({"clave": seccion, "filas": filas})
    gastos_sin_categoria = real.get((None, mes, "GASTO"), 0.0)
    if gastos_sin_categoria > 0:
        secciones.append({"clave": "SIN_GRUPO",
                          "filas": [_fila("SIN_GRUPO", "Sin categoría", 0.0, gastos_sin_categoria, 0, mes)]})

    t = _totales_mes(cats, plan, real, mes)
    for s in secciones:
        total_plan = sum(f["presupuesto"] for f in s["filas"])
        total_real = sum(f["real"] for f in s["filas"])
        resumen = _fila(s["clave"], f"Total {TITULO_SECCION[s['clave']].lower()}", total_plan, total_real,
                        None, mes)
        s.update({
            "titulo": TITULO_SECCION[s["clave"]],
            "color": COLOR_SECCION[s["clave"]],
            "total": resumen,
            "meta": "",
        })
        if s["clave"] in finanzas.GRUPOS:
            parte = finanzas.porcentaje(total_plan, t["ingresos_plan"])
            s["meta"] = f"{parte} de los ingresos presupuestados · meta del plan {finanzas.texto_pct(pcts[s['clave']])} %"

    quedan_plan = t["ingresos_plan"] - t["gastos_plan"]
    quedan_real = t["ingresos_real"] - t["gastos_real"]
    usado_gastos = _usado(t["gastos_plan"], t["gastos_real"])
    estado_gastos, clase_gastos = _estado("GASTO", t["gastos_plan"], t["gastos_real"])

    # ── Gráfica de distribución: cómo se reparten los ingresos presupuestados ──
    distribucion = [
        {"nombre": TITULO_SECCION[s["clave"]], "total": s["total"]["presupuesto"], "color": s["color"]}
        for s in secciones if s["clave"] in finanzas.GRUPOS and s["total"]["presupuesto"] > 0
    ]
    if quedan_plan > 0 and distribucion:
        distribucion.append({"nombre": "Sin asignar", "total": round(quedan_plan, 2), "color": COLOR_SIN_ASIGNAR})
    base = t["ingresos_plan"] if t["ingresos_plan"] > 0 else sum(d["total"] for d in distribucion)
    for d in distribucion:
        d["etiqueta"] = f"{d['nombre']} ({finanzas.porcentaje(d['total'], base)})"

    # ── Año completo: serie de la gráfica y tabla resumen (como la hoja anual) ──
    serie, anual = [], []
    suma = {"ingresos_plan": 0.0, "gastos_plan": 0.0, "ingresos_real": 0.0, "gastos_real": 0.0}
    for n in range(1, 13):
        m = date(anio, n, 1)
        tm = _totales_mes(cats, plan, real, m)
        for k in suma:
            suma[k] += tm[k]
        serie.append({"mes": n, "etiqueta": finanzas.etiqueta_mes_corta(m), "gastos_plan": round(tm["gastos_plan"], 2),
                      "gastos_real": round(tm["gastos_real"], 2)})
        anual.append(_fila_anual(finanzas.MESES[n - 1].capitalize(), tm, n == numero_mes))
    total_anual = _fila_anual(f"Total {anio}", suma, False)

    # ── En qué se fue el dinero: los gastos más grandes del mes ──
    fin = finanzas.meses_atras(mes, -1) - timedelta(days=1)
    gastos_mes = repositorio.buscar_registros(usuario_id, {
        "tipo": "GASTO", "fecha_desde": mes, "fecha_hasta": fin, "concepto": None, "monto_min": None,
        "monto_max": None, "categoria": None})
    mayores = sorted(gastos_mes, key=lambda r: (-r["monto"], r["id_pago"]))[:MAYORES_GASTOS]

    hoy = finanzas.hoy()
    return {
        "anio": anio,
        "mes": numero_mes,
        "nombre_mes": nombre,
        "anios": [hoy.year - 1, hoy.year, hoy.year + 1],
        "meses": [{"valor": n, "texto": finanzas.MESES[n - 1].capitalize()} for n in range(1, 13)],
        "alcances": [{"valor": k, "texto": v} for k, v in ALCANCES.items()],
        "hay_presupuesto": t["ingresos_plan"] > 0 or t["gastos_plan"] > 0,
        "tarjetas": {
            "ingresos": finanzas.moneda(t["ingresos_real"]),
            "ingresos_plan": f"de {finanzas.moneda(t['ingresos_plan'])} presupuestados",
            "gastos": finanzas.moneda(t["gastos_real"]),
            "gastos_plan": f"de {finanzas.moneda(t['gastos_plan'])} presupuestados",
            "quedan": finanzas.moneda(quedan_real),
            "quedan_positivo": quedan_real >= 0,
            "quedan_plan": f"Presupuestado: {finanzas.moneda(quedan_plan)}",
            "usado": "—" if usado_gastos is None else f"{int(usado_gastos + 0.5)} %",
            "usado_estado": estado_gastos,
            "usado_clase": clase_gastos,
        },
        "titulo_serie": f"Gastos presupuestados vs. reales · {anio}",
        "titulo_distribucion": f"Distribución del presupuesto · {nombre}",
        "titulo_tabla": f"Presupuesto de {nombre}",
        "titulo_anual": f"Resumen del año {anio}",
        "titulo_mayores": f"Mayores gastos de {nombre}",
        "mayores_gastos": [{
            "id_pago": r["id_pago"],
            "fecha": r["fecha"].isoformat() if isinstance(r["fecha"], date) else str(r["fecha"]),
            "concepto": r["concepto"],
            "categoria": r["categoria"],
            "monto": finanzas.moneda(r["monto"]),
            "parte": finanzas.porcentaje(r["monto"], t["gastos_real"]),
        } for r in mayores],
        "filtro_mes": {"tipo": "", "categoria": "", "fecha_desde": mes.isoformat(), "fecha_hasta": fin.isoformat()},
        "serie": serie,
        "distribucion": distribucion,
        "secciones": secciones,
        "anual": anual,
        "total_anual": total_anual,
        "colores": {"presupuesto": COLOR_SIN_ASIGNAR, "gastos": finanzas.COLOR_GASTOS,
                    "ingresos": finanzas.COLOR_INGRESOS},
    }


def _fila_anual(mes: str, t: dict[str, float], actual: bool) -> dict:
    quedan_plan = t["ingresos_plan"] - t["gastos_plan"]
    quedan_real = t["ingresos_real"] - t["gastos_real"]
    return {
        "mes": mes,
        "actual": actual,
        "ingresos_plan": finanzas.moneda(t["ingresos_plan"]),
        "gastos_plan": finanzas.moneda(t["gastos_plan"]),
        "quedan_plan": finanzas.moneda(quedan_plan),
        "ingresos_real": finanzas.moneda(t["ingresos_real"]),
        "gastos_real": finanzas.moneda(t["gastos_real"]),
        "quedan_real": finanzas.moneda(quedan_real),
        "quedan_real_positivo": quedan_real >= 0,
    }


def estado_categoria(usuario_id: int, categoria_id: int | None, dia: date) -> str | None:
    """"Mercado en octubre 2026: $ 450.000,00 de $ 600.000,00 (75 %)" tras guardar un
    registro; None si esa categoría no tiene presupuesto ese mes."""
    if categoria_id is None:
        return None
    mes = dia.replace(day=1)
    siguiente = finanzas.meses_atras(mes, -1)
    plan = repositorio.presupuesto_anual(usuario_id, mes, siguiente).get((categoria_id, mes), 0.0)
    if plan <= 0:
        return None
    cats = {c["id"]: c for c in repositorio.categorias(usuario_id)}
    categoria = cats.get(categoria_id)
    if categoria is None:
        return None
    real = repositorio.real_por_categoria_mes(usuario_id, mes, siguiente).get(
        (categoria_id, mes, categoria["tipo"]), 0.0)
    seccion = "INGRESO" if categoria["tipo"] == "INGRESO" else categoria["grupo"]
    estado, _ = _estado(seccion, plan, real)
    return (f"{categoria['nombre']} en {finanzas.nombre_mes(mes).lower()}: {finanzas.moneda(real)} "
            f"de {finanzas.moneda(plan)} ({int(real * 100 / plan + 0.5)} %) · {estado}")


def _meses_alcance(anio: int, mes: int, alcance: str) -> list[date]:
    if alcance not in ALCANCES:
        raise ErrorServicio("Elige a qué meses aplicar el presupuesto")
    inicio, fin = {"mes": (mes, mes), "resto": (mes, 12), "anio": (1, 12)}[alcance]
    return [date(anio, n, 1) for n in range(inicio, fin + 1)]


def _texto_meses(anio: int, mes: int, alcance: str) -> str:
    if alcance == "mes":
        return f"de {finanzas.MESES[mes - 1]} {anio}"
    if alcance == "resto":
        return f"de {finanzas.MESES[mes - 1]} a diciembre de {anio}"
    return f"de todo {anio}"


def guardar(usuario_id: int, anio: int, mes: int, montos: dict[str, str], alcance: str) -> str:
    """montos: {categoria_id: texto}. Vacío o 0 quita el presupuesto de esa categoría."""
    meses = _meses_alcance(anio, mes, alcance)
    cats = {c["id"]: c["nombre"] for c in repositorio.categorias(usuario_id)}
    valores: list[tuple[int, float]] = []
    for clave, texto in montos.items():
        try:
            categoria_id = int(clave)
        except ValueError:
            categoria_id = -1
        if categoria_id not in cats:
            raise ErrorServicio("Categoría no válida para el presupuesto")
        valor = monto(str(texto if texto is not None else ""))
        if valor is None:
            raise ErrorServicio(f"Monto inválido en {cats[categoria_id]}")
        valores.append((categoria_id, valor))
    if not valores:
        raise ErrorServicio("No hay montos para guardar")
    texto = _texto_meses(anio, mes, alcance)
    repositorio.guardar_presupuesto(usuario_id, [(c, m, v) for m in meses for c, v in valores],
                                    f"Presupuesto {texto}")
    return f"Presupuesto {texto} guardado"


def copiar_mes_anterior(usuario_id: int, anio: int, mes: int) -> str:
    """El mes elegido queda igual al anterior (enero copia diciembre del año pasado)."""
    actual = date(anio, mes, 1)
    anterior = finanzas.meses_atras(actual, 1)
    plan = repositorio.presupuesto_anual(usuario_id, anterior, actual)
    if not plan:
        raise ErrorServicio("El mes anterior no tiene presupuesto para copiar")
    cats = repositorio.categorias(usuario_id)
    repositorio.guardar_presupuesto(
        usuario_id, [(c["id"], actual, plan.get((c["id"], anterior), 0.0)) for c in cats],
        f"Presupuesto copiado de {finanzas.MESES[anterior.month - 1]} {anterior.year}")
    return (f"Se copió el presupuesto de {finanzas.nombre_mes(anterior).lower()} "
            f"a {finanzas.nombre_mes(actual).lower()}")
