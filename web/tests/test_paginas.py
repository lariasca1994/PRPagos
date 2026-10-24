"""
Las páginas públicas se renderizan (plantillas Jinja2 con la firma actual de
Starlette: TemplateResponse(request, nombre, contexto)). Sin base de datos.
"""
import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.mark.parametrize("ruta", ["/login", "/registro", "/recuperar"])
def test_paginas_publicas_se_renderizan(ruta):
    respuesta = TestClient(app).get(ruta)
    assert respuesta.status_code == 200
    assert "PRPagos" in respuesta.text


@pytest.mark.parametrize("ruta", ["/login/codigo", "/login/elegir", "/login/correo", "/login/configurar"])
def test_pasos_sin_ingreso_vuelven_al_login(ruta):
    respuesta = TestClient(app).get(ruta, follow_redirects=False)
    assert respuesta.status_code == 303
    assert respuesta.headers["location"].startswith("/login")
