"""
Envío de correos con la API transaccional de Brevo (plan gratuito: 300 al
día). Gemelo de escritorio/.../finanzas/Correo.java: la misma petición, el
mismo remitente y las mismas plantillas (compartido/correo/).
"""
import json
import urllib.error
import urllib.request

from .config import settings

URL_BREVO = "https://api.brevo.com/v3/smtp/email"
NOMBRE_REMITENTE = "PRPagos"


class ErrorCorreo(RuntimeError):
    pass


def disponible() -> bool:
    return bool(settings.brevo_api_key and settings.brevo_remitente)


def enviar(destino: str, nombre: str, asunto: str, html: str) -> None:
    if not disponible():
        raise ErrorCorreo("El envío de correos no está configurado (BREVO_API_KEY y BREVO_REMITENTE).")
    cuerpo = json.dumps({
        "sender": {"name": NOMBRE_REMITENTE, "email": settings.brevo_remitente},
        "to": [{"email": destino, "name": nombre}],
        "subject": asunto,
        "htmlContent": html,
    }).encode("utf-8")
    peticion = urllib.request.Request(
        URL_BREVO,
        data=cuerpo,
        method="POST",
        headers={
            "api-key": settings.brevo_api_key,
            "content-type": "application/json",
            "accept": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(peticion, timeout=20) as respuesta:
            if respuesta.status not in (200, 201, 202):
                raise ErrorCorreo(f"Brevo respondió {respuesta.status}")
    except urllib.error.HTTPError as e:
        detalle = e.read().decode("utf-8", "replace")[:200]
        raise ErrorCorreo(f"Brevo rechazó el correo ({e.code}): {detalle}") from e
    except urllib.error.URLError as e:
        raise ErrorCorreo(f"No se pudo conectar con Brevo: {e.reason}") from e
