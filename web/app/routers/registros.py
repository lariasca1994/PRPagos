"""
Páginas de registros de ingresos y gastos. La lógica (filtros, totales, CSV y
validaciones) está en servicios/registros.py, la misma que usa el escritorio.

Nota: la ruta se llama "registros" en la URL (no "pagos") a propósito: algunos
antivirus con protección de banca en línea interceptan la navegación cuando
la URL de un formulario con contraseña contiene "pagos".
"""
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse, Response
from fastapi.templating import Jinja2Templates

from ..dependencias import UsuarioActual, exigir_admin, exigir_login
from ..servicios import ErrorServicio
from ..servicios import registros as servicio

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")


def _redirigir(ruta: str, mensaje: str, exito: bool = False) -> RedirectResponse:
    clave = "ok" if exito else "error"
    separador = "&" if "?" in ruta else "?"
    return RedirectResponse(url=f"{ruta}{separador}{clave}={quote(mensaje)}", status_code=303)


@router.get("/registros")
def listar_registros(request: Request, usuario: UsuarioActual = Depends(exigir_login), tipo: str | None = None,
                     concepto: str | None = None, monto_min: str | None = None, monto_max: str | None = None,
                     fecha_desde: str | None = None, fecha_hasta: str | None = None):
    filtros = servicio.parsear_filtros(tipo, concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    resultado = servicio.consultar(usuario.usuario_id, usuario.es_admin, filtros)
    return templates.TemplateResponse("registros_lista.html", {
        "request": request, "usuario": usuario, "tipos": servicio.TIPOS, **resultado,
        "filtros": {"tipo": filtros["tipo"] or "", "concepto": concepto or "", "monto_min": monto_min or "",
                    "monto_max": monto_max or "", "fecha_desde": fecha_desde or "",
                    "fecha_hasta": fecha_hasta or ""},
    })


@router.get("/registros/exportar.csv")
def exportar_csv(usuario: UsuarioActual = Depends(exigir_login), tipo: str | None = None,
                 concepto: str | None = None, monto_min: str | None = None, monto_max: str | None = None,
                 fecha_desde: str | None = None, fecha_hasta: str | None = None):
    filtros = servicio.parsear_filtros(tipo, concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    return Response(servicio.csv_texto(usuario.usuario_id, usuario.es_admin, filtros), media_type="text/csv",
                    headers={"Content-Disposition": "attachment; filename=registros.csv"})


def _formulario(request: Request, usuario: UsuarioActual, plantilla: str, id_pago: int | None = None):
    datos = servicio.formulario(usuario.usuario_id, usuario.es_admin, id_pago)
    return templates.TemplateResponse(plantilla, {"request": request, "usuario": usuario, **datos,
                                                  "error": request.query_params.get("error")})


@router.get("/registros/nuevo")
def form_nuevo_registro(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    return _formulario(request, usuario, "registros_nuevo.html")


@router.post("/registros/nuevo")
def crear_registro(tipo: str = Form(...), monto: str = Form(...), fecha: str = Form(...),
                   categoria_id: str = Form(""), concepto: str = Form(...),
                   usuario: UsuarioActual = Depends(exigir_login)):
    try:
        mensaje = servicio.crear(usuario.usuario_id, tipo, monto, fecha, categoria_id, concepto)
    except ErrorServicio as e:
        return _redirigir("/registros/nuevo", e.mensaje)
    return _redirigir("/registros", mensaje, exito=True)


@router.get("/registros/{id_pago}/editar")
def form_editar_registro(id_pago: int, request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    try:
        return _formulario(request, usuario, "registros_editar.html", id_pago)
    except ErrorServicio:
        return RedirectResponse(url="/registros", status_code=303)


@router.post("/registros/{id_pago}/editar")
def procesar_editar_registro(id_pago: int, tipo: str = Form(...), monto: str = Form(...), fecha: str = Form(...),
                             categoria_id: str = Form(""), concepto: str = Form(...),
                             usuario: UsuarioActual = Depends(exigir_login)):
    try:
        mensaje = servicio.actualizar(usuario.usuario_id, usuario.es_admin, id_pago, tipo, monto, fecha,
                                      categoria_id, concepto)
    except ErrorServicio as e:
        if e.mensaje.startswith("No se pudo modificar"):
            return _redirigir("/registros", e.mensaje)
        return _redirigir(f"/registros/{id_pago}/editar", e.mensaje)
    return _redirigir("/registros", mensaje, exito=True)


@router.post("/registros/{id_pago}/eliminar")
def eliminar_registro(id_pago: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return _redirigir("/registros", servicio.eliminar(usuario.usuario_id, True, id_pago), exito=True)
