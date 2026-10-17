"""
Pantalla "Seguridad" (con la sesión abierta): cambiar la contraseña y
cambiar de autenticador. Las dos acciones piden un código TOTP vigente,
así que una sesión robada no alcanza para tomar la cuenta.
"""
from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import repositorio
from ..dependencias import UsuarioActual, exigir_login
from ..qr import qr_svg
from ..seguridad import (
    cifrar_secreto,
    crear_token_reconfiguracion,
    descifrar_o_none,
    generar_secreto_totp,
    hash_password,
    leer_token_reconfiguracion,
    problema_contrasena,
    secreto_legible,
    uri_totp,
    verificar_password,
    verificar_totp,
)

router = APIRouter(prefix="/seguridad")
templates = Jinja2Templates(directory="app/templates")

COOKIE_RECONFIGURAR = "reconfigurar"


def _redirigir(ruta: str, mensaje: str, exito: bool = False) -> RedirectResponse:
    clave = "ok" if exito else "error"
    return RedirectResponse(url=f"{ruta}?{clave}={quote(mensaje)}", status_code=303)


def _codigo_vigente(cred: dict, codigo: str) -> bool:
    """Verifica un código del autenticador ACTUAL y lo marca como usado."""
    secreto = descifrar_o_none(cred["totp_secreto"])
    paso = verificar_totp(secreto, codigo, cred["totp_ultimo_paso"]) if secreto else None
    if paso is None or not repositorio.registrar_acceso_exitoso(
        cred["usuario_id"], paso, "VERIFICA_TOTP"
    ):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_TOTP")
        return False
    return True


@router.get("")
def pantalla_seguridad(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    return templates.TemplateResponse(
        "seguridad.html",
        {
            "request": request,
            "usuario": usuario,
            "totp_activo": bool(cred and cred["totp_activo"]),
            "error": request.query_params.get("error"),
            "ok": request.query_params.get("ok"),
        },
    )


@router.post("/contrasena")
def cambiar_contrasena(
    actual: str = Form(...),
    nueva: str = Form(...),
    confirmacion: str = Form(...),
    codigo: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _redirigir("/seguridad", "Cuenta bloqueada temporalmente; espera 5 minutos")
    if not verificar_password(actual, cred["password"]):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CLAVE")
        return _redirigir("/seguridad", "La contraseña actual no es correcta")
    motivo = problema_contrasena(nueva)
    if motivo:
        return _redirigir("/seguridad", motivo)
    if nueva != confirmacion:
        return _redirigir("/seguridad", "Las contraseñas nuevas no coinciden")
    if not _codigo_vigente(cred, codigo):
        return _redirigir("/seguridad", "Código del autenticador incorrecto o ya usado")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva), "CAMBIO_CLAVE")
    return _redirigir("/seguridad", "Contraseña actualizada", exito=True)


@router.post("/autenticador")
def iniciar_cambio_autenticador(
    codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)
):
    """Paso 1 del cambio de autenticador: confirmar con el actual."""
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _redirigir("/seguridad", "Cuenta bloqueada temporalmente; espera 5 minutos")
    if not _codigo_vigente(cred, codigo):
        return _redirigir("/seguridad", "Código del autenticador incorrecto o ya usado")
    respuesta = RedirectResponse(url="/seguridad/autenticador", status_code=303)
    respuesta.set_cookie(
        COOKIE_RECONFIGURAR,
        crear_token_reconfiguracion(usuario.usuario_id, generar_secreto_totp()),
        httponly=True, secure=True, samesite="lax", max_age=600,
    )
    return respuesta


@router.get("/autenticador")
def form_autenticador_nuevo(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    secreto = leer_token_reconfiguracion(
        request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id
    )
    if secreto is None:
        return _redirigir("/seguridad", "El cambio de autenticador expiró; empieza de nuevo")
    return templates.TemplateResponse(
        "seguridad_autenticador.html",
        {
            "request": request,
            "usuario": usuario,
            "qr": qr_svg(uri_totp(secreto, usuario.usuario)),
            "secreto": secreto_legible(secreto),
            "error": request.query_params.get("error"),
        },
    )


@router.post("/autenticador/confirmar")
def confirmar_autenticador_nuevo(
    request: Request, codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)
):
    """Paso 2: el secreto nuevo solo se guarda si su primer código es válido."""
    secreto = leer_token_reconfiguracion(
        request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id
    )
    if secreto is None:
        return _redirigir("/seguridad", "El cambio de autenticador expiró; empieza de nuevo")
    paso = verificar_totp(secreto, codigo)
    if paso is None:
        repositorio.registrar_fallo_acceso(usuario.usuario_id, "FALLO_TOTP")
        return _redirigir("/seguridad/autenticador", "El código no coincide con el nuevo autenticador")
    repositorio.guardar_totp(usuario.usuario_id, cifrar_secreto(secreto), paso)
    respuesta = _redirigir("/seguridad", "Autenticador actualizado", exito=True)
    respuesta.delete_cookie(COOKIE_RECONFIGURAR)
    return respuesta
