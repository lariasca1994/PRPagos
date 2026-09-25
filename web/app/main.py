"""
Punto de entrada de la app web. Reemplaza a Main.java / VentanaPrincipal
como "arranque" del sistema, pero en vez de abrir una ventana Swing expone
las mismas pantallas como paginas HTML.
"""
import logging

import oracledb
from fastapi import FastAPI, Request
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates

from .config import settings
from .dependencias import (
    RedireccionLogin,
    RedireccionSinPermiso,
    obtener_usuario_opcional,
    respuesta_redireccion_login,
    respuesta_redireccion_sin_permiso,
)
from .routers import auth, registros, usuarios

logging.basicConfig(level=logging.INFO, format="%(levelname)s:%(name)s: %(message)s")
logger = logging.getLogger("prpagos")

settings.validar()

app = FastAPI(title="PRPagos Web")

app.mount("/static", StaticFiles(directory="app/static"), name="static")

app.include_router(auth.router)
app.include_router(registros.router)
app.include_router(usuarios.router)

templates = Jinja2Templates(directory="app/templates")


@app.exception_handler(RedireccionLogin)
def manejar_redireccion_login(request: Request, exc: RedireccionLogin):
    # exigir_login() (en dependencias.py) lanza esta excepcion cuando una
    # ruta protegida no tiene sesion valida. FastAPI no permite que una
    # Depends() devuelva una Response en rutas que tambien renderizan HTML,
    # asi que se resuelve aca con un exception handler.
    return respuesta_redireccion_login()


@app.exception_handler(RedireccionSinPermiso)
def manejar_redireccion_sin_permiso(request: Request, exc: RedireccionSinPermiso):
    # exigir_admin() lanza esta excepcion cuando SI hay sesion valida pero
    # el usuario no es admin -- mismo mecanismo que RedireccionLogin.
    return respuesta_redireccion_sin_permiso()


@app.exception_handler(oracledb.Error)
def manejar_error_oracle(request: Request, exc: oracledb.Error):
    # Cualquier falla de conexion o de un procedimiento almacenado (alias
    # mal, procedimiento con firma distinta, etc.) cae aca en vez de
    # mostrar la pagina en blanco de "Internal Server Error" -- el detalle
    # tecnico queda en el log del servidor, no se le muestra al usuario.
    # exc_info=exc (no logger.exception(), que depende de sys.exc_info() del
    # hilo actual -- FastAPI corre los manejadores sincronos en un hilo
    # aparte al de la excepcion original, y ahi sys.exc_info() ya esta
    # vacio, por eso salia "NoneType: None" en vez del error real.
    logger.error("Error de base de datos", exc_info=exc)
    return templates.TemplateResponse(
        "error.html",
        {
            "request": request,
            "usuario": obtener_usuario_opcional(request),
            "mensaje": "No se pudo completar la operación por un problema con la base de datos. Intenta de nuevo en unos minutos.",
        },
        status_code=503,
    )


@app.exception_handler(Exception)
def manejar_error_generico(request: Request, exc: Exception):
    logger.error("Error inesperado", exc_info=exc)
    return templates.TemplateResponse(
        "error.html",
        {
            "request": request,
            "usuario": obtener_usuario_opcional(request),
            "mensaje": "Ocurrió un error inesperado. Intenta de nuevo.",
        },
        status_code=500,
    )


@app.get("/")
def raiz(request: Request):
    # Pantalla de explicacion publica, igual que el resto del portafolio
    # (ver gestor-casos-qa): no exige login, pero cambia el llamado a la
    # accion segun si hay sesion o no.
    usuario = obtener_usuario_opcional(request)
    return templates.TemplateResponse(
        "inicio.html", {"request": request, "usuario": usuario}
    )
