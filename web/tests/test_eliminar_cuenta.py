"""
"Eliminar mi cuenta": confirmación fuerte (ELIMINAR + contraseña + segundo factor) y
borrado definitivo de la cuenta y de todos sus datos. El borrado se prueba con las mismas
sentencias del procedimiento eliminar_cuenta sobre una base SQLite en memoria.
"""
import re
import sqlite3
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import correo as servicio_correo
from app import repositorio
from app.main import app
from app.seguridad import crear_token, hash_password
from app.servicios import ErrorServicio, perfil

CLAVE = "Contrasena-Segura-123"


# ───────────────────────── Confirmación fuerte ─────────────────────────


@pytest.fixture
def cuenta(monkeypatch):
    estado = {"rol": "usuario", "bloqueada": False, "eliminadas": [], "fallos": 0, "correos": []}

    def credenciales(usuario=None, usuario_id=None):
        return {"usuario_id": 7, "usuario": "ana@example.com", "nombre": "Ana",
                "password": hash_password(CLAVE), "totp_secreto": None, "totp_activo": True,
                "dos_pasos": 1, "totp_ultimo_paso": 0, "bloqueada": estado["bloqueada"]}

    monkeypatch.setattr(repositorio, "obtener_credenciales", credenciales)
    monkeypatch.setattr(repositorio, "obtener_estado_usuario",
                        lambda uid: {"rol": estado["rol"], "activo": True, "clave_restablecida_hace": None})
    monkeypatch.setattr(repositorio, "registrar_fallo_acceso",
                        lambda *a: estado.__setitem__("fallos", estado["fallos"] + 1))
    monkeypatch.setattr(repositorio, "eliminar_cuenta",
                        lambda uid: estado["eliminadas"].append(uid) or True)
    monkeypatch.setattr(perfil, "verificar_segundo_factor", lambda cred, codigo, motivo: codigo == "123456")
    monkeypatch.setattr(servicio_correo, "enviar",
                        lambda destino, nombre, asunto, html: estado["correos"].append((asunto, html)))
    return estado


def test_elimina_con_la_confirmacion_completa_y_avisa_por_correo(cuenta):
    mensaje = perfil.eliminar_cuenta(7, CLAVE, "123456", "ELIMINAR")
    assert "definitiva" in mensaje
    assert cuenta["eliminadas"] == [7]
    asunto, html = cuenta["correos"][0]
    assert "eliminada" in asunto and "Simulador de pagos" in asunto
    assert "Tu cuenta fue eliminada" in html and "Simulador de pagos" in html


@pytest.mark.parametrize("confirmacion", ["", "eliminar", "BORRAR", "ELIMINA"])
def test_sin_escribir_eliminar_no_borra(cuenta, confirmacion):
    with pytest.raises(ErrorServicio, match="escribe ELIMINAR"):
        perfil.eliminar_cuenta(7, CLAVE, "123456", confirmacion)
    assert cuenta["eliminadas"] == []


def test_contrasena_incorrecta_no_borra_y_cuenta_como_fallo(cuenta):
    with pytest.raises(ErrorServicio, match="contraseña actual no es correcta"):
        perfil.eliminar_cuenta(7, "otra-clave-123", "123456", "ELIMINAR")
    assert cuenta["eliminadas"] == [] and cuenta["fallos"] == 1


def test_codigo_incorrecto_no_borra(cuenta):
    with pytest.raises(ErrorServicio, match="Código incorrecto"):
        perfil.eliminar_cuenta(7, CLAVE, "000000", "ELIMINAR")
    assert cuenta["eliminadas"] == []


def test_cuenta_bloqueada_o_admin_no_se_borra(cuenta):
    cuenta["rol"] = "admin"
    with pytest.raises(ErrorServicio, match="administración"):
        perfil.eliminar_cuenta(7, CLAVE, "123456", "ELIMINAR")
    cuenta["rol"], cuenta["bloqueada"] = "usuario", True
    with pytest.raises(ErrorServicio, match="bloqueada"):
        perfil.eliminar_cuenta(7, CLAVE, "123456", "ELIMINAR")
    assert cuenta["eliminadas"] == []


def test_si_falla_el_correo_la_cuenta_igual_queda_eliminada(cuenta, monkeypatch):
    def falla(*a):
        raise servicio_correo.ErrorCorreo("sin conexión")
    monkeypatch.setattr(servicio_correo, "enviar", falla)
    assert "definitiva" in perfil.eliminar_cuenta(7, CLAVE, "123456", "ELIMINAR")
    assert cuenta["eliminadas"] == [7]


def test_la_web_cierra_la_sesion_y_vuelve_al_ingreso(cuenta):
    cliente = TestClient(app)
    cliente.cookies.set("sesion", crear_token(7, "ana@example.com", "Ana"))
    respuesta = cliente.post("/perfil/eliminar", data={"contrasena": CLAVE, "codigo": "123456",
                                                        "confirmacion": "ELIMINAR"}, follow_redirects=False)
    assert respuesta.status_code == 303
    assert respuesta.headers["location"].startswith("/login?ok=")
    assert 'sesion=""' in respuesta.headers["set-cookie"] or "Max-Age=0" in respuesta.headers["set-cookie"]


def test_la_api_del_escritorio_usa_las_mismas_reglas(cuenta):
    cliente = TestClient(app)
    cabecera = {"Authorization": "Bearer " + crear_token(7, "ana@example.com", "Ana")}
    malo = cliente.post("/api/perfil/eliminar", json={"contrasena": CLAVE, "codigo": "123456",
                                                      "confirmacion": "no"}, headers=cabecera)
    assert malo.status_code == 400 and "ELIMINAR" in malo.json()["error"]
    bueno = cliente.post("/api/perfil/eliminar", json={"contrasena": CLAVE, "codigo": "123456",
                                                       "confirmacion": "ELIMINAR"}, headers=cabecera)
    assert bueno.status_code == 200 and "definitiva" in bueno.json()["mensaje"]


# ───────────────────────── Borrado definitivo ─────────────────────────

ESQUEMA = """
CREATE TABLE TBPLogin (id INTEGER PRIMARY KEY, usuario TEXT, nombre TEXT, recup_hash TEXT);
CREATE TABLE TBCategorias (id INTEGER PRIMARY KEY, usuario_id INTEGER, nombre TEXT);
CREATE TABLE TBPagos (id_pago INTEGER PRIMARY KEY, usuario_id INTEGER, categoria_id INTEGER, concepto TEXT);
CREATE TABLE TBAuditoria (id INTEGER PRIMARY KEY, usuario_id INTEGER, id_pago INTEGER, accion TEXT, detalle TEXT);
CREATE TABLE TBPresupuestoMensual (usuario_id INTEGER, categoria_id INTEGER, monto REAL);
CREATE TABLE TBPresupuestos (usuario_id INTEGER PRIMARY KEY, pct_necesidades REAL);
CREATE TABLE TBEnviosResumen (usuario_id INTEGER, tipo TEXT, periodo TEXT);
CREATE TABLE TBCodigosRespaldo (id INTEGER PRIMARY KEY, usuario_id INTEGER, codigo_hash TEXT);
CREATE TABLE TBLlavesAcceso (id INTEGER PRIMARY KEY, usuario_id INTEGER, credencial_id TEXT);
"""


@pytest.fixture
def base():
    conn = sqlite3.connect(":memory:")
    conn.executescript(ESQUEMA)
    for uid, correo in ((1, "borrar@ejemplo.com"), (2, "otra@ejemplo.com")):
        conn.execute("INSERT INTO TBPLogin VALUES (?, ?, 'Nombre', 'huella')", (uid, correo))
        conn.execute("INSERT INTO TBCategorias VALUES (?, ?, 'Propia')", (uid * 10, uid))
        conn.execute("INSERT INTO TBPagos VALUES (?, ?, ?, 'Concepto personal')", (uid * 100, uid, uid * 10))
        conn.execute("INSERT INTO TBAuditoria VALUES (?, ?, ?, 'INSERTAR', 'Concepto personal')",
                     (uid * 1000, uid, uid * 100))
        conn.execute("INSERT INTO TBAuditoria VALUES (?, ?, NULL, 'INGRESO', 'Acceso')", (uid * 1000 + 1, uid))
        conn.execute("INSERT INTO TBPresupuestoMensual VALUES (?, ?, 100)", (uid, uid * 10))
        conn.execute("INSERT INTO TBPresupuestos VALUES (?, 50)", (uid,))
        conn.execute("INSERT INTO TBEnviosResumen VALUES (?, 'semanal', '2026-W45')", (uid,))
        conn.execute("INSERT INTO TBCodigosRespaldo VALUES (?, ?, 'h')", (uid * 10000, uid))
        conn.execute("INSERT INTO TBLlavesAcceso VALUES (?, ?, 'c')", (uid * 20000, uid))
    # Un admin (cuenta 2) modificó un registro de la cuenta 1: esa auditoría también la identifica.
    conn.execute("INSERT INTO TBAuditoria VALUES (5000, 2, 100, 'ACTUALIZAR (admin)', 'Concepto personal')")
    conn.commit()
    yield conn
    conn.close()


def _filas_de(conn, uid: int) -> dict[str, int]:
    consultas = {
        "TBPLogin": "SELECT COUNT(*) FROM TBPLogin WHERE id = ?",
        "TBCategorias": "SELECT COUNT(*) FROM TBCategorias WHERE usuario_id = ?",
        "TBPagos": "SELECT COUNT(*) FROM TBPagos WHERE usuario_id = ?",
        "TBAuditoria": "SELECT COUNT(*) FROM TBAuditoria WHERE usuario_id = ? OR id_pago IN "
                       "(SELECT ? * 100)",
        "TBPresupuestoMensual": "SELECT COUNT(*) FROM TBPresupuestoMensual WHERE usuario_id = ?",
        "TBPresupuestos": "SELECT COUNT(*) FROM TBPresupuestos WHERE usuario_id = ?",
        "TBEnviosResumen": "SELECT COUNT(*) FROM TBEnviosResumen WHERE usuario_id = ?",
        "TBCodigosRespaldo": "SELECT COUNT(*) FROM TBCodigosRespaldo WHERE usuario_id = ?",
        "TBLlavesAcceso": "SELECT COUNT(*) FROM TBLlavesAcceso WHERE usuario_id = ?",
    }
    return {tabla: conn.execute(sql, (uid,) * sql.count("?")).fetchone()[0] for tabla, sql in consultas.items()}


def test_no_queda_nada_de_la_cuenta_y_las_demas_siguen_intactas(base):
    antes = _filas_de(base, 2)
    assert repositorio.borrar_cuenta_con_sentencias(base, 1) is True
    assert set(_filas_de(base, 1).values()) == {0}
    # Nada en ninguna tabla menciona a la persona borrada.
    for (tabla,) in base.execute("SELECT name FROM sqlite_master WHERE type = 'table'").fetchall():
        for fila in base.execute(f"SELECT * FROM {tabla}").fetchall():
            assert "borrar@ejemplo.com" not in [str(v) for v in fila]
    despues = _filas_de(base, 2)
    despues["TBAuditoria"] += 1  # la auditoría del admin sobre el registro borrado también se fue
    assert despues == antes


def test_borrar_una_cuenta_inexistente_no_toca_nada(base):
    antes = (_filas_de(base, 1), _filas_de(base, 2))
    assert repositorio.borrar_cuenta_con_sentencias(base, 99) is False
    assert (_filas_de(base, 1), _filas_de(base, 2)) == antes


def _normalizar(sql: str) -> str:
    return re.sub(r"\s+", " ", sql).strip().upper()


def test_el_procedimiento_y_las_sentencias_de_respaldo_son_las_mismas():
    texto = (Path(__file__).resolve().parents[2] / "sql" / "DBPagos.sql").read_text(encoding="utf-8")
    cuerpo = texto.split("CREATE OR REPLACE PROCEDURE eliminar_cuenta", 1)[1]
    cuerpo = cuerpo.split("BEGIN", 1)[1].split("p_eliminada :=", 1)[0]
    procedimiento = [_normalizar(s.replace("p_usuario_id", ":id")) for s in cuerpo.split(";") if s.strip()]
    assert procedimiento == [_normalizar(s) for s in repositorio.SENTENCIAS_ELIMINAR_CUENTA]
