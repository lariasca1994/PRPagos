"""
Resúmenes por correo: el semanal (viernes, los 7 días que terminan ese día)
y el general (día 29 de cada mes; en febrero, el último día). Los dispara
cada día GitHub Actions (/tareas/resumenes) y también el botón "Enviarme el
resumen ahora" del dashboard. Mismo contenido que el escritorio
(finanzas/Resumenes.java): mismas consultas y misma plantilla.
"""
import logging
from datetime import date

from . import correo, finanzas, repositorio
from .config import settings

logger = logging.getLogger("prpagos.resumenes")


def datos(usuario_id: int, nombre: str, periodo: finanzas.Periodo) -> finanzas.DatosResumen:
    ingresos, gastos, movimientos = repositorio.totales(usuario_id, periodo.desde, periodo.hasta)
    return finanzas.DatosResumen(
        nombre=nombre,
        periodo=periodo,
        ingresos=ingresos,
        gastos=gastos,
        movimientos=movimientos,
        categorias=repositorio.gastos_por_categoria(usuario_id, periodo.desde, periodo.hasta),
        pcts=repositorio.plan(usuario_id),
        gastos_grupo=repositorio.gastos_por_grupo(usuario_id, periodo.desde, periodo.hasta),
    )


def enviar(usuario_id: int, correo_destino: str, nombre: str, periodo: finanzas.Periodo) -> None:
    contenido = finanzas.html_resumen(datos(usuario_id, nombre, periodo), settings.url_publica)
    correo.enviar(correo_destino, nombre, finanzas.asunto_resumen(periodo), contenido)


def periodos_de_hoy(dia: date, forzar: str | None = None) -> list[finanzas.Periodo]:
    """Qué resúmenes tocan hoy (o el forzado, para probar)."""
    periodos = []
    if forzar == "semanal" or (forzar is None and finanzas.es_dia_semanal(dia)):
        periodos.append(finanzas.periodo_semanal(dia))
    if forzar == "mensual" or (forzar is None and finanzas.es_dia_mensual(dia)):
        periodos.append(finanzas.periodo_mensual(dia))
    return periodos


def tarea_diaria(forzar: str | None = None) -> dict:
    """Envía a cada cuenta activa con resúmenes activados los que tocan hoy.
    Cada envío se marca antes en TBEnviosResumen: si la tarea se repite, no
    se duplica; si Brevo falla, se desmarca para el próximo intento."""
    dia = finanzas.hoy()
    informe = {"fecha": dia.isoformat(), "enviados": 0, "omitidos": 0, "errores": 0, "periodos": []}
    for periodo in periodos_de_hoy(dia, forzar):
        informe["periodos"].append(f"{periodo.tipo} {periodo.clave}")
        for d in repositorio.destinatarios_resumen():
            if not repositorio.marcar_envio(d["usuario_id"], periodo.tipo, periodo.clave):
                informe["omitidos"] += 1
                continue
            try:
                enviar(d["usuario_id"], d["usuario"], d["nombre"], periodo)
                informe["enviados"] += 1
            except Exception:  # se reintenta en la próxima ejecución
                logger.exception("No se pudo enviar el resumen %s a la cuenta %s", periodo.tipo, d["usuario_id"])
                repositorio.desmarcar_envio(d["usuario_id"], periodo.tipo, periodo.clave)
                informe["errores"] += 1
    return informe
