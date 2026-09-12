"""
Hasheo de contraseñas y sesión.

El hash de password es SHA-256 en hexadecimal minúscula -- exactamente el
mismo algoritmo que Seguridad.java (MessageDigest "SHA-256" + toHexString).
Esto es intencional: la tabla TBPLogin ya tiene contraseñas guardadas con
ese hash desde la version de escritorio, así que un usuario creado o con
password cambiado desde cualquiera de las dos interfaces sigue pudiendo
iniciar sesión en la otra. Mismo comentario honesto que dejó la version
Java: para un sistema real en produccion lo correcto seria bcrypt/PBKDF2
con sal; se mantiene SHA-256 aqui por compatibilidad con los datos ya
existentes, no por ser la mejor practica.

La sesión web (que la version de escritorio no necesitaba, porque una
ventana de Swing vive mientras el proceso vive) se resuelve con un JWT
guardado en una cookie httponly, igual que el resto de los proyectos
FastAPI del portafolio.
"""
import hashlib
from datetime import datetime, timedelta, timezone

import jwt

from .config import settings

ALGORITMO_JWT = "HS256"
DURACION_SESION = timedelta(hours=8)


def hash_password(texto_plano: str) -> str:
    return hashlib.sha256(texto_plano.encode("utf-8")).hexdigest()


def crear_token(usuario_id: int, usuario: str, nombre: str) -> str:
    payload = {
        "usuario_id": usuario_id,
        "usuario": usuario,
        "nombre": nombre,
        "exp": datetime.now(timezone.utc) + DURACION_SESION,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=ALGORITMO_JWT)


def leer_token(token: str) -> dict | None:
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[ALGORITMO_JWT])
    except jwt.PyJWTError:
        return None
