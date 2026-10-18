"""
Presupuesto mensual por categoría: comparación con los registros, textos,
guardado por alcance, copia del mes anterior y la misma vista por la API.
Sin base de datos: el repositorio se reemplaza por datos de prueba.
"""
from datetime import date

import pytest
from fastapi.testclient import TestClient

from app import finanzas, repositorio
from app.main import app
from app.seguridad import crear_token
from app.servicios import ErrorServicio, presupuesto, registros

OCT = date(2026, 10, 1)
CATEGORIAS = [
    {"id": 1, "nombre": "Salario", "tipo": "INGRESO", "grupo": None, "propia": False, "usos": 3},
    {"id": 4, "nombre": "Vivienda", "tipo": "GASTO", "grupo": "NECESIDADES", "propia": False, "usos": 2},
    {"id": 6, "nombre": "Mercado", "tipo": "GASTO", "grupo": "NECESIDADES", "propia": False, "usos": 5},
    {"id": 10, "nombre": "Restaurantes", "tipo": "GASTO", "grupo": "DESEOS", "propia": False, "usos": 1},
    {"id": 14, "nombre": "Ahorro", "tipo": "GASTO", "grupo": "AHORRO", "propia": False, "usos": 0},
]


@pytest.fixture
def datos(monkeypatch):
    plan = {(1, OCT): 5_000_000.0, (4, OCT): 1_500_000.0, (6, OCT): 800_000.0, (10, OCT): 300_000.0,
            (14, OCT): 1_000_000.0, (6, date(2026, 9, 1)): 750_000.0}
    real = {(1, OCT, "INGRESO"): 5_000_000.0, (4, OCT, "GASTO"): 1_500_000.0, (6, OCT, "GASTO"): 900_000.0,
            (10, OCT, "GASTO"): 120_000.0, (None, OCT, "GASTO"): 80_000.0}
    guardados = []

    def presupuesto_anual(usuario_id, desde, hasta):
        return {k: v for k, v in plan.items() if desde <= k[1] < hasta}

    monkeypatch.setattr(repositorio, "categorias", lambda *a: CATEGORIAS)
    monkeypatch.setattr(repositorio, "presupuesto_anual", presupuesto_anual)
    monkeypatch.setattr(repositorio, "real_por_categoria_mes",
                        lambda u, d, h: {k: v for k, v in real.items() if d <= k[1] < h})
    monkeypatch.setattr(repositorio, "plan", lambda *a: {"NECESIDADES": 50.0, "DESEOS": 30.0, "AHORRO": 20.0})
    monkeypatch.setattr(repositorio, "buscar_registros", lambda *a: [
        {"id_pago": 7, "tipo": "GASTO", "monto": 1_500_000.0, "fecha": date(2026, 10, 2), "concepto": "Arriendo",
         "categoria": "Vivienda", "categoria_id": 4},
        {"id_pago": 9, "tipo": "GASTO", "monto": 900_000.0, "fecha": date(2026, 10, 5), "concepto": "Mercado",
         "categoria": "Mercado", "categoria_id": 6}])
    monkeypatch.setattr(repositorio, "guardar_presupuesto", lambda u, montos, d: guardados.append((montos, d)))
    monkeypatch.setattr(finanzas, "hoy", lambda: date(2026, 10, 17))
    return guardados


def test_vista_compara_presupuesto_con_registros(datos):
    v = presupuesto.vista(23, 2026, 10)
    assert v["nombre_mes"] == "Octubre 2026"
    assert v["tarjetas"]["ingresos"] == "$ 5.000.000,00"
    assert v["tarjetas"]["gastos"] == "$ 2.600.000,00"  # incluye los 80.000 sin categoría
    assert v["tarjetas"]["gastos_plan"] == "de $ 3.600.000,00 presupuestados"
    assert v["tarjetas"]["quedan"] == "$ 2.400.000,00" and v["tarjetas"]["quedan_positivo"]
    assert v["tarjetas"]["quedan_plan"] == "Presupuestado: $ 1.400.000,00"
    assert v["tarjetas"]["usado"] == "72 %" and v["tarjetas"]["usado_clase"] == "bien"

    claves = [s["clave"] for s in v["secciones"]]
    assert claves == ["INGRESO", "NECESIDADES", "DESEOS", "AHORRO", "SIN_GRUPO"]
    necesidades = v["secciones"][1]
    mercado = necesidades["filas"][1]
    assert mercado["nombre"] == "Mercado" and mercado["valor_campo"] == "800000"
    assert mercado["usado"] == "113 %" and mercado["barra"] == 100
    assert mercado["estado"] == "Excedido" and mercado["clase_estado"] == "mal"
    assert mercado["filtro"] == {"tipo": "GASTO", "categoria": "6", "fecha_desde": "2026-10-01",
                                 "fecha_hasta": "2026-10-31"}
    assert necesidades["total"]["texto_presupuesto"] == "$ 2.300.000,00"
    assert necesidades["meta"] == "46 % de los ingresos presupuestados · meta del plan 50 %"
    ahorro = v["secciones"][3]["filas"][0]
    assert ahorro["estado"] == "Por debajo de la meta" and ahorro["clase_estado"] == "alerta"
    sin_grupo = v["secciones"][4]["filas"][0]
    assert not sin_grupo["editable"] and sin_grupo["filtro"]["categoria"] == "0"

    # Distribución: lo que no se asignó a ningún grupo aparece como "Sin asignar".
    assert [d["etiqueta"] for d in v["distribucion"]] == [
        "Necesidades (46 %)", "Deseos (6 %)", "Ahorro y deudas (20 %)", "Sin asignar (28 %)"]
    assert len(v["serie"]) == 12 and v["serie"][9] == {"mes": 10, "etiqueta": "oct 26", "gastos_plan": 3_600_000.0,
                                                       "gastos_real": 2_600_000.0}
    assert v["anual"][9]["actual"] and v["anual"][9]["quedan_plan"] == "$ 1.400.000,00"
    assert v["total_anual"]["mes"] == "Total 2026"
    assert v["mayores_gastos"][0]["concepto"] == "Arriendo" and v["mayores_gastos"][0]["parte"] == "58 %"


def test_mes_sin_presupuesto(datos):
    v = presupuesto.vista(23, 2026, 3)
    assert not v["hay_presupuesto"] and v["distribucion"] == []
    assert v["tarjetas"]["usado"] == "—"
    assert v["secciones"][0]["filas"][0]["estado"] == "Sin presupuesto"


@pytest.mark.parametrize("texto, valor", [
    ("1.500.000", 1_500_000.0), ("1500000", 1_500_000.0), ("1500000,50", 1_500_000.5),
    ("1500000.5", 1_500_000.5), ("$ 250.000", 250_000.0), ("", 0.0), ("  ", 0.0), ("0", 0.0),
    ("abc", None), ("-5", None),
])
def test_montos_aceptados(texto, valor):
    assert presupuesto.monto(texto) == valor


def test_guardar_por_alcance(datos):
    assert presupuesto.guardar(23, 2026, 10, {"6": "850.000", "10": ""}, "resto") == (
        "Presupuesto de octubre a diciembre de 2026 guardado")
    montos, detalle = datos[-1]
    assert sorted({m for _, m, _ in montos}) == [date(2026, 10, 1), date(2026, 11, 1), date(2026, 12, 1)]
    assert (6, date(2026, 11, 1), 850_000.0) in montos and (10, date(2026, 12, 1), 0.0) in montos
    assert presupuesto.guardar(23, 2026, 10, {"6": "1"}, "anio") == "Presupuesto de todo 2026 guardado"
    assert len(datos[-1][0]) == 12
    assert presupuesto.guardar(23, 2026, 10, {"6": "1"}, "mes") == "Presupuesto de octubre 2026 guardado"


@pytest.mark.parametrize("montos, alcance, mensaje", [
    ({"6": "abc"}, "mes", "Monto inválido en Mercado"),
    ({"999": "10"}, "mes", "Categoría no válida para el presupuesto"),
    ({}, "mes", "No hay montos para guardar"),
    ({"6": "10"}, "otro", "Elige a qué meses aplicar el presupuesto"),
])
def test_guardar_valida(datos, montos, alcance, mensaje):
    with pytest.raises(ErrorServicio, match=mensaje):
        presupuesto.guardar(23, 2026, 10, montos, alcance)


def test_copiar_mes_anterior(datos):
    assert presupuesto.copiar_mes_anterior(23, 2026, 10) == (
        "Se copió el presupuesto de septiembre 2026 a octubre 2026")
    montos, _ = datos[-1]
    assert (6, OCT, 750_000.0) in montos and (4, OCT, 0.0) in montos
    with pytest.raises(ErrorServicio, match="El mes anterior no tiene presupuesto"):
        presupuesto.copiar_mes_anterior(23, 2026, 3)


def test_periodo_fuera_de_rango(datos):
    assert presupuesto.anio_elegido("2010") == 2026 and presupuesto.anio_elegido("2027") == 2027
    assert presupuesto.mes_elegido("13", 2026) == 10 and presupuesto.mes_elegido("", 2027) == 1


def test_registro_nuevo_informa_su_presupuesto(datos, monkeypatch):
    monkeypatch.setattr(repositorio, "insertar_registro", lambda *a: None)
    mensaje = registros.crear(23, "GASTO", "50000", "2026-10-18", "6", "Fruta")
    assert mensaje == ("Registro guardado · Mercado en octubre 2026: $ 900.000,00 de $ 800.000,00 (113 %)"
                       " · Excedido")
    assert registros.crear(23, "GASTO", "50000", "2026-10-18", "", "Fruta") == "Registro guardado"


def test_api_devuelve_la_misma_vista_y_mensajes(datos, monkeypatch):
    monkeypatch.setattr(repositorio, "obtener_estado_usuario", lambda *a: {"activo": 1, "rol": "usuario"})
    cliente = TestClient(app)
    cabeceras = {"Authorization": f"Bearer {crear_token(23, 'ana@correo.com', 'Ana')}"}
    r = cliente.get("/api/presupuesto?anio=2026&mes=10", headers=cabeceras)
    assert r.status_code == 200 and r.json() == presupuesto.vista(23, 2026, 10)
    r = cliente.put("/api/presupuesto", headers=cabeceras,
                    json={"anio": 2026, "mes": 10, "alcance": "mes", "montos": {"6": "900000"}})
    assert r.json() == {"mensaje": "Presupuesto de octubre 2026 guardado"}
    r = cliente.put("/api/presupuesto", headers=cabeceras,
                    json={"anio": 2026, "mes": 10, "alcance": "mes", "montos": {"6": "x"}})
    assert r.status_code == 400 and r.json() == {"error": "Monto inválido en Mercado"}
    web = cliente.post("/presupuesto", data={"anio": "2026", "mes": "10", "alcance": "mes", "monto_6": "x"},
                       cookies={"sesion": cabeceras["Authorization"][7:]}, follow_redirects=False)
    assert "Monto%20inv%C3%A1lido%20en%20Mercado" in web.headers["location"]
