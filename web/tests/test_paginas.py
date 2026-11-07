"""
Las páginas públicas se renderizan (plantillas Jinja2 con la firma actual de
Starlette: TemplateResponse(request, nombre, contexto)). Sin base de datos.
"""
import pytest
from fastapi.testclient import TestClient

from app import repositorio
from app.main import app


@pytest.mark.parametrize("ruta", ["/login", "/registro", "/recuperar"])
def test_paginas_publicas_se_renderizan(ruta):
    respuesta = TestClient(app).get(ruta)
    assert respuesta.status_code == 200
    assert "Simulador de pagos" in respuesta.text


def test_olvide_mi_contrasena_solo_pide_el_correo():
    texto = TestClient(app).get("/recuperar").text
    assert 'name="correo"' in texto
    for campo in ("nombre_registrado", 'name="codigo"', "nueva_contrasena"):
        assert campo not in texto


@pytest.mark.parametrize("ruta", ["/login/codigo", "/login/elegir", "/login/correo", "/login/configurar"])
def test_pasos_sin_ingreso_vuelven_al_login(ruta):
    respuesta = TestClient(app).get(ruta, follow_redirects=False)
    assert respuesta.status_code == 303
    assert respuesta.headers["location"].startswith("/login")


def test_el_enlace_guarda_el_token_en_una_cookie_y_lo_quita_de_la_direccion():
    cliente = TestClient(app)
    respuesta = cliente.get("/restablecer?token=" + "a" * 43, follow_redirects=False)
    assert respuesta.status_code == 303
    assert respuesta.headers["location"] == "/restablecer"
    assert "restablecer=" in respuesta.headers["set-cookie"]
    assert "httponly" in respuesta.headers["set-cookie"].lower()


def test_enlace_vencido_no_muestra_el_formulario(monkeypatch):
    monkeypatch.setattr(repositorio, "cuenta_por_enlace", lambda *a: None)
    texto = TestClient(app).get("/restablecer").text
    assert "no es válido o ya venció" in texto
    assert "nueva_contrasena" not in texto
    assert "Pedir un enlace nuevo" in texto


def test_enlace_vigente_pide_solo_la_contrasena_nueva(monkeypatch):
    monkeypatch.setattr(repositorio, "cuenta_por_enlace", lambda *a: 7)
    cliente = TestClient(app)
    cliente.cookies.set("restablecer", "b" * 43, path="/restablecer")
    texto = cliente.get("/restablecer").text
    assert "nueva_contrasena" in texto and 'name="confirmacion"' in texto
    for campo in ("nombre_registrado", 'name="codigo"', 'name="correo"'):
        assert campo not in texto
