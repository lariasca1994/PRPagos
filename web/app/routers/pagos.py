import csv
import io
from datetime import date
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse, StreamingResponse
from fastapi.templating import Jinja2Templates

from .. import repositorio
from ..dependencias import UsuarioActual, exigir_login

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")


def _parsear_filtros(
    concepto: str | None,
    monto_min: str | None,
    monto_max: str | None,
    fecha_desde: str | None,
    fecha_hasta: str | None,
):
    """Misma logica que buscarConFiltros() en VentanaConsultaPagos.java:
    campo vacio = sin filtro, y un monto invalido se ignora en vez de
    reventar la busqueda."""
    def _monto(valor):
        valor = (valor or "").strip()
        if not valor:
            return None
        try:
            return float(valor)
        except ValueError:
            return None

    def _fecha(valor):
        valor = (valor or "").strip()
        return date.fromisoformat(valor) if valor else None

    return {
        "concepto": (concepto or "").strip() or None,
        "monto_min": _monto(monto_min),
        "monto_max": _monto(monto_max),
        "fecha_desde": _fecha(fecha_desde),
        "fecha_hasta": _fecha(fecha_hasta),
    }


@router.get("/pagos")
def listar_pagos(
    request: Request,
    usuario: UsuarioActual = Depends(exigir_login),
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    pagos = repositorio.buscar_pagos(usuario.usuario_id, **{
        "fecha_desde": filtros["fecha_desde"],
        "fecha_hasta": filtros["fecha_hasta"],
        "concepto": filtros["concepto"],
        "monto_min": filtros["monto_min"],
        "monto_max": filtros["monto_max"],
    })
    total_pagado = sum(p["monto"] for p in pagos)
    return templates.TemplateResponse(
        "pagos_lista.html",
        {
            "request": request,
            "usuario": usuario,
            "pagos": pagos,
            "total_pagado": total_pagado,
            "filtros": {
                "concepto": concepto or "",
                "monto_min": monto_min or "",
                "monto_max": monto_max or "",
                "fecha_desde": fecha_desde or "",
                "fecha_hasta": fecha_hasta or "",
            },
        },
    )


@router.get("/pagos/exportar.csv")
def exportar_csv(
    usuario: UsuarioActual = Depends(exigir_login),
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    pagos = repositorio.buscar_pagos(usuario.usuario_id, **{
        "fecha_desde": filtros["fecha_desde"],
        "fecha_hasta": filtros["fecha_hasta"],
        "concepto": filtros["concepto"],
        "monto_min": filtros["monto_min"],
        "monto_max": filtros["monto_max"],
    })

    buffer = io.StringIO()
    escritor = csv.writer(buffer)
    escritor.writerow(["ID Pago", "Monto", "Fecha", "Concepto"])
    for p in pagos:
        escritor.writerow([p["id_pago"], p["monto"], p["fecha"], p["concepto"]])
    buffer.seek(0)

    return StreamingResponse(
        buffer,
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=pagos.csv"},
    )


@router.get("/pagos/nuevo")
def form_nuevo_pago(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    return templates.TemplateResponse(
        "pagos_nuevo.html", {"request": request, "usuario": usuario, "error": None}
    )


@router.post("/pagos/nuevo")
def crear_pago(
    monto: str = Form(...),
    fecha: str = Form(...),
    concepto: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    try:
        monto_val = float(monto)
    except ValueError:
        return RedirectResponse(
            url=f"/pagos/nuevo?error={quote('Monto inválido')}", status_code=303
        )

    repositorio.insertar_pago(monto_val, date.fromisoformat(fecha), concepto, usuario.usuario_id)
    return RedirectResponse(url="/pagos", status_code=303)


@router.get("/pagos/{id_pago}/editar")
def form_editar_pago(
    id_pago: int, request: Request, usuario: UsuarioActual = Depends(exigir_login)
):
    pago = repositorio.obtener_pago_de_usuario(id_pago, usuario.usuario_id)
    if pago is None:
        return RedirectResponse(url="/pagos", status_code=303)
    return templates.TemplateResponse(
        "pagos_editar.html", {"request": request, "usuario": usuario, "pago": pago, "error": None}
    )


@router.post("/pagos/{id_pago}/editar")
def procesar_editar_pago(
    id_pago: int,
    monto: str = Form(...),
    fecha: str = Form(...),
    concepto: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    try:
        monto_val = float(monto)
    except ValueError:
        return RedirectResponse(
            url=f"/pagos/{id_pago}/editar?error={quote('Monto inválido')}", status_code=303
        )

    # Misma verificacion que actualizar_tbpago: si el pago no es del
    # usuario logueado, el UPDATE no afecta filas -- no es un error de
    # SQL, es la forma en que el procedimiento impide modificar pagos
    # ajenos.
    filas = repositorio.actualizar_pago(
        id_pago, monto_val, date.fromisoformat(fecha), concepto, usuario.usuario_id
    )
    if filas == 0:
        return RedirectResponse(url="/pagos?error=No+autorizado", status_code=303)
    return RedirectResponse(url="/pagos", status_code=303)
