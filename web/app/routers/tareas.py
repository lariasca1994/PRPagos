"""
Tareas programadas. GitHub Actions (.github/workflows/resumenes.yml) llama a
POST /tareas/resumenes cada día a las 08:00 (hora de Colombia) con el token
TAREAS_TOKEN; la app decide si hoy toca el resumen semanal (viernes) o el
general (día 29; en febrero, el último día). Sin costo: Actions es gratis
en repositorios públicos y Brevo, en su plan gratuito.

POST /tareas/version-escritorio lo llama .github/workflows/escritorio.yml al
publicar un Release: registra la versión y su huella SHA-256 para que la
aplicación de escritorio se actualice sola.
"""
import hmac
import re
from urllib.parse import urlparse

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

from .. import repositorio, resumenes
from ..config import settings

router = APIRouter(prefix="/tareas")

# Solo paquetes publicados como Release de este repositorio.
PREFIJO_RELEASES = "/lariasca1994/PRPagos/releases/download/"


class VersionEscritorio(BaseModel):
    version: str
    url: str
    sha256: str


def _autorizar(token: str) -> None:
    if not settings.tareas_token or not hmac.compare_digest(token, settings.tareas_token):
        raise HTTPException(status_code=403, detail="No autorizado")


@router.post("/resumenes", include_in_schema=False)
def tarea_resumenes(x_tarea_token: str = Header(""), forzar: str | None = None):
    _autorizar(x_tarea_token)
    if forzar not in (None, "semanal", "mensual"):
        raise HTTPException(status_code=400, detail="forzar debe ser semanal o mensual")
    resultado = resumenes.tarea_diaria(forzar)
    # Misma tarea diaria: depura los eventos de seguridad de más de 15 días.
    try:
        resultado["eventos_depurados"] = repositorio.depurar_eventos_seguridad()
    except Exception:  # noqa: BLE001 - la depuración no debe frenar los resúmenes
        resultado["eventos_depurados"] = None
    return resultado


@router.post("/version-escritorio", include_in_schema=False)
def tarea_version_escritorio(datos: VersionEscritorio, x_tarea_token: str = Header("")):
    _autorizar(x_tarea_token)
    url = urlparse(datos.url)
    if not re.fullmatch(r"\d+\.\d+\.\d+", datos.version):
        raise HTTPException(status_code=400, detail="Versión inválida")
    if url.scheme != "https" or url.netloc != "github.com" or not url.path.startswith(PREFIJO_RELEASES):
        raise HTTPException(status_code=400, detail="La URL debe ser de un Release de GitHub del proyecto")
    if not re.fullmatch(r"[0-9a-f]{64}", datos.sha256):
        raise HTTPException(status_code=400, detail="Huella SHA-256 inválida")
    repositorio.publicar_version_escritorio(datos.version, datos.url, datos.sha256)
    return {"version": datos.version, "registrada": True}
