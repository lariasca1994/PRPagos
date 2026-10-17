"""
Contraseñas, verificación en dos pasos (TOTP), cifrado del secreto TOTP y
sesión web.

Todo lo de este archivo tiene un gemelo exacto en la versión de escritorio
(escritorio/src/main/java/com/prpagos/seguridad/): una cuenta creada, una
contraseña cambiada o un autenticador configurado desde una interfaz tiene
que funcionar en la otra. Si se cambia un formato aquí, hay que cambiarlo
allá también.

Contraseñas: PBKDF2-HMAC-SHA256 con sal aleatoria de 16 bytes y 600.000
iteraciones (recomendación de OWASP para PBKDF2-SHA256). Se guarda como
    pbkdf2_sha256$<iteraciones>$<sal en base64>$<hash en base64>
Las iteraciones viajan dentro del valor guardado, así que se pueden subir en
el futuro sin romper las contraseñas existentes.

Las cuentas creadas antes de este cambio tienen un SHA-256 simple (64
caracteres hexadecimales). Se siguen aceptando una sola vez: al ingresar con
la contraseña correcta, se vuelven a guardar con PBKDF2 (ver
necesita_rehash()).

TOTP (RFC 6238): HMAC-SHA1, 6 dígitos, pasos de 30 segundos, una ventana
de tolerancia de ±1 paso. Un código ya usado no se acepta de nuevo: se
guarda el último paso aceptado y solo vale uno posterior.

El secreto TOTP se guarda cifrado con AES-256-GCM (clave TOTP_CLAVE, fuera
de la base) como
    v1:<base64(nonce de 12 bytes + texto cifrado + etiqueta de 16 bytes)>
"""
import base64
import hashlib
import hmac
import secrets
import struct
import time
from datetime import datetime, timedelta, timezone
from urllib.parse import quote

import jwt
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .config import settings

# ───────────────────────── Contraseñas ─────────────────────────

ALGORITMO_HASH = "pbkdf2_sha256"
ITERACIONES_PBKDF2 = 600_000
BYTES_SAL = 16
BYTES_HASH = 32
LONGITUD_MINIMA_CONTRASENA = 8


def hash_password(texto_plano: str, iteraciones: int = ITERACIONES_PBKDF2) -> str:
    sal = secrets.token_bytes(BYTES_SAL)
    derivado = hashlib.pbkdf2_hmac(
        "sha256", texto_plano.encode("utf-8"), sal, iteraciones, BYTES_HASH
    )
    return "$".join(
        (
            ALGORITMO_HASH,
            str(iteraciones),
            base64.b64encode(sal).decode("ascii"),
            base64.b64encode(derivado).decode("ascii"),
        )
    )


def _es_sha256_heredado(guardado: str) -> bool:
    return len(guardado) == 64 and all(c in "0123456789abcdef" for c in guardado)


def verificar_password(texto_plano: str, guardado: str | None) -> bool:
    if not guardado:
        return False
    if guardado.startswith(ALGORITMO_HASH + "$"):
        try:
            _, iteraciones, sal_b64, hash_b64 = guardado.split("$")
            sal = base64.b64decode(sal_b64)
            esperado = base64.b64decode(hash_b64)
            derivado = hashlib.pbkdf2_hmac(
                "sha256", texto_plano.encode("utf-8"), sal, int(iteraciones), len(esperado)
            )
        except (ValueError, TypeError):
            return False
        return hmac.compare_digest(derivado, esperado)
    if _es_sha256_heredado(guardado):
        heredado = hashlib.sha256(texto_plano.encode("utf-8")).hexdigest()
        return hmac.compare_digest(heredado, guardado)
    return False


def necesita_rehash(guardado: str) -> bool:
    """True si el valor guardado es el SHA-256 simple de antes o PBKDF2 con
    menos iteraciones que las actuales."""
    if not guardado.startswith(ALGORITMO_HASH + "$"):
        return True
    try:
        return int(guardado.split("$")[1]) < ITERACIONES_PBKDF2
    except (IndexError, ValueError):
        return True


def problema_contrasena(texto_plano: str) -> str | None:
    """Regla mínima para contraseñas nuevas. Devuelve el motivo del rechazo
    o None si es aceptable."""
    if len(texto_plano) < LONGITUD_MINIMA_CONTRASENA:
        return f"La contraseña debe tener al menos {LONGITUD_MINIMA_CONTRASENA} caracteres"
    return None


# ───────────────────────── Correo ─────────────────────────


def correo_valido(correo: str) -> bool:
    """Cualquier correo real (las notificaciones y la recuperación de cuenta
    llegan ahí). Validación de forma, no de existencia: algo@dominio.tld,
    sin espacios y dentro del tamaño de la columna."""
    if not correo or len(correo) > 100 or any(c.isspace() for c in correo):
        return False
    local, separador, dominio = correo.rpartition("@")
    if not separador or not local or "@" in local:
        return False
    partes = dominio.split(".")
    return len(partes) >= 2 and all(partes) and len(partes[-1]) >= 2


# ───────────────────────── TOTP ─────────────────────────

EMISOR_TOTP = "PRPagos"
DIGITOS_TOTP = 6
PERIODO_TOTP = 30
VENTANA_TOTP = 1


def generar_secreto_totp() -> str:
    """160 bits aleatorios en base32 sin relleno (32 caracteres), el formato
    que esperan Google Authenticator, Microsoft Authenticator, Authy, etc."""
    return base64.b32encode(secrets.token_bytes(20)).decode("ascii").rstrip("=")


def _decodificar_base32(secreto: str) -> bytes:
    limpio = secreto.replace(" ", "").upper()
    return base64.b32decode(limpio + "=" * (-len(limpio) % 8))


def codigo_totp(secreto: str, paso: int) -> str:
    mensaje = struct.pack(">Q", paso)
    digest = hmac.new(_decodificar_base32(secreto), mensaje, hashlib.sha1).digest()
    desplazamiento = digest[-1] & 0x0F
    valor = struct.unpack(">I", digest[desplazamiento:desplazamiento + 4])[0] & 0x7FFFFFFF
    return str(valor % 10**DIGITOS_TOTP).zfill(DIGITOS_TOTP)


def paso_actual(ahora: float | None = None) -> int:
    return int((time.time() if ahora is None else ahora) // PERIODO_TOTP)


def verificar_totp(
    secreto: str, codigo: str, ultimo_paso: int = 0, ahora: float | None = None
) -> int | None:
    """Devuelve el paso de tiempo que coincidió (para guardarlo como último
    usado) o None si el código no es válido o ya se usó."""
    codigo = (codigo or "").strip().replace(" ", "")
    if len(codigo) != DIGITOS_TOTP or not codigo.isdigit():
        return None
    actual = paso_actual(ahora)
    for paso in range(actual - VENTANA_TOTP, actual + VENTANA_TOTP + 1):
        if paso <= ultimo_paso:
            continue
        if hmac.compare_digest(codigo_totp(secreto, paso), codigo):
            return paso
    return None


def uri_totp(secreto: str, cuenta: str) -> str:
    etiqueta = quote(f"{EMISOR_TOTP}:{cuenta}")
    return (
        f"otpauth://totp/{etiqueta}?secret={secreto}&issuer={quote(EMISOR_TOTP)}"
        f"&algorithm=SHA1&digits={DIGITOS_TOTP}&period={PERIODO_TOTP}"
    )


def secreto_legible(secreto: str) -> str:
    """El secreto en grupos de 4 para copiarlo a mano sin equivocarse."""
    return " ".join(secreto[i:i + 4] for i in range(0, len(secreto), 4))


# ─────────────── Recuperación si se pierde el teléfono ───────────────
# Igual en escritorio (seguridad/Recuperacion.java).

CANTIDAD_RESPALDO = 10
# Sin 0/O ni 1/I/L para que no se confundan al copiarlos a mano.
_ALFABETO_RESPALDO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
MINUTOS_CODIGO_CORREO = 15


def generar_codigos_respaldo() -> list[str]:
    """10 códigos de un solo uso con forma ABCD-EFGH (unos 40 bits cada uno)."""
    codigos = []
    for _ in range(CANTIDAD_RESPALDO):
        crudo = "".join(secrets.choice(_ALFABETO_RESPALDO) for _ in range(8))
        codigos.append(f"{crudo[:4]}-{crudo[4:]}")
    return codigos


def normalizar_respaldo(texto: str) -> str | None:
    """"abcd efgh", "ABCD-EFGH" -> "ABCDEFGH"; None si no tiene esa forma."""
    limpio = "".join(c for c in (texto or "").upper() if c.isalnum())
    if len(limpio) != 8 or any(c not in _ALFABETO_RESPALDO for c in limpio):
        return None
    return limpio


def hash_corto(texto: str) -> str:
    """SHA-256 en hex de un código de un solo uso (respaldo o correo). Basta
    sin sal: son aleatorios, de un solo uso y con bloqueo por intentos."""
    return hashlib.sha256(texto.encode("utf-8")).hexdigest()


def generar_codigo_correo() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


# ─────────────────── Cifrado del secreto TOTP ───────────────────

_PREFIJO_CIFRADO = "v1:"


def _aes() -> AESGCM:
    return AESGCM(base64.b64decode(settings.totp_clave))


def cifrar_secreto(secreto: str) -> str:
    nonce = secrets.token_bytes(12)
    cifrado = _aes().encrypt(nonce, secreto.encode("ascii"), None)
    return _PREFIJO_CIFRADO + base64.b64encode(nonce + cifrado).decode("ascii")


def descifrar_secreto(valor: str) -> str:
    if not valor.startswith(_PREFIJO_CIFRADO):
        raise ValueError("Formato de secreto TOTP desconocido")
    crudo = base64.b64decode(valor[len(_PREFIJO_CIFRADO):])
    return _aes().decrypt(crudo[:12], crudo[12:], None).decode("ascii")


# ───────────────────────── Sesión ─────────────────────────

ALGORITMO_JWT = "HS256"
# Igual que el escritorio (VentanaPrincipal): la sesión se cierra tras 6 horas
# sin uso, y como máximo a las 8 horas aunque haya actividad.
INACTIVIDAD_SESION = timedelta(hours=6)
DURACION_SESION = timedelta(hours=8)
# Mensajes de cierre de sesión: los mismos textos en la web y en el escritorio.
MENSAJES_CIERRE = {
    "inactividad": "Tu sesión se cerró tras 6 horas de inactividad. Ingresa de nuevo.",
    "maximo": "Tu sesión se cerró al cumplir 8 horas. Ingresa de nuevo.",
    "suspendida": "Tu sesión se cerró porque tu cuenta ya no está activa.",
    "salir": "Cerraste sesión.",
}
# Entre la contraseña y el código TOTP (o la configuración del autenticador).
DURACION_ACCESO_PARCIAL = timedelta(minutes=10)


def crear_token(usuario_id: int, usuario: str, nombre: str, inicio: int | None = None) -> str:
    """Sesión completa: solo se emite después de verificar el código TOTP.
    Vence a las 6 horas sin uso (cada petición la renueva, ver
    dependencias.py) y nunca más allá de 8 horas desde el ingreso."""
    ahora = datetime.now(timezone.utc)
    inicio = inicio or int(ahora.timestamp())
    tope = datetime.fromtimestamp(inicio, timezone.utc) + DURACION_SESION
    payload = {
        "tipo": "sesion",
        "usuario_id": usuario_id,
        "usuario": usuario,
        "nombre": nombre,
        "inicio": inicio,
        "exp": min(ahora + INACTIVIDAD_SESION, tope),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGORITMO_JWT)


def renovar_token(datos: dict) -> str:
    """La misma sesión con el plazo de inactividad reiniciado."""
    return crear_token(datos["usuario_id"], datos["usuario"], datos["nombre"], datos.get("inicio"))


def motivo_cierre(token: str | None) -> str | None:
    """Por qué ya no vale una cookie de sesión auténtica: "inactividad" o
    "maximo". None si no hay cookie, si es falsa o si sigue vigente."""
    if not token:
        return None
    try:
        datos = jwt.decode(
            token, settings.jwt_secret, algorithms=[ALGORITMO_JWT], options={"verify_exp": False}
        )
    except jwt.PyJWTError:
        return None
    if datos.get("tipo") != "sesion":
        return None
    ahora = datetime.now(timezone.utc).timestamp()
    inicio = datos.get("inicio")
    if inicio and ahora >= inicio + DURACION_SESION.total_seconds():
        return "maximo"
    if ahora >= datos.get("exp", 0):
        return "inactividad"
    return None


def leer_token(token: str) -> dict | None:
    datos = _decodificar(token)
    if not datos or datos.get("tipo") != "sesion":
        return None
    return datos


def crear_token_parcial(
    usuario_id: int, usuario: str, nombre: str, etapa: str, secreto_pendiente: str | None = None
) -> str:
    """Acceso a medio camino: la contraseña ya se verificó pero falta el
    código TOTP (etapa "codigo") o configurar el autenticador (etapa
    "configurar"). En la configuración, el secreto nuevo viaja aquí
    cifrado con AES-GCM: no se escribe en la base hasta que se confirma con
    un código válido."""
    payload = {
        "tipo": "parcial",
        "etapa": etapa,
        "usuario_id": usuario_id,
        "usuario": usuario,
        "nombre": nombre,
        "exp": datetime.now(timezone.utc) + DURACION_ACCESO_PARCIAL,
    }
    if secreto_pendiente:
        payload["pendiente"] = cifrar_secreto(secreto_pendiente)
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGORITMO_JWT)


def leer_token_parcial(token: str | None) -> dict | None:
    datos = _decodificar(token) if token else None
    if not datos or datos.get("tipo") != "parcial":
        return None
    return datos


def crear_token_reconfiguracion(usuario_id: int, secreto_pendiente: str) -> str:
    """Para cambiar de autenticador con la sesión ya abierta: guarda el
    secreto nuevo (cifrado) hasta que se confirme con un código suyo."""
    payload = {
        "tipo": "reconfigurar",
        "usuario_id": usuario_id,
        "pendiente": cifrar_secreto(secreto_pendiente),
        "exp": datetime.now(timezone.utc) + DURACION_ACCESO_PARCIAL,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGORITMO_JWT)


def leer_token_reconfiguracion(token: str | None, usuario_id: int) -> str | None:
    datos = _decodificar(token) if token else None
    if not datos or datos.get("tipo") != "reconfigurar" or datos.get("usuario_id") != usuario_id:
        return None
    return descifrar_o_none(datos.get("pendiente"))


def descifrar_o_none(valor: str | None) -> str | None:
    """Como descifrar_secreto, pero sin excepciones: un valor alterado o
    cifrado con otra clave (InvalidTag) cuenta como inexistente."""
    if not valor:
        return None
    try:
        return descifrar_secreto(valor)
    except Exception:  # InvalidTag, ValueError, binascii.Error
        return None


def _decodificar(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[ALGORITMO_JWT])
    except jwt.PyJWTError:
        return None
