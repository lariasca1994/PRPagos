"""
Acceso a compartido/: las consultas SQL y las plantillas de correo que usan
IGUAL la web y el escritorio (escritorio/.../compartido/Compartido.java).
Una sola fuente para las dos versiones: los mismos números y los mismos
correos.

La carpeta se busca hacia arriba desde este archivo: en el repositorio está
en la raíz (web/app/ -> ../../compartido) y en la imagen de Cloud Run el
Dockerfile la copia a /app/compartido.
"""
import html
import re
from functools import lru_cache
from pathlib import Path


def _carpeta() -> Path:
    for padre in Path(__file__).resolve().parents:
        candidato = padre / "compartido"
        if candidato.is_dir():
            return candidato
    raise RuntimeError("No se encontró la carpeta compartido/ (consultas y plantillas de correo)")


@lru_cache(maxsize=None)
def consulta(nombre: str) -> str:
    """SQL de compartido/consultas/<nombre>.sql sin los comentarios de línea."""
    texto = (_carpeta() / "consultas" / f"{nombre}.sql").read_text(encoding="utf-8")
    return "\n".join(l for l in texto.splitlines() if not l.lstrip().startswith("--")).strip()


@lru_cache(maxsize=None)
def plantilla(nombre: str) -> str:
    return (_carpeta() / "correo" / nombre).read_text(encoding="utf-8")


class Crudo(str):
    """Valor que ya es HTML (filas, bloques): no se escapa al rellenar."""


def rellenar(texto: str, valores: dict[str, object]) -> str:
    """Reemplaza {{clave}}. Los valores se escapan como HTML salvo los Crudo.
    Una clave sin valor es un error: así una plantilla nunca sale a medias."""
    def reemplazo(m: re.Match) -> str:
        clave = m.group(1)
        if clave not in valores:
            raise KeyError(f"Falta el valor de {{{{{clave}}}}}")
        valor = valores[clave]
        return valor if isinstance(valor, Crudo) else html.escape(str(valor), quote=True)

    return re.sub(r"\{\{([a-z_]+)\}\}", reemplazo, texto)
