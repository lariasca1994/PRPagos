from fastapi import Request
from fastapi.responses import RedirectResponse

from . import repositorio
from .seguridad import leer_token


class UsuarioActual:
    def __init__(self, usuario_id: int, usuario: str, nombre: str, rol: str):
        self.usuario_id = usuario_id
        self.usuario = usuario
        self.nombre = nombre
        self.rol = rol

    @property
    def es_admin(self) -> bool:
        return self.rol == "admin"


def obtener_usuario_opcional(request: Request) -> UsuarioActual | None:
    token = request.cookies.get("sesion")
    if not token:
        return None
    datos = leer_token(token)
    if not datos:
        return None
    # Se vuelve a consultar TBPLogin en cada request (no alcanza con lo que
    # dice el JWT, que vive hasta 8 horas) para que una suspension o un
    # cambio de rol hecho por el admin tengan efecto inmediato -- mismo
    # patron que el resto del portafolio. Ver obtener_estado_usuario en
    # repositorio.py.
    estado = repositorio.obtener_estado_usuario(datos["usuario_id"])
    if estado is None or not estado["activo"]:
        return None
    return UsuarioActual(datos["usuario_id"], datos["usuario"], datos["nombre"], estado["rol"])


def exigir_login(request: Request):
    """Dependencia para rutas protegidas: si no hay sesión válida, redirige
    a /login en vez de tirar un 401 -- esto es una app de páginas, no una
    API pura."""
    usuario = obtener_usuario_opcional(request)
    if usuario is None:
        raise RedireccionLogin()
    return usuario


def exigir_admin(request: Request):
    """Como exigir_login, pero además exige rol='admin'. Si hay sesión
    válida pero no es admin, redirige a /registros en vez de mostrar la
    pantalla de gestión -- no es un error de sesión, es un permiso que
    falta."""
    usuario = exigir_login(request)
    if not usuario.es_admin:
        raise RedireccionSinPermiso()
    return usuario


class RedireccionLogin(Exception):
    """Señal para que un exception handler devuelva el redirect. FastAPI no
    deja que una dependencia devuelva una Response directamente cuando se
    usa como Depends() en una ruta que también renderiza HTML, así que se
    resuelve con un exception handler registrado en main.py."""
    pass


class RedireccionSinPermiso(Exception):
    """Como RedireccionLogin, pero cuando SÍ hay sesión válida y solo falta
    el rol admin -- se resuelve con su propio exception handler en
    main.py."""
    pass


def respuesta_redireccion_login() -> RedirectResponse:
    return RedirectResponse(url="/login", status_code=303)


def respuesta_redireccion_sin_permiso() -> RedirectResponse:
    return RedirectResponse(url="/registros", status_code=303)
