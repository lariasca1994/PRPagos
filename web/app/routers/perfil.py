"""
Pantalla "Perfil" (con la sesión abierta): tus datos y los resúmenes por
correo, el plan 50/30/20, tus categorías, la contraseña, el autenticador y
los códigos de respaldo. Las acciones de seguridad piden un código vigente
(de la app autenticadora o uno de respaldo), así que una sesión robada no
alcanza para tomar la cuenta. Igual que escritorio/.../ui/PanelPerfil.java.
"""
import json
from urllib.parse import quote

import oracledb
from fastapi import APIRouter, Body, Depends, Form, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import finanzas, llaves, repositorio
from ..dependencias import UsuarioActual, exigir_login
from ..qr import qr_svg
from ..seguridad import (
    cifrar_secreto,
    crear_token_reconfiguracion,
    generar_codigos_respaldo,
    generar_secreto_totp,
    hash_corto,
    hash_password,
    leer_token_reconfiguracion,
    problema_contrasena,
    secreto_legible,
    uri_totp,
    verificar_password,
    verificar_totp,
)
from .verificacion import verificar_segundo_factor

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

COOKIE_RECONFIGURAR = "reconfigurar"
GRUPOS = {"NECESIDADES": "Necesidades", "DESEOS": "Deseos", "AHORRO": "Ahorro y deudas"}


def _redirigir(mensaje: str, exito: bool = False, ancla: str = "") -> RedirectResponse:
    clave = "ok" if exito else "error"
    return RedirectResponse(url=f"/perfil?{clave}={quote(mensaje)}{ancla}", status_code=303)


@router.get("/seguridad")
def seguridad_antigua():
    return RedirectResponse(url="/perfil", status_code=303)


@router.get("/perfil")
def pantalla_perfil(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    return templates.TemplateResponse(
        "perfil.html",
        {
            "request": request,
            "usuario": usuario,
            "perfil": repositorio.perfil(usuario.usuario_id),
            "totp_activo": bool(cred and cred["totp_activo"]),
            "pcts": repositorio.plan(usuario.usuario_id),
            "categorias": repositorio.categorias(usuario.usuario_id),
            "llaves": repositorio.llaves(usuario.usuario_id),
            "grupos": GRUPOS,
            "texto_pct": finanzas.texto_pct,
            "error": request.query_params.get("error"),
            "ok": request.query_params.get("ok"),
        },
    )


@router.post("/perfil/datos")
def guardar_datos(
    nombre: str = Form(...), notificaciones: str = Form(""), usuario: UsuarioActual = Depends(exigir_login)
):
    nombre = nombre.strip()
    if not nombre:
        return _redirigir("Escribe tu nombre completo")
    repositorio.guardar_datos_perfil(usuario.usuario_id, nombre[:100], notificaciones == "1")
    return _redirigir("Datos guardados", exito=True)


@router.post("/perfil/plan")
def guardar_plan(
    necesidades: str = Form(...), deseos: str = Form(...), ahorro: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    try:
        valores = [round(float(v.strip().replace(",", ".")), 2) for v in (necesidades, deseos, ahorro)]
    except ValueError:
        return _redirigir("Los porcentajes deben ser números")
    if any(v < 0 or v > 100 for v in valores) or round(sum(valores), 2) != 100:
        return _redirigir("Los tres porcentajes deben sumar 100")
    repositorio.guardar_plan(usuario.usuario_id, *valores)
    return _redirigir("Plan actualizado", exito=True)


@router.post("/perfil/categorias")
def crear_categoria(
    nombre: str = Form(...), tipo: str = Form(...), grupo: str = Form(""),
    usuario: UsuarioActual = Depends(exigir_login),
):
    nombre = nombre.strip()[:60]
    if not nombre:
        return _redirigir("Escribe el nombre de la categoría")
    if tipo not in ("INGRESO", "GASTO"):
        return _redirigir("Elige si la categoría es de ingresos o de gastos")
    if tipo == "GASTO" and grupo not in GRUPOS:
        return _redirigir("Elige el grupo del plan para la categoría de gastos")
    if not repositorio.crear_categoria(usuario.usuario_id, nombre, tipo, grupo if tipo == "GASTO" else None):
        return _redirigir("Ya tienes una categoría con ese nombre")
    return _redirigir("Categoría creada", exito=True)


@router.post("/perfil/categorias/{categoria_id}/eliminar")
def eliminar_categoria(categoria_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    motivo = repositorio.eliminar_categoria(usuario.usuario_id, categoria_id)
    return _redirigir(motivo) if motivo else _redirigir("Categoría eliminada", exito=True)


@router.post("/perfil/contrasena")
def cambiar_contrasena(
    actual: str = Form(...), nueva: str = Form(...), confirmacion: str = Form(...), codigo: str = Form(...),
    usuario: UsuarioActual = Depends(exigir_login),
):
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _redirigir("Cuenta bloqueada temporalmente; espera 5 minutos")
    if not verificar_password(actual, cred["password"]):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CLAVE")
        return _redirigir("La contraseña actual no es correcta")
    motivo = problema_contrasena(nueva)
    if motivo:
        return _redirigir(motivo)
    if nueva != confirmacion:
        return _redirigir("Las contraseñas nuevas no coinciden")
    if not verificar_segundo_factor(cred, codigo, "VERIFICA_TOTP"):
        return _redirigir("Código incorrecto o ya usado")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva), "CAMBIO_CLAVE")
    return _redirigir("Contraseña actualizada", exito=True)


def _pantalla_codigos(request: Request, usuario: UsuarioActual, codigos: list[str]):
    return templates.TemplateResponse(
        "perfil_respaldo.html", {"request": request, "usuario": usuario, "codigos": codigos}
    )


def _nuevos_codigos(usuario_id: int) -> list[str]:
    codigos = generar_codigos_respaldo()
    repositorio.reemplazar_codigos_respaldo(usuario_id, [hash_corto(c.replace("-", "")) for c in codigos])
    return codigos


@router.post("/perfil/respaldo")
def regenerar_codigos(request: Request, codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _redirigir("Cuenta bloqueada temporalmente; espera 5 minutos")
    if not verificar_segundo_factor(cred, codigo, "VERIFICA_TOTP"):
        return _redirigir("Código incorrecto o ya usado")
    return _pantalla_codigos(request, usuario, _nuevos_codigos(usuario.usuario_id))


@router.post("/perfil/autenticador")
def iniciar_cambio_autenticador(codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    """Paso 1 del cambio (o restablecimiento) del autenticador: confirmar con
    un código del actual o con uno de respaldo."""
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _redirigir("Cuenta bloqueada temporalmente; espera 5 minutos")
    if not verificar_segundo_factor(cred, codigo, "VERIFICA_TOTP"):
        return _redirigir("Código incorrecto o ya usado")
    respuesta = RedirectResponse(url="/perfil/autenticador", status_code=303)
    respuesta.set_cookie(
        COOKIE_RECONFIGURAR,
        crear_token_reconfiguracion(usuario.usuario_id, generar_secreto_totp()),
        httponly=True, secure=True, samesite="lax", max_age=600,
    )
    return respuesta


@router.get("/perfil/autenticador")
def form_autenticador_nuevo(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    secreto = leer_token_reconfiguracion(request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id)
    if secreto is None:
        return _redirigir("El cambio de autenticador expiró; empieza de nuevo")
    return templates.TemplateResponse(
        "perfil_autenticador.html",
        {
            "request": request,
            "usuario": usuario,
            "qr": qr_svg(uri_totp(secreto, usuario.usuario)),
            "secreto": secreto_legible(secreto),
            "error": request.query_params.get("error"),
        },
    )


@router.post("/perfil/autenticador/confirmar")
def confirmar_autenticador_nuevo(
    request: Request, codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)
):
    """Paso 2: el secreto nuevo solo se guarda si su primer código es válido.
    Con el autenticador nuevo se entregan códigos de respaldo nuevos."""
    secreto = leer_token_reconfiguracion(request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id)
    if secreto is None:
        return _redirigir("El cambio de autenticador expiró; empieza de nuevo")
    paso = verificar_totp(secreto, codigo)
    if paso is None:
        repositorio.registrar_fallo_acceso(usuario.usuario_id, "FALLO_TOTP")
        return RedirectResponse(
            url=f"/perfil/autenticador?error={quote('El código no coincide con el nuevo autenticador')}",
            status_code=303,
        )
    repositorio.guardar_totp(usuario.usuario_id, cifrar_secreto(secreto), paso)
    respuesta = _pantalla_codigos(request, usuario, _nuevos_codigos(usuario.usuario_id))
    respuesta.delete_cookie(COOKIE_RECONFIGURAR)
    return respuesta


# ───────────────────────── Llaves de acceso ─────────────────────────
#
# Agregar una llave pide un código de la app o de respaldo (como los demás
# cambios de seguridad). Mismas reglas y textos que PanelPerfil.java.

COOKIE_RETO = "reto_llave"


def _error(mensaje: str) -> JSONResponse:
    return JSONResponse({"error": mensaje}, status_code=400)


@router.post("/perfil/llaves/opciones")
def opciones_llave_nueva(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    nombre = llaves.nombre_valido(str(datos.get("nombre", "")))
    if nombre is None:
        return _error("Escribe un nombre para este dispositivo")
    cred = repositorio.obtener_credenciales(usuario_id=usuario.usuario_id)
    if cred is None or cred["bloqueada"]:
        return _error("Cuenta bloqueada temporalmente; espera 5 minutos")
    if not verificar_segundo_factor(cred, str(datos.get("codigo", "")), "VERIFICA_TOTP"):
        return _error("Código incorrecto o ya usado")
    existentes = [llave["credencial_id"] for llave in repositorio.llaves(usuario.usuario_id)]
    reto, token = llaves.crear_reto("registro", usuario.usuario_id, nombre)
    opciones = llaves.opciones_registro(usuario.usuario_id, cred["usuario"], cred["nombre"], existentes, reto)
    respuesta = JSONResponse(json.loads(opciones))
    respuesta.set_cookie(COOKIE_RETO, token, httponly=True, secure=True, samesite="strict", max_age=300)
    return respuesta


@router.post("/perfil/llaves/registrar")
def registrar_llave(request: Request, credencial: dict = Body(...),
                    usuario: UsuarioActual = Depends(exigir_login)):
    datos = llaves.leer_reto(request.cookies.get(COOKIE_RETO), "registro")
    if datos is None or datos.get("usuario_id") != usuario.usuario_id:
        return _error("La solicitud expiró; empieza de nuevo.")
    try:
        credencial_id, clave_publica, contador = llaves.verificar_registro(credencial, datos["reto"])
    except llaves.ErrorLlave as error:
        return _error(str(error))
    try:
        repositorio.guardar_llave(usuario.usuario_id, credencial_id, clave_publica, contador, datos["nombre"])
    except oracledb.IntegrityError:
        return _error("Esa llave de acceso ya está registrada")
    respuesta = JSONResponse({"ir": f"/perfil?ok={quote('Llave de acceso guardada')}#llaves"})
    respuesta.delete_cookie(COOKIE_RETO)
    return respuesta


@router.post("/perfil/llaves/{llave_id}/eliminar")
def eliminar_llave(llave_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    repositorio.eliminar_llave(usuario.usuario_id, llave_id)
    return _redirigir("Llave de acceso eliminada", exito=True, ancla="#llaves")
