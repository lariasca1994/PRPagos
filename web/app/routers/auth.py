"""
Páginas de acceso: ingreso, registro y recuperación de contraseña. La lógica
está en servicios/acceso.py (la misma que usa la app de escritorio por la API);
aquí solo se muestran las pantallas y se guardan las cookies.

  1. /login         correo + contraseña.
  2a. /login/codigo     código TOTP de 6 dígitos (o uno de respaldo).
  2b. /login/configurar QR + primer código, si todavía no hay autenticador; al
                        activarlo se entregan 10 códigos de respaldo.
  Perdí mi teléfono:    /login/perdi-telefono envía un código al correo y
                        /login/correo lo verifica para configurar uno nuevo.
  Llave de acceso:      /login/llave (WebAuthn) reemplaza los dos pasos.
Entre un paso y otro solo existe la cookie "acceso" (JWT de 10 minutos, tipo
"parcial"); la cookie "sesion" se emite únicamente al final.
"""
import json
from urllib.parse import quote

from fastapi import APIRouter, Body, Form, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import llaves
from ..seguridad import (
    MENSAJES_CIERRE,
    MINUTOS_CODIGO_CORREO,
    crear_token,
    crear_token_parcial,
    descifrar_o_none,
    leer_token_parcial,
)
from ..servicios import ErrorServicio
from ..servicios import acceso

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

COOKIE_SESION = "sesion"
COOKIE_ACCESO = "acceso"
COOKIE_RETO = "reto_llave"


def _redirigir(ruta: str, mensaje: str | None = None, exito: bool = False) -> RedirectResponse:
    # Los acentos y la "ñ" se codifican: sin eso el navegador puede descartar
    # el query string completo y el mensaje nunca llega a mostrarse.
    if mensaje:
        clave = "ok" if exito else "error"
        ruta = f"{ruta}?{clave}={quote(mensaje)}"
    return RedirectResponse(url=ruta, status_code=303)


def _cookie(respuesta, nombre: str, valor: str, max_age: int) -> None:
    # secure=True: solo viaja por HTTPS (los navegadores la aceptan también en
    # http://localhost y http://127.0.0.1 para el desarrollo local).
    respuesta.set_cookie(nombre, valor, httponly=True, secure=True, samesite="lax", max_age=max_age)


def _abrir_sesion(cred: dict, respuesta=None):
    """Emite la sesión completa (por defecto, rumbo al dashboard)."""
    respuesta = respuesta or _redirigir("/dashboard")
    _cookie(respuesta, COOKIE_SESION, crear_token(cred["usuario_id"], cred["usuario"], cred["nombre"]), 8 * 3600)
    respuesta.delete_cookie(COOKIE_ACCESO)
    return respuesta


def _contexto(request: Request, **extra) -> dict:
    return {"request": request, "error": request.query_params.get("error"),
            "ok": request.query_params.get("ok"), **extra}


def _parcial(request: Request, etapa: str) -> dict | None:
    datos = leer_token_parcial(request.cookies.get(COOKIE_ACCESO))
    return datos if datos and datos.get("etapa") == etapa else None


def _hacia_paso(paso: acceso.PasoIngreso, mensaje: str | None = None) -> RedirectResponse:
    cred = paso.cred
    if paso.etapa == "codigo":
        respuesta = _redirigir("/login/codigo", mensaje, exito=True)
        token = crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "codigo")
    else:
        respuesta = _redirigir("/login/configurar", mensaje, exito=True)
        token = crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "configurar",
                                    secreto_pendiente=paso.secreto)
    _cookie(respuesta, COOKIE_ACCESO, token, 600)
    return respuesta


# ───────────────────────── Paso 1: contraseña ─────────────────────────


@router.get("/login")
def form_login(request: Request):
    return templates.TemplateResponse("login.html", _contexto(request))


@router.post("/login")
def procesar_login(usuario: str = Form(...), contrasena: str = Form(...)):
    try:
        return _hacia_paso(acceso.ingresar(usuario, contrasena))
    except ErrorServicio as e:
        return _redirigir("/login", e.mensaje)


# ───────────────────── Paso 2a: código del autenticador ─────────────────────


@router.get("/login/codigo")
def form_codigo(request: Request):
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    return templates.TemplateResponse("login_codigo.html", _contexto(request, correo=datos["usuario"]))


@router.post("/login/codigo")
def procesar_codigo(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    try:
        return _abrir_sesion(acceso.verificar_codigo(datos["usuario_id"], codigo))
    except ErrorServicio as e:
        destino = "/login" if e.mensaje in (acceso.MENSAJE_BLOQUEO, acceso.MENSAJE_EXPIRADO) else "/login/codigo"
        return _redirigir(destino, e.mensaje)


# ─────────────── Ingresar con una llave de acceso (WebAuthn) ───────────────


@router.post("/login/llave/opciones")
def opciones_llave():
    reto, token = llaves.crear_reto("ingreso")
    respuesta = JSONResponse(json.loads(llaves.opciones_ingreso(reto)))
    respuesta.set_cookie(COOKIE_RETO, token, httponly=True, secure=True, samesite="strict", max_age=300)
    return respuesta


@router.post("/login/llave")
def ingresar_con_llave(request: Request, credencial: dict = Body(...)):
    datos = llaves.leer_reto(request.cookies.get(COOKIE_RETO), "ingreso")
    if datos is None:
        return JSONResponse({"error": "La solicitud expiró; inténtalo de nuevo."}, status_code=400)
    try:
        cred = acceso.ingresar_con_llave(credencial, datos["reto"])
    except ErrorServicio as e:
        return JSONResponse({"error": e.mensaje}, status_code=400)
    respuesta = _abrir_sesion(cred, JSONResponse({"ir": "/dashboard"}))
    respuesta.delete_cookie(COOKIE_RETO)
    return respuesta


# ─────────────────── Paso 2b: configurar el autenticador ───────────────────


@router.get("/login/configurar")
def form_configurar(request: Request):
    datos = _parcial(request, "configurar")
    secreto = descifrar_o_none(datos.get("pendiente")) if datos else None
    if secreto is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    config = acceso.configuracion_autenticador(secreto, datos["usuario"])
    return templates.TemplateResponse(
        "login_configurar.html",
        _contexto(request, correo=datos["usuario"], qr=config["qr"], secreto=config["clave"]))


@router.post("/login/configurar")
def procesar_configurar(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "configurar")
    secreto = descifrar_o_none(datos.get("pendiente")) if datos else None
    if secreto is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    try:
        cred, codigos = acceso.activar_autenticador(datos["usuario_id"], secreto, codigo)
    except ErrorServicio as e:
        destino = "/login" if e.mensaje in (acceso.MENSAJE_BLOQUEO, acceso.MENSAJE_EXPIRADO) else "/login/configurar"
        return _redirigir(destino, e.mensaje)
    # Los códigos se muestran una sola vez, en esta misma respuesta.
    pantalla = templates.TemplateResponse("login_respaldo.html", _contexto(request, codigos=codigos))
    return _abrir_sesion(cred, pantalla)


# ─────────────────── Perdí mi teléfono (código por correo) ───────────────────


@router.post("/login/perdi-telefono")
def perdi_telefono(request: Request):
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    try:
        cred = acceso.perdi_telefono(datos["usuario_id"])
    except ErrorServicio as e:
        destino = "/login" if e.mensaje == acceso.MENSAJE_BLOQUEO else "/login/codigo"
        return _redirigir(destino, e.mensaje)
    respuesta = _redirigir("/login/correo")
    _cookie(respuesta, COOKIE_ACCESO,
            crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "correo"), 600)
    return respuesta


@router.get("/login/correo")
def form_codigo_correo(request: Request):
    datos = _parcial(request, "correo")
    if datos is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    return templates.TemplateResponse(
        "login_correo.html", _contexto(request, correo=datos["usuario"], minutos=MINUTOS_CODIGO_CORREO))


@router.post("/login/correo")
def procesar_codigo_correo(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "correo")
    if datos is None:
        return _redirigir("/login", acceso.MENSAJE_EXPIRADO)
    try:
        paso = acceso.verificar_codigo_correo(datos["usuario_id"], codigo)
    except ErrorServicio as e:
        destino = "/login" if e.mensaje == acceso.MENSAJE_BLOQUEO else "/login/correo"
        return _redirigir(destino, e.mensaje)
    return _hacia_paso(paso, "Código verificado: configura tu autenticador nuevo.")


@router.get("/logout")
def logout():
    respuesta = _redirigir("/login", MENSAJES_CIERRE["salir"], exito=True)
    respuesta.delete_cookie(COOKIE_SESION)
    respuesta.delete_cookie(COOKIE_ACCESO)
    return respuesta


# ───────────────────────── Registro y recuperación ─────────────────────────


@router.get("/registro")
def form_registro(request: Request):
    return templates.TemplateResponse("registro.html", _contexto(request))


@router.post("/registro")
def procesar_registro(usuario: str = Form(...), nombre: str = Form(...), contrasena: str = Form(...),
                      confirmacion: str = Form(...)):
    try:
        return _redirigir("/login", acceso.registrar(usuario, nombre, contrasena, confirmacion), exito=True)
    except ErrorServicio as e:
        return _redirigir("/registro", e.mensaje)


@router.get("/recuperar")
def form_recuperar(request: Request):
    return templates.TemplateResponse("recuperar.html", _contexto(request))


@router.post("/recuperar")
def procesar_recuperar(correo: str = Form(...), nombre_registrado: str = Form(...), codigo: str = Form(""),
                       nueva_contrasena: str = Form(...), confirmacion: str = Form(...)):
    try:
        mensaje = acceso.recuperar(correo, nombre_registrado, codigo, nueva_contrasena, confirmacion)
    except ErrorServicio as e:
        return _redirigir("/recuperar", e.mensaje)
    return _redirigir("/login", mensaje, exito=True)
