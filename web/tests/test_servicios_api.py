"""
Una sola lógica: las reglas viven en app/servicios/ y la API del escritorio
(routers/api.py) responde con exactamente los mismos mensajes que la web.
Sin base de datos: el repositorio se reemplaza por datos de prueba.
"""
from datetime import date

import pytest
from fastapi.testclient import TestClient

from app import finanzas, repositorio
from app.main import app
from app.servicios import ErrorServicio, dashboard, perfil, registros, usuarios

cliente = TestClient(app)


# ───────────────────────── Servicios ─────────────────────────


@pytest.mark.parametrize("campos, mensaje", [
    (("OTRO", "10", "2026-09-01", "x"), "Elige si es un ingreso o un gasto"),
    (("GASTO", "0", "2026-09-01", "x"), "Monto inválido"),
    (("GASTO", "abc", "2026-09-01", "x"), "Monto inválido"),
    (("GASTO", "10", "01/09/2026", "x"), "Fecha inválida"),
    (("GASTO", "10", "2026-09-01", "   "), "Escribe el concepto"),
])
def test_validacion_de_registros(campos, mensaje):
    tipo, monto, fecha, concepto = campos
    with pytest.raises(ErrorServicio, match=mensaje):
        registros.crear(23, tipo, monto, fecha, None, concepto)


def test_registro_valido_con_coma_decimal(monkeypatch):
    guardados = []
    monkeypatch.setattr(repositorio, "insertar_registro", lambda *a: guardados.append(a))
    monkeypatch.setattr(repositorio, "presupuesto_anual", lambda *a: {})
    assert registros.crear(23, "GASTO", "1234,5", "2026-09-01", "6", " Mercado ") == "Registro guardado"
    assert guardados == [("GASTO", 1234.5, date(2026, 9, 1), "Mercado", 6, 23)]


def test_filtros_vacios_o_invalidos_se_ignoran():
    f = registros.parsear_filtros("OTRO", "  ", "abc", "10,5", "no-fecha", "2026-09-30")
    assert f == {"tipo": None, "concepto": None, "monto_min": None, "monto_max": 10.5,
                 "fecha_desde": None, "fecha_hasta": date(2026, 9, 30), "categoria": None}
    assert registros.parsear_filtros(categoria="0")["categoria"] == 0
    assert registros.parsear_filtros(categoria="6")["categoria"] == 6
    assert registros.parsear_filtros(categoria="x")["categoria"] is None


def test_solo_admin_elimina_registros():
    with pytest.raises(ErrorServicio):
        registros.eliminar(23, False, 5)


@pytest.mark.parametrize("valores", [("60", "30", "20"), ("a", "30", "20"), ("-10", "90", "20")])
def test_plan_debe_sumar_100(valores):
    with pytest.raises(ErrorServicio):
        perfil.guardar_plan(23, *valores)


def test_admin_no_se_suspende_ni_elimina_a_si_mismo():
    with pytest.raises(ErrorServicio, match="No puedes suspender tu propia cuenta"):
        usuarios.suspender(1, 1)
    with pytest.raises(ErrorServicio, match="No puedes eliminar tu propia cuenta"):
        usuarios.eliminar(1, 1)


@pytest.fixture
def datos_mes(monkeypatch):
    monkeypatch.setattr(repositorio, "totales", lambda *a: (4_850_000.0, 3_870_000.0, 12))
    monkeypatch.setattr(repositorio, "serie_meses", lambda *a: [(date(2026, 9, 1), 4_850_000.0, 3_870_000.0)])
    monkeypatch.setattr(repositorio, "gastos_por_categoria",
                        lambda *a: [("Vivienda", "NECESIDADES", 1_250_000.0), ("Ocio", "DESEOS", 119_000.0)])
    monkeypatch.setattr(repositorio, "plan", lambda *a: {"NECESIDADES": 50.0, "DESEOS": 30.0, "AHORRO": 20.0})
    monkeypatch.setattr(repositorio, "gastos_por_grupo",
                        lambda *a: {"NECESIDADES": 2_400_000.0, "DESEOS": 600_000.0, "AHORRO": 870_000.0})
    monkeypatch.setattr(repositorio, "categorias", lambda *a: [
        {"id": 4, "nombre": "Vivienda", "tipo": "GASTO", "grupo": "NECESIDADES", "propia": False, "usos": 1}])
    monkeypatch.setattr(finanzas, "hoy", lambda: date(2026, 10, 17))


def test_vista_del_dashboard_ya_formateada(datos_mes):
    v = dashboard.vista(23, date(2026, 9, 1))
    assert v["tarjetas"]["ingresos"] == "$ 4.850.000,00"
    assert v["tarjetas"]["balance"] == "$ 980.000,00" and v["tarjetas"]["balance_positivo"]
    assert v["tarjetas"]["ahorro"] == "20 %" and v["tarjetas"]["movimientos"] == "12 movimientos"
    assert v["titulo_plan"] == "Plan 50/30/20"
    assert [m["etiqueta"] for m in v["serie"]] == ["abr 26", "may 26", "jun 26", "jul 26", "ago 26", "sep 26"]
    necesidades = v["plan"][0]
    assert necesidades["texto_presupuesto"] == "$ 2.425.000,00" and necesidades["usado"] == "99 %"
    assert necesidades["estado"] == "Dentro del plan" and necesidades["clase_estado"] == "bien"
    assert v["meses"][0] == {"valor": "2026-10", "texto": "Octubre 2026"}
    # Clic en la dona: los registros de esa categoría en ese mes ("Ocio" no es del usuario -> 0).
    assert v["categorias"][0]["filtro"] == {"tipo": "GASTO", "categoria": "4", "fecha_desde": "2026-09-01",
                                            "fecha_hasta": "2026-09-30"}
    assert v["serie"][-1]["mes"] == "2026-09"


def test_mes_fuera_de_rango_vuelve_al_actual(datos_mes):
    assert dashboard.mes_elegido("2020-01") == date(2026, 10, 1)
    assert dashboard.mes_elegido("2026-09") == date(2026, 9, 1)


# ───────────────────────── API (mismos mensajes que la web) ─────────────────────────


def test_api_sin_sesion_responde_401_con_el_motivo():
    r = cliente.get("/api/dashboard", headers={"Authorization": "Bearer token-falso"})
    assert r.status_code == 401
    assert r.json()["error"] == "Tu sesión no es válida; ingresa de nuevo."


def test_api_ingreso_con_mismo_mensaje_que_la_web(monkeypatch):
    monkeypatch.setattr(repositorio, "obtener_credenciales", lambda **k: None)
    r = cliente.post("/api/acceso/ingresar", json={"usuario": "nadie@correo.com", "contrasena": "x"})
    assert r.status_code == 400 and r.json() == {"error": "Usuario o contraseña incorrectos"}
    web = cliente.post("/login", data={"usuario": "nadie@correo.com", "contrasena": "x"}, follow_redirects=False)
    assert "Usuario%20o%20contrase%C3%B1a%20incorrectos" in web.headers["location"]


def test_api_token_parcial_vencido(monkeypatch):
    r = cliente.post("/api/acceso/codigo", json={"token": "no-es-un-token", "codigo": "123456"})
    assert r.status_code == 400 and r.json()["error"].startswith("Tu ingreso expiró")


def test_api_registro_valida_igual_que_la_web(monkeypatch):
    r = cliente.post("/api/acceso/registro", json={"usuario": "no-es-correo", "nombre": "Ana",
                                                   "contrasena": "12345678", "confirmacion": "12345678"})
    assert r.json() == {"error": "Escribe un correo válido (por ejemplo, nombre@dominio.com)"}
