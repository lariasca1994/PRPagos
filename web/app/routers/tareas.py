"""
Tareas programadas. GitHub Actions (.github/workflows/resumenes.yml) llama a
POST /tareas/resumenes cada día a las 08:00 (hora de Colombia) con el token
TAREAS_TOKEN; la app decide si hoy toca el resumen semanal (viernes) o el
general (día 29; en febrero, el último día). Sin costo: Actions es gratis
en repositorios públicos y Brevo, en su plan gratuito.
"""
import hmac

from fastapi import APIRouter, Header, HTTPException

from .. import resumenes
from ..config import settings

router = APIRouter(prefix="/tareas")


@router.post("/resumenes", include_in_schema=False)
def tarea_resumenes(x_tarea_token: str = Header(""), forzar: str | None = None):
    if not settings.tareas_token or not hmac.compare_digest(x_tarea_token, settings.tareas_token):
        raise HTTPException(status_code=403, detail="No autorizado")
    if forzar not in (None, "semanal", "mensual"):
        raise HTTPException(status_code=400, detail="forzar debe ser semanal o mensual")
    return resumenes.tarea_diaria(forzar)
