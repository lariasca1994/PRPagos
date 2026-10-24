"""Página de gestión de cuentas (rol admin). La lógica está en servicios/usuarios.py."""
from urllib.parse import quote

from fastapi import APIRouter, Depends, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from ..dependencias import UsuarioActual, exigir_admin
from ..servicios import ErrorServicio
from ..servicios import usuarios as servicio

router = APIRouter(prefix="/admin/usuarios")
templates = Jinja2Templates(directory="app/templates")


def _resultado(funcion, *args) -> RedirectResponse:
    try:
        return RedirectResponse(url=f"/admin/usuarios?ok={quote(funcion(*args))}", status_code=303)
    except ErrorServicio as e:
        return RedirectResponse(url=f"/admin/usuarios?error={quote(e.mensaje)}", status_code=303)


@router.get("")
def listar(request: Request, usuario: UsuarioActual = Depends(exigir_admin)):
    return templates.TemplateResponse(request, "admin_usuarios.html",
                                      {"request": request, "usuario": usuario, "usuarios": servicio.listar()})


@router.post("/{usuario_id}/suspender")
def suspender(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return _resultado(servicio.suspender, usuario.usuario_id, usuario_id)


@router.post("/{usuario_id}/reactivar")
def reactivar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return _resultado(servicio.reactivar, usuario_id)


@router.post("/{usuario_id}/eliminar")
def eliminar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    return _resultado(servicio.eliminar, usuario.usuario_id, usuario_id)
