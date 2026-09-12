import re
from urllib.parse import quote

from fastapi import APIRouter, Form, Request
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates

from .. import repositorio
from ..seguridad import crear_token, hash_password

router = APIRouter()
templates = Jinja2Templates(directory="app/templates")

# Mismo patron que VentanaCrearUsuario.java / VentanaRecuperarContrasena.java:
# el "usuario" del sistema es siempre un correo @EAN.com.
PATRON_CORREO = re.compile(r"^[a-zA-Z0-9_.+-]+@EAN\.com$", re.IGNORECASE)


def _redirigir_con_error(ruta: str, mensaje: str) -> RedirectResponse:
    # Los acentos y la "ñ" no son validos sin codificar dentro de una URL --
    # si se dejan tal cual, el navegador puede descartar en silencio el
    # query string completo y el mensaje de error nunca llega a mostrarse.
    return RedirectResponse(url=f"{ruta}?error={quote(mensaje)}", status_code=303)


@router.get("/login")
def form_login(request: Request, error: str | None = None):
    return templates.TemplateResponse(
        "login.html", {"request": request, "error": error}
    )


@router.post("/login")
def procesar_login(usuario: str = Form(...), contrasena: str = Form(...)):
    resultado = repositorio.validar_login(usuario, hash_password(contrasena))
    if resultado is None:
        return _redirigir_con_error("/login", "Usuario o contraseña incorrectos")
    nombre, usuario_id = resultado
    token = crear_token(usuario_id, usuario, nombre)
    respuesta = RedirectResponse(url="/registros", status_code=303)
    respuesta.set_cookie(
        "sesion", token, httponly=True, samesite="lax", max_age=8 * 3600
    )
    return respuesta


@router.get("/logout")
def logout():
    respuesta = RedirectResponse(url="/login", status_code=303)
    respuesta.delete_cookie("sesion")
    return respuesta


@router.get("/registro")
def form_registro(request: Request, error: str | None = None):
    return templates.TemplateResponse(
        "registro.html", {"request": request, "error": error}
    )


@router.post("/registro")
def procesar_registro(
    usuario: str = Form(...), nombre: str = Form(...), contrasena: str = Form(...)
):
    if not PATRON_CORREO.match(usuario):
        return _redirigir_con_error(
            "/registro", "El usuario debe ser un correo válido @EAN.com"
        )
    creado = repositorio.crear_usuario(usuario, nombre, hash_password(contrasena))
    if not creado:
        return _redirigir_con_error("/registro", "Ese usuario ya existe")
    return _redirigir_con_error("/login", "Usuario creado, ya puedes ingresar")


@router.get("/recuperar")
def form_recuperar(request: Request, error: str | None = None):
    return templates.TemplateResponse(
        "recuperar.html", {"request": request, "error": error}
    )


@router.post("/recuperar")
def procesar_recuperar(
    correo: str = Form(...),
    nombre_registrado: str = Form(...),
    nueva_contrasena: str = Form(...),
):
    if not PATRON_CORREO.match(correo):
        return _redirigir_con_error("/recuperar", "El correo no es válido")

    nombre_real = repositorio.buscar_nombre_por_usuario(correo)
    if nombre_real is None:
        return _redirigir_con_error("/recuperar", "El correo no está registrado")

    # Misma verificacion minima que VentanaRecuperarContrasena.java: el
    # nombre tiene que coincidir con el registrado (no es un token de un
    # solo uso por correo, que seria lo correcto en un sistema real, pero
    # cierra el hueco mas grave -- ver comentario en el .java original).
    if nombre_real.strip().lower() != nombre_registrado.strip().lower():
        return _redirigir_con_error(
            "/recuperar", "El nombre no coincide con el registrado"
        )

    repositorio.actualizar_password(correo, hash_password(nueva_contrasena))
    return _redirigir_con_error("/login", "Contraseña actualizada, ya puedes ingresar")
