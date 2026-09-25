"""
Nota: esta ruta se llama "registros" en la URL (no "pagos") a propósito --
algunos antivirus (Kaspersky, con su función "Protección de pagos"/"Safe
Money") interceptan la navegación cuando detectan la palabra "pagos" en la
URL de un formulario con contraseña, y eso rompía el login y la navegación
en el navegador del usuario. La lógica de negocio y los procedimientos
almacenados (que sí se llaman *_tbpago*) no cambiaron, solo el nombre que
queda visible en la barra de direcciones.
"""
import csv
import io
from datetime import date
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse, StreamingResponse
from fastapi.templating import Jinja2Templates

from .. import repositorio
from ..dependencias import UsuarioActual, exigir_admin, exigir_login

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


@router.get("/registros")
def listar_registros(
    request: Request,
    usuario: UsuarioActual = Depends(exigir_login),
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    kwargs_filtros = {
        "fecha_desde": filtros["fecha_desde"],
        "fecha_hasta": filtros["fecha_hasta"],
        "concepto": filtros["concepto"],
        "monto_min": filtros["monto_min"],
        "monto_max": filtros["monto_max"],
    }
    # El admin ve los pagos de TODOS los usuarios (con el dueño de cada uno
    # visible en la tabla); un usuario normal solo ve los suyos, igual que
    # siempre.
    if usuario.es_admin:
        registros = repositorio.buscar_todos_los_pagos(**kwargs_filtros)
    else:
        registros = repositorio.buscar_pagos(usuario.usuario_id, **kwargs_filtros)
    total = sum(p["monto"] for p in registros)
    return templates.TemplateResponse(
        "registros_lista.html",
        {
            "request": request,
            "usuario": usuario,
            "registros": registros,
            "total": total,
            "filtros": {
                "concepto": concepto or "",
                "monto_min": monto_min or "",
                "monto_max": monto_max or "",
                "fecha_desde": fecha_desde or "",
                "fecha_hasta": fecha_hasta or "",
            },
        },
    )


@router.get("/registros/exportar.csv")
def exportar_csv(
    usuario: UsuarioActual = Depends(exigir_login),
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    kwargs_filtros = {
        "fecha_desde": filtros["fecha_desde"],
        "fecha_hasta": filtros["fecha_hasta"],
        "concepto": filtros["concepto"],
        "monto_min": filtros["monto_min"],
        "monto_max": filtros["monto_max"],
    }
    if usuario.es_admin:
        registros = repositorio.buscar_todos_los_pagos(**kwargs_filtros)
    else:
        registros = repositorio.buscar_pagos(usuario.usuario_id, **kwargs_filtros)

    buffer = io.StringIO()
    escritor = csv.writer(buffer)
    if usuario.es_admin:
        escritor.writerow(["ID", "Usuario", "Nombre", "Monto", "Fecha", "Concepto"])
        for p in registros:
            escritor.writerow([p["id_pago"], p["usuario"], p["nombre"], p["monto"], p["fecha"], p["concepto"]])
    else:
        escritor.writerow(["ID", "Monto", "Fecha", "Concepto"])
        for p in registros:
            escritor.writerow([p["id_pago"], p["monto"], p["fecha"], p["concepto"]])
    buffer.seek(0)

    return StreamingResponse(
        buffer,
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=registros.csv"},
    )


@router.get("/registros/nuevo")
def form_nuevo_registro(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    return templates.TemplateResponse(
        "registros_nuevo.html", {"request": request, "usuario": usuario, "error": None}
    )


@router.post("/registros/nuevo")
def crear_registro(
    monto: str = Form(...),
    fecha: str = Form(...),
    concepto: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    try:
        monto_val = float(monto)
    except ValueError:
        return RedirectResponse(
            url=f"/registros/nuevo?error={quote('Monto inválido')}", status_code=303
        )

    repositorio.insertar_pago(monto_val, date.fromisoformat(fecha), concepto, usuario.usuario_id)
    return RedirectResponse(url="/registros", status_code=303)


@router.get("/registros/{id_pago}/editar")
def form_editar_registro(
    id_pago: int, request: Request, usuario: UsuarioActual = Depends(exigir_login)
):
    # El admin puede abrir el formulario de editar el pago de cualquiera;
    # un usuario normal solo el suyo (obtener_pago_de_usuario ya filtra
    # por dueño).
    if usuario.es_admin:
        registro = repositorio.obtener_pago_por_id(id_pago)
    else:
        registro = repositorio.obtener_pago_de_usuario(id_pago, usuario.usuario_id)
    if registro is None:
        return RedirectResponse(url="/registros", status_code=303)
    return templates.TemplateResponse(
        "registros_editar.html", {"request": request, "usuario": usuario, "registro": registro, "error": None}
    )


@router.post("/registros/{id_pago}/editar")
def procesar_editar_registro(
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
            url=f"/registros/{id_pago}/editar?error={quote('Monto inválido')}", status_code=303
        )

    if usuario.es_admin:
        # Sin filtro de dueño -- ver actualizar_pago_admin en repositorio.py.
        exito = repositorio.actualizar_pago_admin(
            id_pago, monto_val, date.fromisoformat(fecha), concepto, usuario.usuario_id
        )
        filas = 1 if exito else 0
    else:
        # Misma verificacion que actualizar_tbpago: si el registro no es del
        # usuario logueado, el UPDATE no afecta filas -- no es un error de
        # SQL, es la forma en que el procedimiento impide modificar registros
        # ajenos.
        filas = repositorio.actualizar_pago(
            id_pago, monto_val, date.fromisoformat(fecha), concepto, usuario.usuario_id
        )
    if filas == 0:
        return RedirectResponse(
            url=f"/registros?error={quote('No autorizado')}", status_code=303
        )
    return RedirectResponse(url="/registros", status_code=303)


@router.post("/registros/{id_pago}/eliminar")
def eliminar_registro(id_pago: int, usuario: UsuarioActual = Depends(exigir_admin)):
    # Eliminar un pago es exclusivo del admin -- no existía ni siquiera en
    # la versión de escritorio. Un usuario normal ni ve este botón (ver
    # registros_lista.html) ni puede llamar a esta ruta (exigir_admin).
    repositorio.eliminar_pago(id_pago, usuario.usuario_id)
    return RedirectResponse(url="/registros", status_code=303)
