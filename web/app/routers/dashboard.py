"""
Dashboard: tarjetas del mes (ingresos, gastos, balance, ahorro), gráfico de
ingresos vs gastos de los últimos 6 meses, gastos por categoría y el plan
50/30/20. Los datos salen de compartido/consultas/ y las reglas de
finanzas.py: el escritorio (ui/PanelDashboard.java) muestra lo mismo.
"""
import json
from datetime import date
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import correo, finanzas, repositorio, resumenes
from ..dependencias import UsuarioActual, exigir_login

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")


def _mes_elegido(texto: str | None) -> date:
    hoy = finanzas.hoy().replace(day=1)
    try:
        anio, mes = (int(x) for x in (texto or "").split("-"))
        elegido = date(anio, mes, 1)
    except ValueError:
        return hoy
    return elegido if finanzas.meses_atras(hoy, 11) <= elegido <= hoy else hoy


def datos_dashboard(usuario_id: int, mes: date) -> dict:
    siguiente = finanzas.meses_atras(mes, -1)
    ingresos, gastos, movimientos = repositorio.totales(usuario_id, mes, siguiente)
    serie = finanzas.completar_meses(
        mes, repositorio.serie_meses(usuario_id, finanzas.meses_atras(mes, 5), siguiente)
    )
    categorias = repositorio.gastos_por_categoria(usuario_id, mes, siguiente)
    pcts = repositorio.plan(usuario_id)
    plan = finanzas.calcular_plan(ingresos, pcts, repositorio.gastos_por_grupo(usuario_id, mes, siguiente))
    balance = ingresos - gastos
    return {
        "ingresos": ingresos,
        "gastos": gastos,
        "balance": balance,
        "movimientos": movimientos,
        "tasa_ahorro": finanzas.porcentaje(max(balance, 0), ingresos),
        "categorias": categorias,
        "plan": plan,
        "pcts": pcts,
        "graficas": {
            "meses": [finanzas.etiqueta_mes_corta(m) for m, _, _ in serie],
            "ingresos": [round(i, 2) for _, i, _ in serie],
            "gastos": [round(g, 2) for _, _, g in serie],
            "categorias": [c for c, _, _ in categorias],
            "totales_categoria": [round(t, 2) for _, _, t in categorias],
            "colores_categoria": [
                finanzas.PALETA_CATEGORIAS[i % len(finanzas.PALETA_CATEGORIAS)] for i in range(len(categorias))
            ],
            "grupos": [finanzas.NOMBRE_GRUPO[f.grupo] for f in plan],
            "presupuesto": [f.presupuesto for f in plan],
            "real": [f.real for f in plan],
            "colores_grupo": [finanzas.COLOR_GRUPO[f.grupo] for f in plan],
            "color_ingresos": finanzas.COLOR_INGRESOS,
            "color_gastos": finanzas.COLOR_GASTOS,
        },
    }


@router.get("/dashboard")
def dashboard(request: Request, usuario: UsuarioActual = Depends(exigir_login), mes: str | None = None):
    elegido = _mes_elegido(mes)
    datos = datos_dashboard(usuario.usuario_id, elegido)
    hoy = finanzas.hoy().replace(day=1)
    meses = [finanzas.meses_atras(hoy, i) for i in range(12)]
    return templates.TemplateResponse(
        "dashboard.html",
        {
            "request": request,
            "usuario": usuario,
            "d": datos,
            "mes": elegido,
            "nombre_mes": finanzas.nombre_mes(elegido),
            "meses": [(m.strftime("%Y-%m"), finanzas.nombre_mes(m)) for m in meses],
            "moneda": finanzas.moneda,
            "nombre_grupo": finanzas.NOMBRE_GRUPO,
            "texto_pct": finanzas.texto_pct,
            "clase_estado": {finanzas.COLOR_BIEN: "estado-bien", finanzas.COLOR_MAL: "estado-mal",
                             finanzas.COLOR_ALERTA: "estado-alerta"},
            "graficas_json": json.dumps(datos["graficas"]),
            "correo_disponible": correo.disponible(),
            "error": request.query_params.get("error"),
            "ok": request.query_params.get("ok"),
        },
    )


@router.post("/dashboard/enviar-resumen")
def enviar_resumen(tipo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    """"Enviarme el resumen ahora": el semanal (últimos 7 días) o el del mes en curso."""
    dia = finanzas.hoy()
    periodo = finanzas.periodo_semanal(dia) if tipo == "semanal" else finanzas.periodo_mensual(dia)
    try:
        resumenes.enviar(usuario.usuario_id, usuario.usuario, usuario.nombre, periodo)
    except correo.ErrorCorreo as e:
        return RedirectResponse(url=f"/dashboard?error={quote(str(e))}", status_code=303)
    return RedirectResponse(url=f"/dashboard?ok={quote(f'Te enviamos el resumen a {usuario.usuario}')}",
                            status_code=303)
