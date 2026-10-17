"""
Ingreso, registro y recuperación de contraseña.

El ingreso tiene dos pasos y la verificación en dos pasos es obligatoria:
  1. /login         correo + contraseña (PBKDF2; las cuentas con el SHA-256
                    de antes se actualizan solas aquí).
  2a. /login/codigo     código TOTP de 6 dígitos (o uno de respaldo), si la
                        cuenta ya tiene autenticador.
  2b. /login/configurar QR + primer código, si todavía no lo tiene; al
                        activarlo se entregan 10 códigos de respaldo.
  Perdí mi teléfono:    /login/perdi-telefono envía un código al correo
                        (solo después de la contraseña) y /login/correo lo
                        verifica para configurar un autenticador nuevo.
Entre un paso y otro solo existe la cookie "acceso" (JWT de 10 minutos, tipo
"parcial"); la cookie "sesion" se emite únicamente al final del paso 2.

Cinco intentos fallidos seguidos (contraseña, código o datos de
recuperación) bloquean la cuenta 5 minutos: lo decide la base, en los
procedimientos de sql/seguridad.sql, igual para la web y el escritorio.
"""
from urllib.parse import quote

from fastapi import APIRouter, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import correo as servicio_correo
from .. import finanzas, repositorio
from ..seguridad import (
    MENSAJES_CIERRE,
    MINUTOS_CODIGO_CORREO,
    generar_codigo_correo,
    generar_codigos_respaldo,
    hash_corto,
    cifrar_secreto,
    correo_valido,
    crear_token,
    crear_token_parcial,
    descifrar_o_none,
    generar_secreto_totp,
    hash_password,
    leer_token_parcial,
    necesita_rehash,
    problema_contrasena,
    secreto_legible,
    uri_totp,
    verificar_password,
    verificar_totp,
)
from ..qr import qr_svg
from .verificacion import verificar_segundo_factor

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

COOKIE_SESION = "sesion"
COOKIE_ACCESO = "acceso"
MENSAJE_BLOQUEO = "Demasiados intentos fallidos. Espera 5 minutos e inténtalo de nuevo."
# Con un correo inexistente se verifica contra este hash igual, para que el
# tiempo de respuesta no delate qué correos tienen cuenta.
_HASH_RELLENO = hash_password("relleno-para-igualar-tiempos")


def _redirigir(ruta: str, mensaje: str | None = None, exito: bool = False) -> RedirectResponse:
    # Los acentos y la "ñ" no son validos sin codificar dentro de una URL --
    # si se dejan tal cual, el navegador puede descartar en silencio el
    # query string completo y el mensaje nunca llega a mostrarse.
    if mensaje:
        clave = "ok" if exito else "error"
        ruta = f"{ruta}?{clave}={quote(mensaje)}"
    return RedirectResponse(url=ruta, status_code=303)


def _cookie(respuesta: RedirectResponse, nombre: str, valor: str, max_age: int) -> None:
    # secure=True: solo viaja por HTTPS. Los navegadores la aceptan también
    # en http://localhost y http://127.0.0.1, así que el desarrollo local
    # sigue funcionando.
    respuesta.set_cookie(
        nombre, valor, httponly=True, secure=True, samesite="lax", max_age=max_age
    )


def _abrir_sesion(cred: dict, respuesta=None):
    """Emite la sesión completa (por defecto, rumbo al dashboard)."""
    respuesta = respuesta or _redirigir("/dashboard")
    _cookie(
        respuesta, COOKIE_SESION,
        crear_token(cred["usuario_id"], cred["usuario"], cred["nombre"]), 8 * 3600,
    )
    respuesta.delete_cookie(COOKIE_ACCESO)
    return respuesta


def _contexto(request: Request, **extra) -> dict:
    return {
        "request": request,
        "error": request.query_params.get("error"),
        "ok": request.query_params.get("ok"),
        **extra,
    }


# ───────────────────────── Paso 1: contraseña ─────────────────────────


@router.get("/login")
def form_login(request: Request):
    return templates.TemplateResponse("login.html", _contexto(request))


@router.post("/login")
def procesar_login(usuario: str = Form(...), contrasena: str = Form(...)):
    usuario = usuario.strip()
    cred = repositorio.obtener_credenciales(usuario=usuario)
    if cred is None:
        verificar_password(contrasena, _HASH_RELLENO)
        return _redirigir("/login", "Usuario o contraseña incorrectos")
    if cred["bloqueada"]:
        return _redirigir("/login", MENSAJE_BLOQUEO)
    if not verificar_password(contrasena, cred["password"]):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CLAVE")
        return _redirigir("/login", "Usuario o contraseña incorrectos")

    if necesita_rehash(cred["password"]):
        repositorio.cambiar_password(cred["usuario_id"], hash_password(contrasena), "MIGRAR_HASH")

    if cred["totp_activo"] and cred["totp_secreto"]:
        token = crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "codigo")
        respuesta = _redirigir("/login/codigo")
    else:
        token = crear_token_parcial(
            cred["usuario_id"], cred["usuario"], cred["nombre"], "configurar",
            secreto_pendiente=generar_secreto_totp(),
        )
        respuesta = _redirigir("/login/configurar")
    _cookie(respuesta, COOKIE_ACCESO, token, 600)
    return respuesta


# ───────────────────── Paso 2a: código del autenticador ─────────────────────


def _parcial(request: Request, etapa: str) -> dict | None:
    datos = leer_token_parcial(request.cookies.get(COOKIE_ACCESO))
    return datos if datos and datos.get("etapa") == etapa else None


@router.get("/login/codigo")
def form_codigo(request: Request):
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    return templates.TemplateResponse(
        "login_codigo.html", _contexto(request, correo=datos["usuario"])
    )


@router.post("/login/codigo")
def procesar_codigo(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    cred = repositorio.obtener_credenciales(usuario_id=datos["usuario_id"])
    if cred is None:
        return _redirigir("/login", "Usuario o contraseña incorrectos")
    if cred["bloqueada"]:
        return _redirigir("/login", MENSAJE_BLOQUEO)
    if not verificar_segundo_factor(cred, codigo, "INGRESO"):
        return _redirigir("/login/codigo", "Código incorrecto o ya usado")
    return _abrir_sesion(cred)


# ─────────────────── Paso 2b: configurar el autenticador ───────────────────


@router.get("/login/configurar")
def form_configurar(request: Request):
    datos = _parcial(request, "configurar")
    secreto = descifrar_o_none(datos.get("pendiente")) if datos else None
    if secreto is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    return templates.TemplateResponse(
        "login_configurar.html",
        _contexto(
            request,
            correo=datos["usuario"],
            qr=qr_svg(uri_totp(secreto, datos["usuario"])),
            secreto=secreto_legible(secreto),
        ),
    )


@router.post("/login/configurar")
def procesar_configurar(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "configurar")
    secreto = descifrar_o_none(datos.get("pendiente")) if datos else None
    if secreto is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    cred = repositorio.obtener_credenciales(usuario_id=datos["usuario_id"])
    if cred is None:
        return _redirigir("/login", "Usuario o contraseña incorrectos")
    if cred["bloqueada"]:
        return _redirigir("/login", MENSAJE_BLOQUEO)
    paso = verificar_totp(secreto, codigo)
    if paso is None:
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_TOTP")
        return _redirigir(
            "/login/configurar",
            "El código no coincide. Revisa que la hora del teléfono sea la correcta.",
        )
    repositorio.guardar_totp(cred["usuario_id"], cifrar_secreto(secreto), paso)
    codigos = generar_codigos_respaldo()
    repositorio.reemplazar_codigos_respaldo(cred["usuario_id"], [hash_corto(c.replace("-", "")) for c in codigos])
    # Los códigos se muestran una sola vez, en esta misma respuesta.
    pantalla = templates.TemplateResponse("login_respaldo.html", _contexto(request, codigos=codigos))
    return _abrir_sesion(cred, pantalla)


# ─────────────────── Perdí mi teléfono (código por correo) ───────────────────


@router.post("/login/perdi-telefono")
def perdi_telefono(request: Request):
    """Solo después de la contraseña (etapa "codigo"): envía un código de 6
    dígitos al correo de la cuenta para configurar un autenticador nuevo."""
    datos = _parcial(request, "codigo")
    if datos is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    cred = repositorio.obtener_credenciales(usuario_id=datos["usuario_id"])
    if cred is None or cred["bloqueada"]:
        return _redirigir("/login", MENSAJE_BLOQUEO)
    codigo = generar_codigo_correo()
    repositorio.guardar_codigo_correo(cred["usuario_id"], hash_corto(codigo), MINUTOS_CODIGO_CORREO)
    try:
        servicio_correo.enviar(
            cred["usuario"], cred["nombre"], "Código para recuperar tu acceso a PRPagos",
            finanzas.html_codigo(cred["nombre"], codigo, MINUTOS_CODIGO_CORREO),
        )
    except servicio_correo.ErrorCorreo:
        return _redirigir("/login/codigo", "No se pudo enviar el correo. Usa un código de respaldo o inténtalo más tarde.")
    respuesta = _redirigir("/login/correo")
    _cookie(respuesta, COOKIE_ACCESO,
            crear_token_parcial(cred["usuario_id"], cred["usuario"], cred["nombre"], "correo"), 600)
    return respuesta


@router.get("/login/correo")
def form_codigo_correo(request: Request):
    datos = _parcial(request, "correo")
    if datos is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    return templates.TemplateResponse(
        "login_correo.html", _contexto(request, correo=datos["usuario"], minutos=MINUTOS_CODIGO_CORREO)
    )


@router.post("/login/correo")
def procesar_codigo_correo(request: Request, codigo: str = Form(...)):
    datos = _parcial(request, "correo")
    if datos is None:
        return _redirigir("/login", "Tu ingreso expiró; vuelve a escribir tu contraseña")
    cred = repositorio.obtener_credenciales(usuario_id=datos["usuario_id"])
    if cred is None or cred["bloqueada"]:
        return _redirigir("/login", MENSAJE_BLOQUEO)
    limpio = "".join(c for c in codigo if c.isdigit())
    if len(limpio) != 6 or not repositorio.usar_codigo_correo(cred["usuario_id"], hash_corto(limpio)):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CORREO")
        return _redirigir("/login/correo", "Código incorrecto o vencido")
    respuesta = _redirigir("/login/configurar", "Código verificado: configura tu autenticador nuevo.", exito=True)
    _cookie(respuesta, COOKIE_ACCESO, crear_token_parcial(
        cred["usuario_id"], cred["usuario"], cred["nombre"], "configurar",
        secreto_pendiente=generar_secreto_totp()), 600)
    return respuesta


@router.get("/logout")
def logout():
    respuesta = _redirigir("/login", MENSAJES_CIERRE["salir"], exito=True)
    respuesta.delete_cookie(COOKIE_SESION)
    respuesta.delete_cookie(COOKIE_ACCESO)
    return respuesta


# ───────────────────────── Registro ─────────────────────────


@router.get("/registro")
def form_registro(request: Request):
    return templates.TemplateResponse("registro.html", _contexto(request))


@router.post("/registro")
def procesar_registro(
    usuario: str = Form(...),
    nombre: str = Form(...),
    contrasena: str = Form(...),
    confirmacion: str = Form(...),
):
    usuario, nombre = usuario.strip(), nombre.strip()
    if not correo_valido(usuario):
        return _redirigir("/registro", "Escribe un correo válido (por ejemplo, nombre@dominio.com)")
    if not nombre:
        return _redirigir("/registro", "Escribe tu nombre completo")
    motivo = problema_contrasena(contrasena)
    if motivo:
        return _redirigir("/registro", motivo)
    if contrasena != confirmacion:
        return _redirigir("/registro", "Las contraseñas no coinciden")
    if not repositorio.crear_usuario(usuario, nombre, hash_password(contrasena)):
        return _redirigir("/registro", "Ese correo ya tiene una cuenta")
    return _redirigir(
        "/login",
        "Cuenta creada. Al ingresar configurarás tu verificación en dos pasos.",
        exito=True,
    )


# ───────────────────────── Recuperación ─────────────────────────


@router.get("/recuperar")
def form_recuperar(request: Request):
    return templates.TemplateResponse("recuperar.html", _contexto(request))


@router.post("/recuperar")
def procesar_recuperar(
    correo: str = Form(...),
    nombre_registrado: str = Form(...),
    codigo: str = Form(""),
    nueva_contrasena: str = Form(...),
    confirmacion: str = Form(...),
):
    """Con el autenticador ya configurado se exige su código: sin él, saber
    el correo y el nombre bastaría para tomar la cuenta. Las cuentas que
    todavía no lo configuraron (creadas antes de este cambio) se recuperan
    con correo + nombre, como antes, y deberán configurarlo al ingresar."""
    correo = correo.strip()
    error_generico = "Los datos no coinciden con una cuenta"
    cred = repositorio.obtener_credenciales(usuario=correo) if correo_valido(correo) else None
    if cred is None:
        return _redirigir("/recuperar", error_generico)
    if cred["bloqueada"]:
        return _redirigir("/recuperar", MENSAJE_BLOQUEO)

    if (cred["nombre"] or "").strip().lower() != nombre_registrado.strip().lower():
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_RECUPERAR")
        return _redirigir("/recuperar", error_generico)
    # Código de la app o de respaldo (cada fallo cuenta para el bloqueo).
    if cred["totp_activo"] and not verificar_segundo_factor(cred, codigo, "RECUPERAR_TOTP"):
        return _redirigir("/recuperar", error_generico)

    motivo = problema_contrasena(nueva_contrasena)
    if motivo:
        return _redirigir("/recuperar", motivo)
    if nueva_contrasena != confirmacion:
        return _redirigir("/recuperar", "Las contraseñas no coinciden")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva_contrasena), "RECUPERAR_CLAVE")
    return _redirigir("/login", "Contraseña actualizada, ya puedes ingresar", exito=True)
