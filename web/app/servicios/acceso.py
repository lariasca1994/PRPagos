"""
Acceso a las cuentas: ingreso en dos pasos, configuración del autenticador,
"perdí mi teléfono", llaves de acceso, registro y recuperación de contraseña.
Los mismos pasos y mensajes para la web y el escritorio.

Cinco intentos fallidos seguidos (contraseña, código o datos de
recuperación) bloquean la cuenta 5 minutos: lo decide la base.
"""
from dataclasses import dataclass

from .. import correo as servicio_correo
from .. import finanzas, llaves, repositorio
from ..qr import qr_svg
from ..seguridad import (
    MINUTOS_CODIGO_CORREO,
    correo_valido,
    generar_codigo_correo,
    generar_codigos_respaldo,
    generar_secreto_totp,
    hash_corto,
    hash_password,
    cifrar_secreto,
    necesita_rehash,
    problema_contrasena,
    secreto_legible,
    uri_totp,
    verificar_password,
    verificar_totp,
)
from .verificacion import verificar_segundo_factor
from . import ErrorServicio

MENSAJE_BLOQUEO = "Demasiados intentos fallidos. Espera 5 minutos e inténtalo de nuevo."
MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos"
MENSAJE_EXPIRADO = "Tu ingreso expiró; vuelve a escribir tu contraseña"
# Con un correo inexistente se verifica contra este hash igual, para que el
# tiempo de respuesta no delate qué correos tienen cuenta.
_HASH_RELLENO = hash_password("relleno-para-igualar-tiempos")


@dataclass
class PasoIngreso:
    """Resultado de la contraseña: falta el código ("codigo") o configurar el
    autenticador ("configurar", con el secreto nuevo todavía sin guardar)."""
    etapa: str
    cred: dict
    secreto: str | None = None


def configuracion_autenticador(secreto: str, correo: str) -> dict:
    """Lo que se muestra para configurar la app autenticadora."""
    uri = uri_totp(secreto, correo)
    return {"uri": uri, "qr": qr_svg(uri), "clave": secreto_legible(secreto)}


def _cuenta(usuario_id: int, expirado: bool = False) -> dict:
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None:
        raise ErrorServicio(MENSAJE_EXPIRADO if expirado else MENSAJE_CREDENCIALES)
    if cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    return cred


# ───────────────────────── Ingreso ─────────────────────────


def ingresar(usuario: str, contrasena: str) -> PasoIngreso:
    usuario = (usuario or "").strip()
    cred = repositorio.obtener_credenciales(usuario=usuario)
    if cred is None:
        verificar_password(contrasena, _HASH_RELLENO)
        raise ErrorServicio(MENSAJE_CREDENCIALES)
    if cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    if not verificar_password(contrasena, cred["password"]):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CLAVE")
        raise ErrorServicio(MENSAJE_CREDENCIALES)
    if necesita_rehash(cred["password"]):
        repositorio.cambiar_password(cred["usuario_id"], hash_password(contrasena), "MIGRAR_HASH")
    if cred["totp_activo"] and cred["totp_secreto"]:
        return PasoIngreso("codigo", cred)
    return PasoIngreso("configurar", cred, generar_secreto_totp())


def verificar_codigo(usuario_id: int, codigo: str) -> dict:
    """Código de la app o de respaldo; devuelve la cuenta para abrir la sesión."""
    cred = _cuenta(usuario_id, expirado=True)
    if not verificar_segundo_factor(cred, codigo, "INGRESO"):
        raise ErrorServicio("Código incorrecto o ya usado")
    return cred


def activar_autenticador(usuario_id: int, secreto: str, codigo: str) -> tuple[dict, list[str]]:
    """Guarda el autenticador si el primer código es válido y entrega los 10
    códigos de respaldo (se muestran una sola vez)."""
    cred = _cuenta(usuario_id, expirado=True)
    paso = verificar_totp(secreto, codigo)
    if paso is None:
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_TOTP")
        raise ErrorServicio("El código no coincide. Revisa que la hora del teléfono sea la correcta.")
    repositorio.guardar_totp(cred["usuario_id"], cifrar_secreto(secreto), paso)
    return cred, nuevos_codigos_respaldo(cred["usuario_id"])


def nuevos_codigos_respaldo(usuario_id: int) -> list[str]:
    codigos = generar_codigos_respaldo()
    repositorio.reemplazar_codigos_respaldo(usuario_id, [hash_corto(c.replace("-", "")) for c in codigos])
    return codigos


def perdi_telefono(usuario_id: int) -> dict:
    """Envía un código de 6 dígitos al correo de la cuenta (solo tras la contraseña)."""
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None or cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    codigo = generar_codigo_correo()
    repositorio.guardar_codigo_correo(cred["usuario_id"], hash_corto(codigo), MINUTOS_CODIGO_CORREO)
    try:
        servicio_correo.enviar(
            cred["usuario"], cred["nombre"], "Código para recuperar tu acceso a PRPagos",
            finanzas.html_codigo(cred["nombre"], codigo, MINUTOS_CODIGO_CORREO),
        )
    except servicio_correo.ErrorCorreo as error:
        raise ErrorServicio(
            "No se pudo enviar el correo. Usa un código de respaldo o inténtalo más tarde.") from error
    return cred


def texto_codigo_enviado(correo: str) -> str:
    return f"Te enviamos un código de 6 dígitos a {correo}. Vence en {MINUTOS_CODIGO_CORREO} minutos."


def verificar_codigo_correo(usuario_id: int, codigo: str) -> PasoIngreso:
    """Código del correo válido: sigue configurar un autenticador nuevo."""
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None or cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    limpio = "".join(c for c in (codigo or "") if c.isdigit())
    if len(limpio) != 6 or not repositorio.usar_codigo_correo(cred["usuario_id"], hash_corto(limpio)):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CORREO")
        raise ErrorServicio("Código incorrecto o vencido")
    return PasoIngreso("configurar", cred, generar_secreto_totp())


# ───────────────────────── Llave de acceso ─────────────────────────


def ingresar_con_llave(credencial: dict, reto: bytes) -> dict:
    """Ingreso con una llave (WebAuthn): reemplaza los dos pasos."""
    llave = repositorio.llave_por_credencial(str(credencial.get("id", "")))
    if llave is None:
        raise ErrorServicio("Esta llave de acceso no está registrada en PRPagos.")
    cred = _cuenta(llave["usuario_id"])
    manejador = (credencial.get("response") or {}).get("userHandle")
    try:
        if manejador and llaves.base64url_to_bytes(manejador) != llaves.id_usuario(cred["usuario_id"]):
            raise llaves.ErrorLlave("La llave de acceso no es válida.")
        contador = llaves.verificar_ingreso(credencial, reto, llave["clave_publica"], llave["contador"])
    except llaves.ErrorLlave as error:
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_LLAVE")
        raise ErrorServicio(str(error)) from error
    repositorio.usar_llave(llave["id"], cred["usuario_id"], contador)
    return cred


# ───────────────────────── Registro y recuperación ─────────────────────────


def registrar(usuario: str, nombre: str, contrasena: str, confirmacion: str) -> str:
    usuario, nombre = (usuario or "").strip(), (nombre or "").strip()
    if not correo_valido(usuario):
        raise ErrorServicio("Escribe un correo válido (por ejemplo, nombre@dominio.com)")
    if not nombre:
        raise ErrorServicio("Escribe tu nombre completo")
    motivo = problema_contrasena(contrasena)
    if motivo:
        raise ErrorServicio(motivo)
    if contrasena != confirmacion:
        raise ErrorServicio("Las contraseñas no coinciden")
    if not repositorio.crear_usuario(usuario, nombre[:100], hash_password(contrasena)):
        raise ErrorServicio("Ese correo ya tiene una cuenta")
    return "Cuenta creada. Al ingresar configurarás tu verificación en dos pasos."


def recuperar(correo: str, nombre_registrado: str, codigo: str, nueva: str, confirmacion: str) -> str:
    """Con el autenticador configurado se exige su código (o uno de respaldo):
    sin él, saber el correo y el nombre bastaría para tomar la cuenta."""
    correo = (correo or "").strip()
    error_generico = "Los datos no coinciden con una cuenta"
    cred = repositorio.obtener_credenciales(usuario=correo) if correo_valido(correo) else None
    if cred is None:
        raise ErrorServicio(error_generico)
    if cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    if (cred["nombre"] or "").strip().lower() != (nombre_registrado or "").strip().lower():
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_RECUPERAR")
        raise ErrorServicio(error_generico)
    if cred["totp_activo"] and not verificar_segundo_factor(cred, codigo or "", "RECUPERAR_TOTP"):
        raise ErrorServicio(error_generico)
    motivo = problema_contrasena(nueva)
    if motivo:
        raise ErrorServicio(motivo)
    if nueva != confirmacion:
        raise ErrorServicio("Las contraseñas no coinciden")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva), "RECUPERAR_CLAVE")
    return "Contraseña actualizada, ya puedes ingresar"
