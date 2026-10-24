"""
Verificación en dos pasos a elección: app autenticadora o código por correo.
Sin base de datos: el repositorio y el correo se reemplazan por datos en memoria.
"""
import pytest

from app import correo as servicio_correo
from app import dos_pasos, repositorio
from app.seguridad import hash_corto, hash_password
from app.servicios import ErrorServicio, acceso, perfil

CLAVE = "Contrasena-Segura-123"


@pytest.fixture
def cuenta(monkeypatch):
    estado = {"dos_pasos": 0, "secreto": None, "codigo_hash": None, "envios": 0, "enviados": [], "fallos": 0}
    hash_clave = hash_password(CLAVE)

    def credenciales(usuario=None, usuario_id=None):
        return {"usuario_id": 7, "usuario": "ana@example.com", "nombre": "Ana", "password": hash_clave,
                "totp_secreto": estado["secreto"], "totp_activo": bool(estado["dos_pasos"] & 1),
                "dos_pasos": estado["dos_pasos"], "totp_ultimo_paso": 0, "bloqueada": False}

    def guardar_codigo(usuario_id, codigo_hash, minutos):
        estado["codigo_hash"] = codigo_hash

    def usar_codigo(usuario_id, codigo_hash):
        ok = codigo_hash == estado["codigo_hash"]
        if ok:
            estado["codigo_hash"] = None
        return ok

    def enviar(destino, nombre, asunto, html):
        estado["enviados"].append(html)

    monkeypatch.setattr(repositorio, "obtener_credenciales", credenciales)
    monkeypatch.setattr(repositorio, "guardar_codigo_correo", guardar_codigo)
    monkeypatch.setattr(repositorio, "usar_codigo_correo", usar_codigo)
    monkeypatch.setattr(repositorio, "envios_codigo_recientes", lambda *a: estado["envios"])
    monkeypatch.setattr(repositorio, "registrar_envio_codigo",
                        lambda *a: estado.__setitem__("envios", estado["envios"] + 1))
    monkeypatch.setattr(repositorio, "guardar_dos_pasos", lambda uid, valor, d: estado.__setitem__("dos_pasos", valor))
    monkeypatch.setattr(repositorio, "reiniciar_intentos", lambda *a: None)
    monkeypatch.setattr(repositorio, "registrar_fallo_acceso",
                        lambda *a: estado.__setitem__("fallos", estado["fallos"] + 1))
    monkeypatch.setattr(repositorio, "reemplazar_codigos_respaldo", lambda *a: None)
    monkeypatch.setattr(repositorio, "cambiar_password", lambda *a: None)
    monkeypatch.setattr(servicio_correo, "enviar", enviar)
    # El código real va en el correo: aquí se fija para poder escribirlo.
    monkeypatch.setattr(acceso, "generar_codigo_correo", lambda: "246810")
    return estado


def test_banderas_conservan_el_valor_de_las_cuentas_que_solo_usan_la_app():
    assert dos_pasos.banderas(True, False, "app") == 1
    assert dos_pasos.metodos(1) == {"app": True, "correo": False, "preferido": "app", "configurado": True}
    assert dos_pasos.metodos(2)["preferido"] == "correo"
    assert dos_pasos.metodos(dos_pasos.banderas(True, True, "correo"))["preferido"] == "correo"
    assert dos_pasos.metodos(3)["preferido"] == "app"
    assert dos_pasos.metodos(0)["configurado"] is False
    # Sin secreto guardado, la bandera de la app no cuenta.
    assert dos_pasos.metodos(1, hay_secreto=False)["configurado"] is False


def test_primer_ingreso_elige_el_correo_y_lo_activa_con_su_primer_codigo(cuenta):
    paso = acceso.ingresar("ana@example.com", CLAVE)
    assert paso.etapa == "elegir"

    paso = acceso.elegir_metodo(7, "correo")
    assert paso.etapa == "activar_correo"
    assert len(cuenta["enviados"]) == 1 and "246810" in cuenta["enviados"][0]

    with pytest.raises(ErrorServicio, match="Código incorrecto"):
        acceso.activar_correo(7, "111111")
    cred, codigos = acceso.activar_correo(7, "246810")
    assert len(codigos) == 10
    assert dos_pasos.metodos(cuenta["dos_pasos"])["preferido"] == "correo"


def test_con_el_correo_activo_el_ingreso_envia_el_codigo_y_no_se_reutiliza(cuenta):
    cuenta["dos_pasos"] = dos_pasos.banderas(False, True, "correo")
    paso = acceso.ingresar("ana@example.com", CLAVE)
    assert paso.etapa == "codigo_correo"
    assert "ana@example.com" in paso.mensaje
    assert acceso.verificar_codigo(7, "246 810", "correo")["usuario_id"] == 7
    with pytest.raises(ErrorServicio):
        acceso.verificar_codigo(7, "246810", "correo")


def test_el_correo_no_vale_si_la_cuenta_no_lo_activo(cuenta):
    cuenta["dos_pasos"] = 0
    cuenta["codigo_hash"] = hash_corto("246810")
    with pytest.raises(ErrorServicio):
        acceso.verificar_codigo(7, "246810", "correo")


def test_tope_de_tres_envios_cada_diez_minutos(cuenta):
    cuenta["dos_pasos"] = dos_pasos.banderas(False, True, "correo")
    cuenta["envios"] = 3
    with pytest.raises(ErrorServicio, match="Ya enviamos 3 códigos"):
        acceso.ingresar("ana@example.com", CLAVE)


def test_desde_el_perfil_se_agrega_el_correo_y_se_elige_el_preferido(cuenta):
    cuenta["dos_pasos"] = dos_pasos.banderas(True, False, "app")
    cuenta["secreto"] = "cifrado"
    assert "ana@example.com" in perfil.iniciar_agregar_correo(7)
    assert perfil.confirmar_correo(7, "246810") == "El código por correo quedó activo"
    metodos = dos_pasos.metodos(cuenta["dos_pasos"])
    assert metodos["app"] and metodos["correo"] and metodos["preferido"] == "app"

    perfil.elegir_preferido(7, "correo")
    assert dos_pasos.metodos(cuenta["dos_pasos"])["preferido"] == "correo"
    with pytest.raises(ErrorServicio):
        perfil.elegir_preferido(7, "sms")


def test_cambiar_de_metodo_en_el_ingreso_solo_si_esta_activo(cuenta):
    cuenta["dos_pasos"] = dos_pasos.banderas(True, False, "app")
    cuenta["secreto"] = "cifrado"
    with pytest.raises(ErrorServicio, match="no está activo"):
        acceso.cambiar_metodo(7, "correo")
    cuenta["dos_pasos"] = dos_pasos.banderas(True, True, "app")
    assert acceso.cambiar_metodo(7, "correo").etapa == "codigo_correo"
