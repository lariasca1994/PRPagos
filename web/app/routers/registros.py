"""
Registros de ingresos y gastos: consultar (con filtros y totales), crear,
modificar, eliminar (admin) y exportar a CSV. Misma pantalla y mismas reglas
que escritorio/.../ui/PanelConsulta.java y PanelPago.java.

Nota: esta ruta se llama "registros" en la URL (no "pagos") a propósito --
algunos antivirus (Kaspersky, con su función "Protección de pagos"/"Safe
Money") interceptan la navegación cuando detectan la palabra "pagos" en la
URL de un formulario con contraseña, y eso rompía el login y la navegación.
"""
import csv
import io
from datetime import date
from urllib.parse import quote

import oracledb
from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse, StreamingResponse
from fastapi.templating import Jinja2Templates

from .. import finanzas, repositorio
from ..dependencias import UsuarioActual, exigir_admin, exigir_login

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

TIPOS = {"GASTO": "Gasto", "INGRESO": "Ingreso"}


def _redirigir(ruta: str, mensaje: str, exito: bool = False) -> RedirectResponse:
    clave = "ok" if exito else "error"
    separador = "&" if "?" in ruta else "?"
    return RedirectResponse(url=f"{ruta}{separador}{clave}={quote(mensaje)}", status_code=303)


def _parsear_filtros(tipo, concepto, monto_min, monto_max, fecha_desde, fecha_hasta) -> dict:
    """Campo vacío = sin filtro; un monto inválido se ignora (igual que el escritorio)."""
    def _monto(valor):
        valor = (valor or "").strip().replace(",", ".")
        try:
            return float(valor) if valor else None
        except ValueError:
            return None

    def _fecha(valor):
        valor = (valor or "").strip()
        try:
            return date.fromisoformat(valor) if valor else None
        except ValueError:
            return None

    return {
        "tipo": tipo if tipo in TIPOS else None,
        "concepto": (concepto or "").strip() or None,
        "monto_min": _monto(monto_min),
        "monto_max": _monto(monto_max),
        "fecha_desde": _fecha(fecha_desde),
        "fecha_hasta": _fecha(fecha_hasta),
    }


def _monto_formulario(texto: str) -> float | None:
    """Coma o punto decimal, igual que la versión de escritorio. Debe ser > 0."""
    try:
        valor = float(texto.strip().replace(",", "."))
    except ValueError:
        return None
    return valor if valor > 0 else None


def _categoria_formulario(texto: str) -> int | None:
    texto = (texto or "").strip()
    return int(texto) if texto.isdigit() else None


@router.get("/registros")
def listar_registros(
    request: Request,
    usuario: UsuarioActual = Depends(exigir_login),
    tipo: str | None = None,
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(tipo, concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    registros = repositorio.buscar_registros(None if usuario.es_admin else usuario.usuario_id, filtros)
    ingresos = sum(r["monto"] for r in registros if r["tipo"] == "INGRESO")
    gastos = sum(r["monto"] for r in registros if r["tipo"] == "GASTO")
    return templates.TemplateResponse(
        "registros_lista.html",
        {
            "request": request,
            "usuario": usuario,
            "registros": registros,
            "tipos": TIPOS,
            "ingresos": ingresos,
            "gastos": gastos,
            "balance": ingresos - gastos,
            "filtros": {
                "tipo": filtros["tipo"] or "",
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
    tipo: str | None = None,
    concepto: str | None = None,
    monto_min: str | None = None,
    monto_max: str | None = None,
    fecha_desde: str | None = None,
    fecha_hasta: str | None = None,
):
    filtros = _parsear_filtros(tipo, concepto, monto_min, monto_max, fecha_desde, fecha_hasta)
    registros = repositorio.buscar_registros(None if usuario.es_admin else usuario.usuario_id, filtros)
    buffer = io.StringIO()
    escritor = csv.writer(buffer)
    if usuario.es_admin:
        escritor.writerow(["ID", "Usuario", "Nombre", "Tipo", "Monto", "Fecha", "Categoría", "Concepto"])
        for r in registros:
            escritor.writerow([r["id_pago"], r["usuario"], r["nombre"], TIPOS[r["tipo"]], r["monto"],
                               r["fecha"], r["categoria"], r["concepto"]])
    else:
        escritor.writerow(["ID", "Tipo", "Monto", "Fecha", "Categoría", "Concepto"])
        for r in registros:
            escritor.writerow([r["id_pago"], TIPOS[r["tipo"]], r["monto"], r["fecha"], r["categoria"], r["concepto"]])
    buffer.seek(0)
    return StreamingResponse(
        buffer,
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=registros.csv"},
    )


def _formulario(request: Request, usuario: UsuarioActual, plantilla: str, registro: dict | None, dueno_id: int):
    return templates.TemplateResponse(
        plantilla,
        {
            "request": request,
            "usuario": usuario,
            "registro": registro,
            "tipos": TIPOS,
            "categorias": repositorio.categorias(dueno_id),
            "hoy": finanzas.hoy().isoformat(),
            "error": request.query_params.get("error"),
        },
    )


@router.get("/registros/nuevo")
def form_nuevo_registro(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    return _formulario(request, usuario, "registros_nuevo.html", None, usuario.usuario_id)


def _validar(tipo: str, monto: str, fecha: str, concepto: str) -> tuple[str | None, float | None, date | None]:
    if tipo not in TIPOS:
        return "Elige si es un ingreso o un gasto", None, None
    valor = _monto_formulario(monto)
    if valor is None:
        return "Monto inválido", None, None
    try:
        dia = date.fromisoformat(fecha)
    except ValueError:
        return "Fecha inválida", None, None
    if not concepto.strip():
        return "Escribe el concepto", None, None
    return None, valor, dia


@router.post("/registros/nuevo")
def crear_registro(
    tipo: str = Form(...),
    monto: str = Form(...),
    fecha: str = Form(...),
    categoria_id: str = Form(""),
    concepto: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    error, valor, dia = _validar(tipo, monto, fecha, concepto)
    if error:
        return _redirigir("/registros/nuevo", error)
    try:
        repositorio.insertar_registro(tipo, valor, dia, concepto.strip(), _categoria_formulario(categoria_id),
                                      usuario.usuario_id)
    except oracledb.DatabaseError:
        return _redirigir("/registros/nuevo", "Categoría no válida para este registro")
    return _redirigir("/registros", "Registro guardado", exito=True)


@router.get("/registros/{id_pago}/editar")
def form_editar_registro(id_pago: int, request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    registro = repositorio.obtener_registro(id_pago, None if usuario.es_admin else usuario.usuario_id)
    if registro is None:
        return RedirectResponse(url="/registros", status_code=303)
    dueno = repositorio.dueno_registro(id_pago) if usuario.es_admin else usuario.usuario_id
    return _formulario(request, usuario, "registros_editar.html", registro, dueno)


@router.post("/registros/{id_pago}/editar")
def procesar_editar_registro(
    id_pago: int,
    tipo: str = Form(...),
    monto: str = Form(...),
    fecha: str = Form(...),
    categoria_id: str = Form(""),
    concepto: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    error, valor, dia = _validar(tipo, monto, fecha, concepto)
    if error:
        return _redirigir(f"/registros/{id_pago}/editar", error)
    categoria = _categoria_formulario(categoria_id)
    try:
        if usuario.es_admin:
            filas = 1 if repositorio.actualizar_registro_admin(
                id_pago, tipo, valor, dia, concepto.strip(), categoria, usuario.usuario_id) else 0
        else:
            filas = repositorio.actualizar_registro(
                id_pago, tipo, valor, dia, concepto.strip(), categoria, usuario.usuario_id)
    except oracledb.DatabaseError:
        return _redirigir(f"/registros/{id_pago}/editar", "Categoría no válida para este registro")
    if filas == 0:
        return _redirigir("/registros", "No se pudo modificar: este registro no te pertenece.")
    return _redirigir("/registros", f"Registro #{id_pago} actualizado", exito=True)


@router.post("/registros/{id_pago}/eliminar")
def eliminar_registro(id_pago: int, usuario: UsuarioActual = Depends(exigir_admin)):
    repositorio.eliminar_pago(id_pago, usuario.usuario_id)
    return _redirigir("/registros", f"Registro #{id_pago} eliminado", exito=True)
