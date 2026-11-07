"""
/api/version-escritorio con el canal Microsoft Store: version_store_minima y
store_product_id solo se envían si están configurados y tienen la forma esperada.
Sin base de datos.
"""
import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.main import app
from app.routers import api

PUBLICADA = {"version": "1.0.26", "url": "https://github.com/x", "sha256": "a" * 64}


@pytest.fixture(autouse=True)
def version_publicada(monkeypatch):
    monkeypatch.setattr(api.repositorio, "ultima_version_escritorio", lambda: dict(PUBLICADA))


def _respuesta() -> dict:
    respuesta = TestClient(app).get("/api/version-escritorio")
    assert respuesta.status_code == 200
    return respuesta.json()


def test_sin_variables_de_la_store_no_cambia_nada(monkeypatch):
    monkeypatch.setattr(settings, "store_version_minima", "")
    monkeypatch.setattr(settings, "store_product_id", "")
    assert _respuesta() == PUBLICADA


def test_con_variables_de_la_store_agrega_los_campos(monkeypatch):
    monkeypatch.setattr(settings, "store_version_minima", "1.0.30")
    monkeypatch.setattr(settings, "store_product_id", "9NBLGGH4R315")
    assert _respuesta() == {**PUBLICADA, "version_store_minima": "1.0.30", "store_product_id": "9NBLGGH4R315"}


@pytest.mark.parametrize("minima, producto", [("1.0", "9NBLGGH4R315"), ("1.0.30", "no válido/&"),
                                              ("1.0.30 ; x", "corto")])
def test_valores_con_forma_invalida_no_se_envian(monkeypatch, minima, producto):
    monkeypatch.setattr(settings, "store_version_minima", minima)
    monkeypatch.setattr(settings, "store_product_id", producto)
    datos = api.datos_store()
    if minima == "1.0.30":
        assert "store_product_id" not in datos
    else:
        assert "version_store_minima" not in datos


def test_sin_version_publicada_igual_responde(monkeypatch):
    monkeypatch.setattr(api.repositorio, "ultima_version_escritorio", lambda: None)
    monkeypatch.setattr(settings, "store_version_minima", "")
    monkeypatch.setattr(settings, "store_product_id", "")
    assert _respuesta() == {}
