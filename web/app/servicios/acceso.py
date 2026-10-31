"""
Acceso a las cuentas: ingreso en dos pasos, configuración del autenticador,
"perdí mi teléfono", llaves de acceso, registro y recuperación de contraseña.
Los mismos pasos y mensajes para la web y el escritorio.

La verificación en dos pasos es obligatoria y cada cuenta elige el método:
app autenticadora o código por correo (ver app/dos_pasos.py). Sin método
configurado, después de la contraseña se elige; con los dos activos, se pide
el preferido y se puede usar el otro.

Cinco intentos fallidos seguidos (contraseña, código o datos de
recuperación) bloquean la cuenta 15 minutos: lo decide la base.
"""
from dataclasses import dataclass

from .. import correo as servicio_correo
from .. import dos_pasos, finanzas, llaves, repositorio
from ..qr import qr_svg
from ..seguridad import (
    MINUTOS_CODIGO_CORREO,
    normalizar_respaldo,
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

MENSAJE_BLOQUEO = "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."
MENSAJE_CREDENCIALES = "Usuario o contraseña incorrectos"
MENSAJE_EXPIRADO = "Tu ingreso expiró; vuelve a escribir tu contraseña"
MENSAJE_TOPE_CORREO = "Ya enviamos 3 códigos en los últimos 10 minutos. Usa el último o espera un momento."
MAX_ENVIOS_CORREO = 3
MINUTOS_TOPE_CORREO = 10
# Con un correo inexistente se verifica contra este hash igual, para que el
# tiempo de respuesta no delate qué correos tienen cuenta.
_HASH_RELLENO = hash_password("relleno-para-igualar-tiempos")


@dataclass
class PasoIngreso:
    """Resultado de la contraseña. Etapas:
    "codigo"         falta el código de la app autenticadora;
    "codigo_correo"  falta el código que se acaba de enviar al correo;
    "elegir"         la cuenta todavía no eligió su método;
    "configurar"     configurar la app (con el secreto nuevo todavía sin guardar);
    "activar_correo" confirmar el primer código del correo para activarlo."""
    etapa: str
    cred: dict
    secreto: str | None = None
    mensaje: str | None = None


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
    metodos = dos_pasos.de_cuenta(cred)
    if not metodos["configurado"]:
        return PasoIngreso("elegir", cred)
    return _paso_codigo(cred, metodos["preferido"])


def _paso_codigo(cred: dict, metodo: str) -> PasoIngreso:
    if metodo == dos_pasos.CORREO:
        enviar_codigo(cred, "Tu código para ingresar a PRPagos")
        return PasoIngreso("codigo_correo", cred, mensaje=texto_codigo_enviado(cred["usuario"]))
    return PasoIngreso("codigo", cred)


def metodos_de(usuario_id: int) -> dict:
    return dos_pasos.de_cuenta(_cuenta(usuario_id, expirado=True))


def elegir_metodo(usuario_id: int, metodo: str) -> PasoIngreso:
    """Primer ingreso: la app (configurarla) o el correo (se envía el código)."""
    cred = _cuenta(usuario_id, expirado=True)
    if dos_pasos.de_cuenta(cred)["configurado"]:
        raise ErrorServicio(MENSAJE_EXPIRADO)
    if metodo == dos_pasos.APP:
        return PasoIngreso("configurar", cred, generar_secreto_totp())
    if metodo == dos_pasos.CORREO:
        enviar_codigo(cred, "Activa tu verificación en dos pasos de PRPagos")
        return PasoIngreso("activar_correo", cred, mensaje=texto_codigo_enviado(cred["usuario"]))
    raise ErrorServicio("Elige la app autenticadora o el código por correo")


def cambiar_metodo(usuario_id: int, metodo: str) -> PasoIngreso:
    """Con los dos métodos activos: usar el otro en este ingreso."""
    cred = _cuenta(usuario_id, expirado=True)
    metodos = dos_pasos.de_cuenta(cred)
    if metodo not in (dos_pasos.APP, dos_pasos.CORREO) or not metodos[metodo]:
        raise ErrorServicio("Ese método no está activo en tu cuenta")
    return _paso_codigo(cred, metodo)


def enviar_codigo(cred: dict, asunto: str) -> None:
    """Código de 6 dígitos al correo de la cuenta: vence en 15 minutos, sirve
    una vez y se envían como máximo 3 cada 10 minutos."""
    if repositorio.envios_codigo_recientes(cred["usuario_id"], MINUTOS_TOPE_CORREO) >= MAX_ENVIOS_CORREO:
        raise ErrorServicio(MENSAJE_TOPE_CORREO)
    codigo = generar_codigo_correo()
    repositorio.guardar_codigo_correo(cred["usuario_id"], hash_corto(codigo), MINUTOS_CODIGO_CORREO)
    repositorio.registrar_envio_codigo(cred["usuario_id"], asunto)
    try:
        servicio_correo.enviar(
            cred["usuario"], cred["nombre"], asunto,
            finanzas.html_codigo(cred["nombre"], codigo, MINUTOS_CODIGO_CORREO),
        )
    except servicio_correo.ErrorCorreo as error:
        raise ErrorServicio(
            "No se pudo enviar el correo. Usa un código de respaldo o inténtalo más tarde.") from error


def _usar_codigo_correo(cred: dict, codigo: str) -> bool:
    limpio = "".join(c for c in (codigo or "") if c.isdigit())
    if len(limpio) == 6 and repositorio.usar_codigo_correo(cred["usuario_id"], hash_corto(limpio)):
        repositorio.reiniciar_intentos(cred["usuario_id"])
        return True
    repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CORREO")
    return False


def verificar_codigo(usuario_id: int, codigo: str, metodo: str = dos_pasos.APP) -> dict:
    """Código de la app, del correo o de respaldo; devuelve la cuenta para abrir la sesión."""
    cred = _cuenta(usuario_id, expirado=True)
    if metodo == dos_pasos.CORREO and normalizar_respaldo(codigo) is None:
        if not dos_pasos.de_cuenta(cred)["correo"] or not _usar_codigo_correo(cred, codigo):
            raise ErrorServicio("Código incorrecto o vencido")
        return cred
    if not verificar_segundo_factor(cred, codigo, "INGRESO"):
        raise ErrorServicio("Código incorrecto o ya usado")
    return cred


def activar_correo(usuario_id: int, codigo: str) -> tuple[dict, list[str]]:
    """Primer ingreso con el correo: su primer código lo activa y se entregan
    los 10 códigos de respaldo (se muestran una sola vez)."""
    cred = _cuenta(usuario_id, expirado=True)
    if not _usar_codigo_correo(cred, codigo):
        raise ErrorServicio("Código incorrecto o vencido")
    metodos = dos_pasos.de_cuenta(cred)
    repositorio.guardar_dos_pasos(
        cred["usuario_id"], dos_pasos.banderas(metodos["app"], True, metodos["preferido"] or dos_pasos.CORREO),
        "Código por correo activado")
    return cred, nuevos_codigos_respaldo(cred["usuario_id"])


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
    enviar_codigo(cred, "Código para recuperar tu acceso a PRPagos")
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
    if dos_pasos.de_cuenta(cred)["configurado"] and not verificar_segundo_factor(cred, codigo or "", "RECUPERAR_TOTP"):
        raise ErrorServicio(error_generico)
    motivo = problema_contrasena(nueva)
    if motivo:
        raise ErrorServicio(motivo)
    if nueva != confirmacion:
        raise ErrorServicio("Las contraseñas no coinciden")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva), "RECUPERAR_CLAVE")
    return "Contraseña actualizada, ya puedes ingresar"
