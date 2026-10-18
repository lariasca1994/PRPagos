"""
Página del presupuesto mensual. La vista (montos, comparación con los
registros, gráficas y textos) la calcula servicios/presupuesto.py, la misma
que recibe el escritorio por la API.
"""
import asyncio
import json
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from ..dependencias import UsuarioActual, exigir_login
from ..servicios import ErrorServicio
from ..servicios import presupuesto as servicio

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")
PREFIJO_MONTO = "monto_"


def _volver(anio: int, mes: int, clave: str, mensaje: str) -> RedirectResponse:
    return RedirectResponse(url=f"/presupuesto?anio={anio}&mes={mes}&{clave}={quote(mensaje)}",
                            status_code=303)


@router.get("/presupuesto")
def presupuesto(request: Request, usuario: UsuarioActual = Depends(exigir_login),
                anio: str | None = None, mes: str | None = None):
    elegido = servicio.anio_elegido(anio)
    vista = servicio.vista(usuario.usuario_id, elegido, servicio.mes_elegido(mes, elegido))
    return templates.TemplateResponse("presupuesto.html", {
        "request": request, "usuario": usuario, "v": vista, "graficas_json": json.dumps(vista).replace("<", "\\u003c"),
        "error": request.query_params.get("error"), "ok": request.query_params.get("ok"),
    })


@router.post("/presupuesto")
async def guardar(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    formulario = await request.form()
    anio = servicio.anio_elegido(str(formulario.get("anio", "")))
    mes = servicio.mes_elegido(str(formulario.get("mes", "")), anio)
    montos = {k[len(PREFIJO_MONTO):]: str(v) for k, v in formulario.items() if k.startswith(PREFIJO_MONTO)}
    alcance = str(formulario.get("alcance", ""))
    try:
        # Oracle es bloqueante: en una ruta async se manda al threadpool.
        mensaje = await asyncio.to_thread(servicio.guardar, usuario.usuario_id, anio, mes, montos, alcance)
    except ErrorServicio as e:
        return _volver(anio, mes, "error", e.mensaje)
    return _volver(anio, mes, "ok", mensaje)


@router.post("/presupuesto/copiar")
def copiar(anio: str = Form(""), mes: str = Form(""), usuario: UsuarioActual = Depends(exigir_login)):
    elegido = servicio.anio_elegido(anio)
    numero = servicio.mes_elegido(mes, elegido)
    try:
        mensaje = servicio.copiar_mes_anterior(usuario.usuario_id, elegido, numero)
    except ErrorServicio as e:
        return _volver(elegido, numero, "error", e.mensaje)
    return _volver(elegido, numero, "ok", mensaje)
