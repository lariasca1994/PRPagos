from fastapi import Request
from fastapi.responses import RedirectResponse

from .seguridad import leer_token


class UsuarioActual:
    def __init__(self, usuario_id: int, usuario: str, nombre: str):
        self.usuario_id = usuario_id
        self.usuario = usuario
        self.nombre = nombre


def obtener_usuario_opcional(request: Request) -> UsuarioActual | None:
    token = request.cookies.get("sesion")
    if not token:
        return None
    datos = leer_token(token)
    if not datos:
        return None
    return UsuarioActual(datos["usuario_id"], datos["usuario"], datos["nombre"])


def exigir_login(request: Request):
    """Dependencia para rutas protegidas: si no hay sesión válida, redirige
    a /login en vez de tirar un 401 -- esto es una app de páginas, no una
    API pura."""
    usuario = obtener_usuario_opcional(request)
    if usuario is None:
        raise RedireccionLogin()
    return usuario


class RedireccionLogin(Exception):
    """Señal para que un exception handler devuelva el redirect. FastAPI no
    deja que una dependencia devuelva una Response directamente cuando se
    usa como Depends() en una ruta que también renderiza HTML, así que se
    resuelve con un exception handler registrado en main.py."""
    pass


def respuesta_redireccion_login() -> RedirectResponse:
    return RedirectResponse(url="/login", status_code=303)
