"""
Dashboard del mes: tarjetas, ingresos vs. gastos de 6 meses, gastos por
categoría y plan 50/30/20, más el envío del resumen por correo. Devuelve la
vista ya calculada y con los textos formateados: la página web (Chart.js) y el
escritorio (JFreeChart) dibujan exactamente estos valores.
"""
from datetime import date

from .. import correo, finanzas, repositorio, resumenes
from . import ErrorServicio

CLASE_ESTADO = {finanzas.COLOR_BIEN: "bien", finanzas.COLOR_MAL: "mal", finanzas.COLOR_ALERTA: "alerta"}


def mes_elegido(texto: str | None) -> date:
    """"2026-09" -> 1 de septiembre de 2026; fuera de los últimos 12 meses, el actual."""
    hoy = finanzas.hoy().replace(day=1)
    try:
        anio, mes = (int(x) for x in (texto or "").split("-"))
        elegido = date(anio, mes, 1)
    except ValueError:
        return hoy
    return elegido if finanzas.meses_atras(hoy, 11) <= elegido <= hoy else hoy


def vista(usuario_id: int, mes: date) -> dict:
    siguiente = finanzas.meses_atras(mes, -1)
    ingresos, gastos, movimientos = repositorio.totales(usuario_id, mes, siguiente)
    serie = finanzas.completar_meses(
        mes, repositorio.serie_meses(usuario_id, finanzas.meses_atras(mes, 5), siguiente))
    categorias = repositorio.gastos_por_categoria(usuario_id, mes, siguiente)
    pcts = repositorio.plan(usuario_id)
    plan = finanzas.calcular_plan(ingresos, pcts, repositorio.gastos_por_grupo(usuario_id, mes, siguiente))
    balance = ingresos - gastos
    hoy = finanzas.hoy().replace(day=1)
    texto_plan = "/".join(finanzas.texto_pct(pcts[g]) for g in finanzas.GRUPOS)
    return {
        "mes": mes.strftime("%Y-%m"),
        "nombre_mes": finanzas.nombre_mes(mes),
        "meses": [{"valor": m.strftime("%Y-%m"), "texto": finanzas.nombre_mes(m)}
                  for m in (finanzas.meses_atras(hoy, i) for i in range(12))],
        "tarjetas": {
            "ingresos": finanzas.moneda(ingresos),
            "gastos": finanzas.moneda(gastos),
            "balance": finanzas.moneda(balance),
            "balance_positivo": balance >= 0,
            "ahorro": finanzas.porcentaje(max(balance, 0), ingresos),
            "movimientos": f"{movimientos} movimientos",
        },
        "titulo_plan": f"Plan {texto_plan}",
        "titulo_categorias": f"Gastos por categoría · {finanzas.nombre_mes(mes)}",
        "titulo_plan_mes": f"Plan {texto_plan} · {finanzas.nombre_mes(mes)}",
        "serie": [{"etiqueta": finanzas.etiqueta_mes_corta(m), "ingresos": round(i, 2), "gastos": round(g, 2)}
                  for m, i, g in serie],
        "categorias": [{"nombre": c, "total": round(t, 2),
                        "color": finanzas.PALETA_CATEGORIAS[n % len(finanzas.PALETA_CATEGORIAS)]}
                       for n, (c, _, t) in enumerate(categorias)],
        "plan": [{
            "grupo": finanzas.NOMBRE_GRUPO[f.grupo],
            "texto_grupo": f"{finanzas.NOMBRE_GRUPO[f.grupo]} ({finanzas.texto_pct(f.pct)}%)",
            "color_grupo": finanzas.COLOR_GRUPO[f.grupo],
            "presupuesto": f.presupuesto,
            "real": f.real,
            "texto_presupuesto": finanzas.moneda(f.presupuesto),
            "texto_real": finanzas.moneda(f.real),
            "usado": f"{int(f.usado + 0.5)} %",
            "estado": f.estado,
            "clase_estado": CLASE_ESTADO[f.color],
        } for f in plan],
        "colores": {"ingresos": finanzas.COLOR_INGRESOS, "gastos": finanzas.COLOR_GASTOS},
        "correo_disponible": correo.disponible(),
    }


def enviar_resumen(usuario_id: int, correo_usuario: str, nombre: str, tipo: str) -> str:
    """"Enviarme el resumen ahora": el semanal (últimos 7 días) o el del mes en curso."""
    dia = finanzas.hoy()
    periodo = finanzas.periodo_semanal(dia) if tipo == "semanal" else finanzas.periodo_mensual(dia)
    try:
        resumenes.enviar(usuario_id, correo_usuario, nombre, periodo)
    except correo.ErrorCorreo as error:
        raise ErrorServicio(str(error)) from error
    return f"Te enviamos el resumen a {correo_usuario}"
