from urllib.parse import quote

from fastapi import Request
from fastapi.responses import RedirectResponse

from . import repositorio
from .seguridad import MENSAJES_CIERRE, leer_token, motivo_cierre, renovar_token


class UsuarioActual:
    def __init__(self, usuario_id: int, usuario: str, nombre: str, rol: str, vence: int = 0):
        self.usuario_id = usuario_id
        self.usuario = usuario
        self.nombre = nombre
        self.rol = rol
        # Momento (epoch, s) en que la sesión vencería sin más actividad: base.html
        # vuelve sola al ingreso con el mensaje, como la ventana de escritorio.
        self.vence = vence

    @property
    def es_admin(self) -> bool:
        return self.rol == "admin"


def obtener_usuario_opcional(request: Request) -> UsuarioActual | None:
    """Sesión de la web (cookie "sesion") o de la app de escritorio
    (cabecera "Authorization: Bearer ..."): el mismo token y las mismas reglas."""
    token = request.cookies.get("sesion")
    autorizacion = request.headers.get("authorization", "")
    if autorizacion.lower().startswith("bearer "):
        token = autorizacion[7:].strip()
    if not token:
        return None
    datos = leer_token(token)
    if not datos:
        request.state.motivo_cierre = motivo_cierre(token)
        return None
    # Se vuelve a consultar TBPLogin en cada request (no alcanza con lo que
    # dice el JWT, que vive hasta 8 horas) para que una suspension o un
    # cambio de rol hecho por el admin tengan efecto inmediato -- mismo
    # patron que el resto del portafolio. Ver obtener_estado_usuario en
    # repositorio.py.
    estado = repositorio.obtener_estado_usuario(datos["usuario_id"])
    if estado is None or not estado["activo"]:
        request.state.motivo_cierre = "suspendida"
        return None
    # Hubo actividad: se renueva el plazo de inactividad (main.py pone la
    # cookie nueva en la respuesta).
    nuevo = renovar_token(datos)
    request.state.token_renovado = nuevo
    vence = leer_token(nuevo)["exp"]
    return UsuarioActual(datos["usuario_id"], datos["usuario"], datos["nombre"], estado["rol"], vence)


def exigir_login(request: Request):
    """Dependencia para rutas protegidas: si no hay sesión válida, redirige
    a /login en vez de tirar un 401 -- esto es una app de páginas, no una
    API pura."""
    usuario = obtener_usuario_opcional(request)
    if usuario is None:
        raise RedireccionLogin(getattr(request.state, "motivo_cierre", None))
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
    resuelve con un exception handler registrado en main.py. Lleva el
    motivo del cierre (inactividad, máximo o cuenta suspendida) para
    mostrarlo en el ingreso."""

    def __init__(self, motivo: str | None = None):
        super().__init__(motivo)
        self.motivo = motivo


class RedireccionSinPermiso(Exception):
    """Como RedireccionLogin, pero cuando SÍ hay sesión válida y solo falta
    el rol admin -- se resuelve con su propio exception handler en
    main.py."""
    pass


def respuesta_redireccion_login(motivo: str | None = None) -> RedirectResponse:
    if motivo in MENSAJES_CIERRE:
        respuesta = RedirectResponse(
            url=f"/login?error={quote(MENSAJES_CIERRE[motivo])}", status_code=303
        )
    else:
        respuesta = RedirectResponse(url="/login", status_code=303)
    respuesta.delete_cookie("sesion")
    return respuesta


def respuesta_redireccion_sin_permiso() -> RedirectResponse:
    return RedirectResponse(url="/dashboard", status_code=303)
