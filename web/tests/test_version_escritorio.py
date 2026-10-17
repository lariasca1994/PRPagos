"""POST /tareas/version-escritorio: solo con el token y solo Releases de GitHub del proyecto."""
import pytest
from fastapi import HTTPException

from app.routers import tareas

URL_OK = ("https://github.com/lariasca1994/PRPagos/releases/download/"
          "escritorio-v1.0.7/PRPagos-Escritorio-1.0.7.zip")
SHA_OK = "a" * 64


@pytest.fixture
def registradas(monkeypatch: pytest.MonkeyPatch) -> list[tuple[str, str, str]]:
    monkeypatch.setattr(tareas.settings, "tareas_token", "token-de-prueba")
    guardadas: list[tuple[str, str, str]] = []
    monkeypatch.setattr(tareas.repositorio, "publicar_version_escritorio",
                        lambda v, u, s: guardadas.append((v, u, s)))
    return guardadas


def _llamar(version: str = "1.0.7", url: str = URL_OK, sha: str = SHA_OK,
            token: str = "token-de-prueba") -> dict:
    datos = tareas.VersionEscritorio(version=version, url=url, sha256=sha)
    return tareas.tarea_version_escritorio(datos, x_tarea_token=token)


def test_registra_version_valida(registradas: list) -> None:
    assert _llamar() == {"version": "1.0.7", "registrada": True}
    assert registradas == [("1.0.7", URL_OK, SHA_OK)]


@pytest.mark.parametrize("cambios, codigo", [
    ({"token": "otro"}, 403),
    ({"version": "1.0"}, 400),
    ({"version": "1.0.7-beta"}, 400),
    ({"url": URL_OK.replace("https://", "http://")}, 400),
    ({"url": URL_OK.replace("github.com", "github.com.evil.io")}, 400),
    ({"url": "https://github.com/otro/repo/releases/download/x/PRPagos.zip"}, 400),
    ({"sha": "A" * 64}, 400),
    ({"sha": "a" * 63}, 400),
])
def test_rechaza_datos_invalidos(registradas: list, cambios: dict, codigo: int) -> None:
    with pytest.raises(HTTPException) as error:
        _llamar(**cambios)
    assert error.value.status_code == codigo
    assert registradas == []


def test_sin_token_configurado_no_acepta_nada(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(tareas.settings, "tareas_token", "")
    with pytest.raises(HTTPException) as error:
        _llamar(token="")
    assert error.value.status_code == 403
