"""
Gestión de cuentas para el admin: listar, suspender, reactivar, eliminar.
Estas rutas nunca existieron en la versión de escritorio -- son
exclusivas del panel web, igual que en el resto del portafolio.
"""
from urllib.parse import quote

from fastapi import APIRouter, Depends, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import repositorio
from ..dependencias import UsuarioActual, exigir_admin

router = APIRouter(prefix="/admin/usuarios")
templates = Jinja2Templates(directory="app/templates")


@router.get("")
def listar(request: Request, usuario: UsuarioActual = Depends(exigir_admin)):
    usuarios = repositorio.listar_usuarios()
    return templates.TemplateResponse(
        "admin_usuarios.html",
        {"request": request, "usuario": usuario, "usuarios": usuarios},
    )


@router.post("/{usuario_id}/suspender")
def suspender(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    # Un admin no puede suspender su propia cuenta -- se quedaría afuera
    # del panel sin que nadie más pueda reactivarlo. Mismo resguardo que
    # ColombiaTech2 / gestor-casos-qa / taskflow / verificador-api.
    if usuario_id == usuario.usuario_id:
        return RedirectResponse(
            url=f"/admin/usuarios?error={quote('No podés suspender tu propia cuenta')}",
            status_code=303,
        )
    repositorio.suspender_usuario(usuario_id)
    return RedirectResponse(url="/admin/usuarios", status_code=303)


@router.post("/{usuario_id}/reactivar")
def reactivar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    repositorio.reactivar_usuario(usuario_id)
    return RedirectResponse(url="/admin/usuarios", status_code=303)


@router.post("/{usuario_id}/eliminar")
def eliminar(usuario_id: int, usuario: UsuarioActual = Depends(exigir_admin)):
    if usuario_id == usuario.usuario_id:
        return RedirectResponse(
            url=f"/admin/usuarios?error={quote('No podés eliminar tu propia cuenta')}",
            status_code=303,
        )
    exito, motivo = repositorio.eliminar_usuario(usuario_id)
    if not exito:
        return RedirectResponse(url=f"/admin/usuarios?error={quote(motivo)}", status_code=303)
    return RedirectResponse(url="/admin/usuarios", status_code=303)
