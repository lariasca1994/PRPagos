"""
Página del dashboard. La vista (cifras, series, plan y textos) la calcula
servicios/dashboard.py, la misma que recibe el escritorio por la API.
"""
import json
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from ..dependencias import UsuarioActual, exigir_login
from ..servicios import ErrorServicio
from ..servicios import dashboard as servicio

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")


@router.get("/dashboard")
def dashboard(request: Request, usuario: UsuarioActual = Depends(exigir_login), mes: str | None = None):
    vista = servicio.vista(usuario.usuario_id, servicio.mes_elegido(mes))
    return templates.TemplateResponse("dashboard.html", {
        "request": request, "usuario": usuario, "v": vista, "graficas_json": json.dumps(vista).replace("<", "\\u003c"),
        "error": request.query_params.get("error"), "ok": request.query_params.get("ok"),
    })


@router.post("/dashboard/enviar-resumen")
def enviar_resumen(tipo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    try:
        mensaje = servicio.enviar_resumen(usuario.usuario_id, usuario.usuario, usuario.nombre, tipo)
    except ErrorServicio as e:
        return RedirectResponse(url=f"/dashboard?error={quote(e.mensaje)}", status_code=303)
    return RedirectResponse(url=f"/dashboard?ok={quote(mensaje)}", status_code=303)
