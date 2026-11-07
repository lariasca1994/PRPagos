"""
"Olvidé mi contraseña" (enlace de un solo uso por correo) y "Perdí mi autenticador"
(código por correo para configurar uno nuevo). Sin base de datos: el repositorio y
el correo se reemplazan por datos en memoria.
"""
import re
import time

import pytest

from app import correo as servicio_correo
from app import dependencias, dos_pasos, repositorio
from app.seguridad import crear_token, hash_enlace, hash_password, leer_token
from app.servicios import ErrorServicio, acceso

CLAVE = "Contrasena-Segura-123"


@pytest.fixture
def cuenta(monkeypatch):
    estado = {"existe": True, "bloqueada": False, "dos_pasos": dos_pasos.banderas(True, False, "app"),
              "recup_hash": None, "envios": 0, "enviados": [], "eventos": [], "claves": [], "fallos": 0}

    def credenciales(usuario=None, usuario_id=None):
        if not estado["existe"] or (usuario is not None and usuario != "ana@example.com"):
            return None
        return {"usuario_id": 7, "usuario": "ana@example.com", "nombre": "Ana", "password": hash_password(CLAVE),
                "totp_secreto": "cifrado", "totp_activo": bool(estado["dos_pasos"] & 1),
                "dos_pasos": estado["dos_pasos"], "totp_ultimo_paso": 0, "bloqueada": estado["bloqueada"]}

    def guardar_codigo(usuario_id, h, minutos):
        estado["recup_hash"], estado["minutos"] = h, minutos

    def usar_codigo(usuario_id, h):
        ok = h == estado["recup_hash"]
        if ok:
            estado["recup_hash"] = None
        return ok

    monkeypatch.setattr(repositorio, "obtener_credenciales", credenciales)
    monkeypatch.setattr(repositorio, "guardar_codigo_correo", guardar_codigo)
    monkeypatch.setattr(repositorio, "usar_codigo_correo", usar_codigo)
    monkeypatch.setattr(repositorio, "usar_enlace", usar_codigo)
    monkeypatch.setattr(repositorio, "cuenta_por_enlace", lambda h: 7 if h == estado["recup_hash"] else None)
    monkeypatch.setattr(repositorio, "envios_codigo_recientes", lambda *a: estado["envios"])
    monkeypatch.setattr(repositorio, "registrar_envio_codigo",
                        lambda *a: estado.__setitem__("envios", estado["envios"] + 1))
    monkeypatch.setattr(repositorio, "registrar_evento", lambda uid, accion, d: estado["eventos"].append(accion))
    monkeypatch.setattr(repositorio, "registrar_fallo_acceso",
                        lambda *a: estado.__setitem__("fallos", estado["fallos"] + 1))
    monkeypatch.setattr(repositorio, "cambiar_password", lambda uid, h, motivo: estado["claves"].append(motivo))
    monkeypatch.setattr(repositorio, "reiniciar_intentos", lambda *a: None)
    estado["respaldo"] = ["viejo"] * 10
    monkeypatch.setattr(repositorio, "reemplazar_codigos_respaldo",
                        lambda uid, hashes: estado.__setitem__("respaldo", list(hashes)))
    monkeypatch.setattr(repositorio, "respaldo_restantes", lambda uid: len(estado["respaldo"]))
    monkeypatch.setattr(repositorio, "guardar_totp", lambda *a: None)
    monkeypatch.setattr(repositorio, "guardar_dos_pasos", lambda *a: pytest.fail("no debe tocar los métodos"))
    monkeypatch.setattr(servicio_correo, "enviar",
                        lambda destino, nombre, asunto, html: estado["enviados"].append((asunto, html)))
    monkeypatch.setattr(acceso, "generar_codigo_correo", lambda: "246810")
    return estado


def _token_del_correo(html: str) -> str:
    return re.search(r"/restablecer\?token=([A-Za-z0-9_\-]+)", html).group(1)


# ───────────────────────── Olvidé mi contraseña ─────────────────────────


def test_la_respuesta_es_la_misma_exista_o_no_la_cuenta(cuenta):
    con_cuenta = acceso.solicitar_restablecer("ana@example.com")
    sin_cuenta = acceso.solicitar_restablecer("nadie@example.com")
    assert con_cuenta == sin_cuenta == acceso.MENSAJE_ENLACE_ENVIADO
    assert len(cuenta["enviados"]) == 1


def test_el_enlace_se_guarda_solo_como_huella_y_vence(cuenta):
    acceso.solicitar_restablecer("ana@example.com")
    asunto, html = cuenta["enviados"][0]
    token = _token_del_correo(html)
    assert len(token) >= 32
    assert cuenta["recup_hash"] == hash_enlace(token) and token not in cuenta["recup_hash"]
    assert 30 <= cuenta["minutos"] <= 60
    assert "RECUPERA_SOLICITA" in cuenta["eventos"]


def test_correo_con_formato_invalido(cuenta):
    with pytest.raises(ErrorServicio, match="correo válido"):
        acceso.solicitar_restablecer("no-es-correo")


def test_tope_de_envios_responde_igual_sin_enviar(cuenta):
    cuenta["envios"] = acceso.MAX_ENVIOS_CORREO
    assert acceso.solicitar_restablecer("ana@example.com") == acceso.MENSAJE_ENLACE_ENVIADO
    assert cuenta["enviados"] == []


def test_si_brevo_falla_no_se_delata_la_cuenta(cuenta, monkeypatch):
    def falla(*a):
        raise servicio_correo.ErrorCorreo("sin conexión")
    monkeypatch.setattr(servicio_correo, "enviar", falla)
    assert acceso.solicitar_restablecer("ana@example.com") == acceso.MENSAJE_ENLACE_ENVIADO


def test_restablecer_cambia_la_clave_y_el_enlace_sirve_una_sola_vez(cuenta):
    acceso.solicitar_restablecer("ana@example.com")
    token = _token_del_correo(cuenta["enviados"][0][1])
    assert acceso.enlace_vigente(token)

    # Un error en la contraseña nueva no gasta el enlace.
    with pytest.raises(ErrorServicio, match="no coinciden"):
        acceso.restablecer(token, "Nueva-Clave-123", "Otra-Clave-123")
    with pytest.raises(ErrorServicio, match="al menos 8"):
        acceso.restablecer(token, "corta", "corta")
    assert acceso.enlace_vigente(token)

    assert acceso.restablecer(token, "Nueva-Clave-123", "Nueva-Clave-123") == acceso.MENSAJE_CLAVE_RESTABLECIDA
    assert cuenta["claves"] == ["RECUPERAR_CLAVE"]
    assert not acceso.enlace_vigente(token)
    with pytest.raises(ErrorServicio, match="no es válido o ya venció"):
        acceso.restablecer(token, "Nueva-Clave-123", "Nueva-Clave-123")
    # La verificación en dos pasos sigue igual (guardar_dos_pasos falla la prueba si se llama).
    assert cuenta["dos_pasos"] == dos_pasos.banderas(True, False, "app")


def test_enlace_inventado_o_reemplazado_no_sirve(cuenta):
    acceso.solicitar_restablecer("ana@example.com")
    viejo = _token_del_correo(cuenta["enviados"][0][1])
    acceso.solicitar_restablecer("ana@example.com")
    with pytest.raises(ErrorServicio, match="no es válido"):
        acceso.restablecer(viejo, "Nueva-Clave-123", "Nueva-Clave-123")
    with pytest.raises(ErrorServicio, match="no es válido"):
        acceso.restablecer("x" * 43, "Nueva-Clave-123", "Nueva-Clave-123")
    # Un código de 6 dígitos nunca sirve como enlace.
    with pytest.raises(ErrorServicio, match="no es válido"):
        acceso.restablecer("246810", "Nueva-Clave-123", "Nueva-Clave-123")
    assert cuenta["claves"] == []


def test_cuenta_bloqueada_no_restablece(cuenta):
    acceso.solicitar_restablecer("ana@example.com")
    token = _token_del_correo(cuenta["enviados"][0][1])
    cuenta["bloqueada"] = True
    with pytest.raises(ErrorServicio, match="Demasiados intentos"):
        acceso.restablecer(token, "Nueva-Clave-123", "Nueva-Clave-123")
    assert cuenta["claves"] == []


def test_restablecer_cierra_las_sesiones_abiertas_antes():
    abierta = leer_token(crear_token(7, "ana@example.com", "Ana", inicio=int(time.time()) - 600))
    assert dependencias.sesion_anterior_a_restablecer(abierta, 60)
    nueva = leer_token(crear_token(7, "ana@example.com", "Ana"))
    assert not dependencias.sesion_anterior_a_restablecer(nueva, 60)
    assert not dependencias.sesion_anterior_a_restablecer(abierta, None)


def test_la_api_responde_401_con_el_motivo_si_la_clave_se_restablecio(monkeypatch):
    from fastapi.testclient import TestClient
    from app.main import app

    token = crear_token(7, "ana@example.com", "Ana", inicio=int(time.time()) - 600)
    monkeypatch.setattr(repositorio, "obtener_estado_usuario",
                        lambda *a: {"activo": True, "rol": "usuario", "clave_restablecida_hace": 30})
    respuesta = TestClient(app).get("/api/sesion", headers={"Authorization": f"Bearer {token}"})
    assert respuesta.status_code == 401
    assert respuesta.json()["motivo"] == "clave"


# ───────────────────────── Perdí mi autenticador ─────────────────────────


def test_perdi_mi_autenticador_envia_el_codigo_aunque_el_correo_no_este_activo(cuenta):
    assert not dos_pasos.de_cuenta(repositorio.obtener_credenciales(usuario_id=7))["correo"]
    acceso.perdi_autenticador(7)
    asunto, html = cuenta["enviados"][0]
    assert "246810" in html and "autenticador nuevo" in html
    paso = acceso.verificar_codigo_correo(7, "246 810")
    assert paso.etapa == "configurar" and paso.secreto


def test_perdi_mi_autenticador_respeta_el_tope_de_envios(cuenta):
    cuenta["envios"] = acceso.MAX_ENVIOS_CORREO
    with pytest.raises(ErrorServicio, match="Ya enviamos 3 códigos"):
        acceso.perdi_autenticador(7)


def test_codigo_errado_cuenta_como_intento_fallido(cuenta):
    acceso.perdi_autenticador(7)
    with pytest.raises(ErrorServicio, match="incorrecto o vencido"):
        acceso.verificar_codigo_correo(7, "111111")
    assert cuenta["fallos"] == 1


# ───────────────────────── Códigos de respaldo ─────────────────────────


def test_olvide_mi_contrasena_anula_los_codigos_y_el_siguiente_ingreso_da_nuevos(cuenta):
    acceso.solicitar_restablecer("ana@example.com")
    token = _token_del_correo(cuenta["enviados"][0][1])
    mensaje = acceso.restablecer(token, "Nueva-Clave-123", "Nueva-Clave-123")
    assert cuenta["respaldo"] == []  # anulados en el mismo momento
    assert "códigos de respaldo" in mensaje
    # Nunca van en el correo: el único correo enviado es el del enlace.
    assert len(cuenta["enviados"]) == 1
    nuevos = acceso.codigos_pendientes(7)
    assert len(nuevos) == 10 and len(cuenta["respaldo"]) == 10
    # Ya con códigos vigentes, el siguiente ingreso no genera otros.
    assert acceso.codigos_pendientes(7) == []


def test_cambiar_la_contrasena_en_el_perfil_da_codigos_nuevos_y_anula_los_anteriores(cuenta, monkeypatch):
    from app.servicios import perfil
    monkeypatch.setattr(perfil, "verificar_segundo_factor", lambda *a: True)
    mensaje, codigos = perfil.cambiar_contrasena(7, CLAVE, "Nueva-Clave-123", "Nueva-Clave-123", "123456")
    assert len(codigos) == 10 and "viejo" not in cuenta["respaldo"] and len(cuenta["respaldo"]) == 10
    assert "ya no sirven" in mensaje
    assert cuenta["claves"] == ["CAMBIO_CLAVE"]


def test_perdi_mi_autenticador_al_activar_la_app_nueva_anula_los_codigos_anteriores(cuenta):
    from app.seguridad import codigo_totp, paso_actual
    acceso.perdi_autenticador(7)
    paso = acceso.verificar_codigo_correo(7, "246810")
    cred, codigos = acceso.activar_autenticador(7, paso.secreto, codigo_totp(paso.secreto, paso_actual()))
    assert len(codigos) == 10 and "viejo" not in cuenta["respaldo"]