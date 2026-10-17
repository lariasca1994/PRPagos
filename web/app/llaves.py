"""
Llaves de acceso (passkeys / WebAuthn): ingresar con Windows Hello, la huella,
el rostro o el PIN del dispositivo, en el navegador y en la PWA de Android.
La biometría nunca sale del dispositivo; la base solo guarda la clave pública
(COSE) y el contador de firmas de cada llave.

Gemelo de escritorio/.../seguridad/LlavesAcceso.java (que usa Windows Hello con
webauthn.dll): los dos usan el mismo identificador de sitio (el dominio de la
web), el mismo id de usuario y los mismos algoritmos, así que una llave creada
en Edge sirve en la app de escritorio del mismo Windows, y al revés.

Una llave con verificación del usuario (huella o PIN) vale como los dos pasos
del ingreso: algo que tienes (el dispositivo) y algo que eres o sabes.
"""
import secrets
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

import jwt
from webauthn import (
    generate_authentication_options,
    generate_registration_options,
    options_to_json,
    verify_authentication_response,
    verify_registration_response,
)
from webauthn.helpers import base64url_to_bytes, bytes_to_base64url
from webauthn.helpers.cose import COSEAlgorithmIdentifier
from webauthn.helpers.structs import (
    AuthenticatorSelectionCriteria,
    PublicKeyCredentialDescriptor,
    ResidentKeyRequirement,
    UserVerificationRequirement,
)

from .config import settings

NOMBRE_SITIO = "PRPagos"
# ES256 y RS256: los dos que verifica también el escritorio (Windows Hello usa RS256 o ES256).
ALGORITMOS = [COSEAlgorithmIdentifier.ECDSA_SHA_256, COSEAlgorithmIdentifier.RSASSA_PKCS1_v1_5_SHA_256]
TIEMPO_MS = 60_000
DURACION_RETO = timedelta(minutes=5)
LONGITUD_NOMBRE = 60


class ErrorLlave(Exception):
    """La llave no es válida: el mensaje se muestra tal cual."""


def origen() -> str:
    return settings.url_publica.rstrip("/")


def id_sitio() -> str:
    """El dominio de la web (sin puerto): identifica las llaves de PRPagos."""
    return urlparse(origen()).hostname or "localhost"


def id_usuario(usuario_id: int) -> bytes:
    """Identificador opaco de la cuenta dentro de la llave (sin datos personales)."""
    return str(usuario_id).encode("ascii")


# ───────────────────────── Reto firmado (cookie de 5 minutos) ─────────────────────────


def crear_reto(fin: str, usuario_id: int | None = None, nombre: str | None = None) -> tuple[bytes, str]:
    """fin: "ingreso" o "registro". Devuelve el reto y el token para la cookie."""
    reto = secrets.token_bytes(32)
    payload = {
        "tipo": "llave",
        "fin": fin,
        "reto": bytes_to_base64url(reto),
        "usuario_id": usuario_id,
        "nombre": nombre,
        "exp": datetime.now(timezone.utc) + DURACION_RETO,
    }
    return reto, jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def leer_reto(token: str | None, fin: str) -> dict | None:
    try:
        datos = jwt.decode(token or "", settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        return None
    if datos.get("tipo") != "llave" or datos.get("fin") != fin:
        return None
    datos["reto"] = base64url_to_bytes(datos["reto"])
    return datos


def nombre_valido(nombre: str) -> str | None:
    limpio = " ".join((nombre or "").split())[:LONGITUD_NOMBRE]
    return limpio or None


# ───────────────────────── Registrar una llave ─────────────────────────


def opciones_registro(usuario_id: int, correo: str, nombre: str, existentes: list[str],
                      reto: bytes) -> str:
    """Opciones para navigator.credentials.create(). La llave queda en el
    dispositivo (residente), con verificación obligatoria; no se repite una
    llave ya registrada en ese mismo dispositivo."""
    opciones = generate_registration_options(
        rp_id=id_sitio(),
        rp_name=NOMBRE_SITIO,
        user_id=id_usuario(usuario_id),
        user_name=correo,
        user_display_name=nombre,
        challenge=reto,
        timeout=TIEMPO_MS,
        authenticator_selection=AuthenticatorSelectionCriteria(
            resident_key=ResidentKeyRequirement.REQUIRED,
            user_verification=UserVerificationRequirement.REQUIRED,
        ),
        exclude_credentials=[PublicKeyCredentialDescriptor(id=base64url_to_bytes(c)) for c in existentes],
        supported_pub_key_algs=ALGORITMOS,
    )
    return options_to_json(opciones)


def verificar_registro(credencial: dict, reto: bytes) -> tuple[str, str, int]:
    """(credencial_id, clave_publica, contador), todo en base64url salvo el contador."""
    try:
        verificada = verify_registration_response(
            credential=credencial,
            expected_challenge=reto,
            expected_rp_id=id_sitio(),
            expected_origin=origen(),
            require_user_verification=True,
            supported_pub_key_algs=ALGORITMOS,
        )
    except Exception as error:  # InvalidRegistrationResponse, InvalidCBORData, KeyError…
        raise ErrorLlave("No se pudo verificar la llave de acceso; inténtalo de nuevo.") from error
    return (bytes_to_base64url(verificada.credential_id),
            bytes_to_base64url(verificada.credential_public_key),
            verificada.sign_count)


# ───────────────────────── Ingresar con una llave ─────────────────────────


def opciones_ingreso(reto: bytes) -> str:
    """Sin lista de llaves: el dispositivo ofrece las de PRPagos que tenga
    (no hace falta escribir el correo)."""
    opciones = generate_authentication_options(
        rp_id=id_sitio(),
        challenge=reto,
        timeout=TIEMPO_MS,
        user_verification=UserVerificationRequirement.REQUIRED,
    )
    return options_to_json(opciones)


def verificar_ingreso(credencial: dict, reto: bytes, clave_publica: str, contador: int) -> int:
    """Devuelve el contador nuevo. Lanza ErrorLlave si la firma no es válida."""
    try:
        verificada = verify_authentication_response(
            credential=credencial,
            expected_challenge=reto,
            expected_rp_id=id_sitio(),
            expected_origin=origen(),
            credential_public_key=base64url_to_bytes(clave_publica),
            credential_current_sign_count=contador,
            require_user_verification=True,
        )
    except Exception as error:  # InvalidAuthenticationResponse y similares
        raise ErrorLlave("La llave de acceso no es válida.") from error
    return verificada.new_sign_count
