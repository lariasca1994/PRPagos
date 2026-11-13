"""
Pantallas de estado propias: /health para la pantalla de mantenimiento, la
pantalla de mantenimiento cuando Oracle falla (503, sin repetir la acción) y
el 404 propio del proyecto. Sin base de datos real.
"""
import oracledb
from fastapi.testclient import TestClient

from app import main, repositorio
from app.main import app


class _Cursor:
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def execute(self, sql):
        assert sql == "SELECT 1 FROM DUAL"

    def fetchone(self):
        return (1,)


class _Conexion:
    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def cursor(self):
        return _Cursor()


def _sin_base(*a, **k):
    raise oracledb.DatabaseError("sin conexión")


def test_health_responde_ok_con_la_base(monkeypatch):
    monkeypatch.setattr(main, "obtener_conexion", lambda: _Conexion())
    respuesta = TestClient(app).get("/health")
    assert respuesta.status_code == 200
    assert respuesta.json() == {"estado": "ok"}
    assert respuesta.headers["cache-control"] == "no-store"


def test_health_responde_503_sin_la_base(monkeypatch):
    monkeypatch.setattr(main, "obtener_conexion", _sin_base)
    respuesta = TestClient(app).get("/health")
    assert respuesta.status_code == 503
    assert respuesta.json() == {"estado": "sin_base"}


def test_falla_de_oracle_muestra_la_pantalla_de_mantenimiento(monkeypatch):
    monkeypatch.setattr(repositorio, "obtener_estado_usuario", _sin_base)
    monkeypatch.setattr("app.dependencias.leer_token", lambda t: {"usuario_id": 1})
    respuesta = TestClient(app).get("/dashboard", headers={"Authorization": "Bearer x"})
    assert respuesta.status_code == 503
    texto = respuesta.text
    assert "Estamos en mantenimiento" in texto
    assert "Ninguna acción quedó a medias" in texto
    assert "/static/estados.js" in texto
    assert respuesta.headers["cache-control"] == "no-store"


def test_la_api_sigue_respondiendo_json_si_falla_oracle(monkeypatch):
    monkeypatch.setattr(repositorio, "obtener_estado_usuario", _sin_base)
    monkeypatch.setattr("app.dependencias.leer_token", lambda t: {"usuario_id": 1})
    respuesta = TestClient(app).get("/api/sesion", headers={"Authorization": "Bearer x"})
    assert respuesta.status_code == 503
    assert "error" in respuesta.json()


def test_pagina_inexistente_muestra_el_404_propio():
    respuesta = TestClient(app).get("/registros-que-no-existen/12")
    assert respuesta.status_code == 404
    assert "NO ENCONTRADA" in respuesta.text
    assert "Este registro no está en tus cuentas" in respuesta.text
    assert "/registros-que-no-existen/12" in respuesta.text


def test_404_de_la_api_sigue_siendo_json():
    respuesta = TestClient(app).get("/api/no-existe")
    assert respuesta.status_code == 404
    assert respuesta.headers["content-type"].startswith("application/json")


def test_la_portada_carga_los_efectos_y_el_acceso_de_una_sola_pantalla():
    texto = TestClient(app).get("/login").text
    assert "/static/efectos.js" in texto and "/static/acceso.js" in texto
    assert texto.index("/static/efectos.js") < texto.index("/static/acceso.js")


def test_registro_pone_el_panel_de_marca_al_otro_lado():
    assert 'class="portada invertida"' in TestClient(app).get("/registro").text
    assert 'class="portada"' in TestClient(app).get("/login").text
