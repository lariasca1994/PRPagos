"""
Página "Perfil". La lógica (datos, plan, categorías, contraseña, autenticador,
códigos de respaldo y llaves de acceso) está en servicios/perfil.py, la misma
que usa el escritorio por la API.
"""
import json
from urllib.parse import quote

from fastapi import APIRouter, Body, Depends, Form, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

from ..dependencias import UsuarioActual, exigir_login
from ..seguridad import crear_token_reconfiguracion, leer_token_reconfiguracion
from ..servicios import ErrorServicio
from ..servicios import acceso
from ..servicios import perfil as servicio

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

COOKIE_RECONFIGURAR = "reconfigurar"
COOKIE_RETO = "reto_llave"


def _redirigir(mensaje: str, exito: bool = False, ancla: str = "") -> RedirectResponse:
    clave = "ok" if exito else "error"
    return RedirectResponse(url=f"/perfil?{clave}={quote(mensaje)}{ancla}", status_code=303)


def _accion(funcion, *args, ancla: str = ""):
    try:
        return _redirigir(funcion(*args), exito=True, ancla=ancla)
    except ErrorServicio as e:
        return _redirigir(e.mensaje, ancla=ancla)


@router.get("/seguridad")
def seguridad_antigua():
    return RedirectResponse(url="/perfil", status_code=303)


@router.get("/perfil")
def pantalla_perfil(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    return templates.TemplateResponse("perfil.html", {
        "request": request, "usuario": usuario, **servicio.datos(usuario.usuario_id),
        "error": request.query_params.get("error"), "ok": request.query_params.get("ok"),
    })


@router.post("/perfil/datos")
def guardar_datos(nombre: str = Form(...), notificaciones: str = Form(""),
                  usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.guardar_datos, usuario.usuario_id, nombre, notificaciones == "1")


@router.post("/perfil/plan")
def guardar_plan(necesidades: str = Form(...), deseos: str = Form(...), ahorro: str = Form(...),
                 usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.guardar_plan, usuario.usuario_id, necesidades, deseos, ahorro)


@router.post("/perfil/categorias")
def crear_categoria(nombre: str = Form(...), tipo: str = Form(...), grupo: str = Form(""),
                    usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.crear_categoria, usuario.usuario_id, nombre, tipo, grupo)


@router.post("/perfil/categorias/{categoria_id}/eliminar")
def eliminar_categoria(categoria_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.eliminar_categoria, usuario.usuario_id, categoria_id)


@router.post("/perfil/contrasena")
def cambiar_contrasena(actual: str = Form(...), nueva: str = Form(...), confirmacion: str = Form(...),
                       codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.cambiar_contrasena, usuario.usuario_id, actual, nueva, confirmacion, codigo)


def _pantalla_codigos(request: Request, usuario: UsuarioActual, codigos: list[str]):
    return templates.TemplateResponse("perfil_respaldo.html",
                                      {"request": request, "usuario": usuario, "codigos": codigos})


@router.post("/perfil/respaldo")
def regenerar_codigos(request: Request, codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    try:
        return _pantalla_codigos(request, usuario, servicio.regenerar_codigos(usuario.usuario_id, codigo))
    except ErrorServicio as e:
        return _redirigir(e.mensaje)


@router.post("/perfil/autenticador")
def iniciar_cambio_autenticador(codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    try:
        secreto = servicio.iniciar_cambio_autenticador(usuario.usuario_id, codigo)
    except ErrorServicio as e:
        return _redirigir(e.mensaje)
    respuesta = RedirectResponse(url="/perfil/autenticador", status_code=303)
    respuesta.set_cookie(COOKIE_RECONFIGURAR, crear_token_reconfiguracion(usuario.usuario_id, secreto),
                         httponly=True, secure=True, samesite="lax", max_age=600)
    return respuesta


@router.get("/perfil/autenticador")
def form_autenticador_nuevo(request: Request, usuario: UsuarioActual = Depends(exigir_login)):
    secreto = leer_token_reconfiguracion(request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id)
    if secreto is None:
        return _redirigir("El cambio de autenticador expiró; empieza de nuevo")
    config = acceso.configuracion_autenticador(secreto, usuario.usuario)
    return templates.TemplateResponse("perfil_autenticador.html", {
        "request": request, "usuario": usuario, "qr": config["qr"], "secreto": config["clave"],
        "error": request.query_params.get("error"),
    })


@router.post("/perfil/autenticador/confirmar")
def confirmar_autenticador_nuevo(request: Request, codigo: str = Form(...),
                                 usuario: UsuarioActual = Depends(exigir_login)):
    secreto = leer_token_reconfiguracion(request.cookies.get(COOKIE_RECONFIGURAR), usuario.usuario_id)
    if secreto is None:
        return _redirigir("El cambio de autenticador expiró; empieza de nuevo")
    try:
        codigos = servicio.confirmar_autenticador(usuario.usuario_id, secreto, codigo)
    except ErrorServicio as e:
        return RedirectResponse(url=f"/perfil/autenticador?error={quote(e.mensaje)}", status_code=303)
    respuesta = _pantalla_codigos(request, usuario, codigos)
    respuesta.delete_cookie(COOKIE_RECONFIGURAR)
    return respuesta


# ───────────────────── Verificación en dos pasos ─────────────────────


@router.post("/perfil/dos-pasos/codigo")
def codigo_perfil(usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.enviar_codigo_perfil, usuario.usuario_id, ancla="#dos-pasos")


@router.post("/perfil/dos-pasos/correo")
def agregar_correo(usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.iniciar_agregar_correo, usuario.usuario_id, ancla="#dos-pasos")


@router.post("/perfil/dos-pasos/correo/confirmar")
def confirmar_correo(codigo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.confirmar_correo, usuario.usuario_id, codigo, ancla="#dos-pasos")


@router.post("/perfil/dos-pasos/preferido")
def elegir_preferido(metodo: str = Form(...), usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.elegir_preferido, usuario.usuario_id, metodo, ancla="#dos-pasos")


# ───────────────────────── Llaves de acceso ─────────────────────────


@router.post("/perfil/llaves/opciones")
def opciones_llave_nueva(datos: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    try:
        opciones, token = servicio.opciones_llave(usuario.usuario_id, str(datos.get("nombre", "")),
                                                  str(datos.get("codigo", "")))
    except ErrorServicio as e:
        return JSONResponse({"error": e.mensaje}, status_code=400)
    respuesta = JSONResponse(json.loads(opciones))
    respuesta.set_cookie(COOKIE_RETO, token, httponly=True, secure=True, samesite="strict", max_age=300)
    return respuesta


@router.post("/perfil/llaves/registrar")
def registrar_llave(request: Request, credencial: dict = Body(...), usuario: UsuarioActual = Depends(exigir_login)):
    try:
        mensaje = servicio.registrar_llave(usuario.usuario_id, request.cookies.get(COOKIE_RETO), credencial)
    except ErrorServicio as e:
        return JSONResponse({"error": e.mensaje}, status_code=400)
    respuesta = JSONResponse({"ir": f"/perfil?ok={quote(mensaje)}#llaves"})
    respuesta.delete_cookie(COOKIE_RETO)
    return respuesta


@router.post("/perfil/llaves/{llave_id}/eliminar")
def eliminar_llave(llave_id: int, usuario: UsuarioActual = Depends(exigir_login)):
    return _accion(servicio.eliminar_llave, usuario.usuario_id, llave_id, ancla="#llaves")
