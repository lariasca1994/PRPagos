from datetime import date

import pytest

from app import compartido, finanzas as f, resumenes
from app import seguridad as s


@pytest.mark.parametrize("valor, texto", [
    (0, "$ 0,00"),
    (1850000, "$ 1.850.000,00"),
    (96750.5, "$ 96.750,50"),
    (-1234.567, "-$ 1.234,57"),
    (999.999, "$ 1.000,00"),
])
def test_moneda(valor, texto):
    assert f.moneda(valor) == texto


def test_periodo_semanal_de_sabado_a_viernes():
    viernes = date(2026, 10, 16)
    p = f.periodo_semanal(viernes)
    assert (p.desde, p.hasta, p.ultimo_dia) == (date(2026, 10, 10), date(2026, 10, 17), viernes)
    assert f.texto_periodo(p) == "Del 10 al 16 de octubre de 2026"
    assert f.es_dia_semanal(viernes) and not f.es_dia_semanal(date(2026, 10, 17))


def test_periodo_semanal_entre_meses_y_anios():
    assert f.texto_periodo(f.periodo_semanal(date(2026, 11, 3))) == "Del 28 de octubre al 3 de noviembre de 2026"
    assert f.texto_periodo(f.periodo_semanal(date(2027, 1, 2))) == (
        "Del 27 de diciembre de 2026 al 2 de enero de 2027")


@pytest.mark.parametrize("dia, toca", [
    (date(2026, 10, 29), True),
    (date(2026, 10, 28), False),
    (date(2026, 10, 30), False),
    (date(2027, 2, 28), True),    # febrero no bisiesto: el último día
    (date(2028, 2, 28), False),   # bisiesto: el 28 no es el último
    (date(2028, 2, 29), True),
])
def test_dia_del_resumen_mensual(dia, toca):
    assert f.es_dia_mensual(dia) is toca


def test_periodo_mensual():
    p = f.periodo_mensual(date(2026, 10, 29))
    assert (p.desde, p.hasta, p.clave) == (date(2026, 10, 1), date(2026, 10, 30), "2026-10")


def test_meses_atras_y_completar():
    oct26 = date(2026, 10, 1)
    assert f.meses_atras(oct26, 10) == date(2025, 12, 1)
    assert f.meses_atras(oct26, -3) == date(2027, 1, 1)
    serie = f.completar_meses(oct26, [(date(2026, 8, 1), 100, 50)])
    assert [m for m, _, _ in serie] == [date(2026, m, 1) for m in range(5, 11)]
    assert serie[3] == (date(2026, 8, 1), 100.0, 50.0) and serie[0][1:] == (0.0, 0.0)
    assert f.etiqueta_mes_corta(oct26) == "oct 26"


def test_plan_503020_estados():
    pcts = {"NECESIDADES": 50, "DESEOS": 30, "AHORRO": 20}
    plan = {p.grupo: p for p in f.calcular_plan(1000, pcts, {"NECESIDADES": 400, "DESEOS": 350, "AHORRO": 200})}
    assert plan["NECESIDADES"].presupuesto == 500 and plan["NECESIDADES"].estado == "Dentro del plan"
    assert plan["DESEOS"].estado == "Excedido" and round(plan["DESEOS"].usado) == 117
    assert plan["AHORRO"].estado == "Meta cumplida"
    sin_ingresos = f.calcular_plan(0, pcts, {})
    assert all(p.estado == "Sin ingresos registrados" for p in sin_ingresos)


def _datos(tipo: str) -> f.DatosResumen:
    periodo = f.periodo_semanal(date(2026, 10, 16)) if tipo == "semanal" else f.periodo_mensual(date(2026, 10, 29))
    return f.DatosResumen(
        nombre="Ana <Pérez>", periodo=periodo, ingresos=3000000, gastos=2499150, movimientos=5,
        categorias=[("Vivienda", "NECESIDADES", 1850000), ("Sin categoría", "SIN_GRUPO", 649150)],
        pcts={"NECESIDADES": 50, "DESEOS": 30, "AHORRO": 20},
        gastos_grupo={"NECESIDADES": 1850000, "SIN_GRUPO": 649150},
    )


def test_correo_semanal_sin_plan_y_escapado():
    html = f.html_resumen(_datos("semanal"), "https://ejemplo.org")
    assert "{{" not in html and "Ana &lt;Pérez&gt;" in html
    assert "Tu resumen semanal" in html and "Plan 50/30/20" not in html
    assert "$ 1.850.000,00" in html and "74 %" in html


def test_correo_mensual_con_plan():
    html = f.html_resumen(_datos("mensual"), "https://ejemplo.org")
    assert "Plan 50/30/20" in html and "Excedido" in html and "Tu resumen de octubre" in html
    assert f.asunto_resumen(_datos("mensual").periodo) == "Tu resumen de octubre en PRPagos"


def test_correo_de_codigo():
    html = f.html_codigo("Ana", "012345", 15)
    assert "012345" in html and "15 minutos" in html and "{{" not in html


def test_rellenar_exige_todas_las_claves():
    with pytest.raises(KeyError):
        compartido.rellenar("Hola {{nombre}}", {})


def test_codigos_de_respaldo():
    codigos = s.generar_codigos_respaldo()
    assert len(codigos) == 10 and len(set(codigos)) == 10
    assert all(len(c) == 9 and c[4] == "-" for c in codigos)
    assert s.normalizar_respaldo(codigos[0].lower().replace("-", " ")) == codigos[0].replace("-", "")
    assert s.normalizar_respaldo("123456") is None          # un código TOTP no es de respaldo
    assert s.normalizar_respaldo("ABCD-EFG0") is None       # 0 no está en el alfabeto
    assert len(s.generar_codigo_correo()) == 6


def test_tarea_diaria_no_duplica_y_reintenta(monkeypatch):
    enviados, marcas = [], set()
    monkeypatch.setattr(f, "hoy", lambda: date(2026, 10, 16))  # viernes
    monkeypatch.setattr(resumenes.repositorio, "destinatarios_resumen",
                        lambda: [{"usuario_id": 1, "usuario": "a@b.co", "nombre": "A"},
                                 {"usuario_id": 2, "usuario": "c@d.co", "nombre": "C"}])

    def marcar(u, t, p):
        if (u, t, p) in marcas:
            return False
        marcas.add((u, t, p))
        return True

    monkeypatch.setattr(resumenes.repositorio, "marcar_envio", marcar)
    monkeypatch.setattr(resumenes.repositorio, "desmarcar_envio", lambda u, t, p: marcas.discard((u, t, p)))

    def enviar(usuario_id, correo, nombre, periodo):
        if usuario_id == 2 and "fallo" not in enviados:  # falla solo la primera vez
            enviados.append("fallo")
            raise RuntimeError("Brevo caído")
        enviados.append((usuario_id, periodo.tipo))

    monkeypatch.setattr(resumenes, "enviar", enviar)
    primero = resumenes.tarea_diaria()
    assert primero["periodos"] == ["semanal 2026-10-16"]
    assert (primero["enviados"], primero["errores"]) == (1, 1)
    segundo = resumenes.tarea_diaria()                      # se repite: solo reintenta el fallido
    assert (segundo["enviados"], segundo["omitidos"], segundo["errores"]) == (1, 1, 0)
    assert resumenes.periodos_de_hoy(date(2026, 10, 15)) == []   # jueves, día 15: nada
