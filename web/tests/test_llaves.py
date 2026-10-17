"""Llaves de acceso (app/llaves.py): identidad del sitio, reto firmado y opciones."""
import json

import pytest

from app import llaves


@pytest.fixture(autouse=True)
def configuracion(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(llaves.settings, "url_publica", "https://prpagos.ejemplo.app/")
    monkeypatch.setattr(llaves.settings, "jwt_secret", "secreto-de-prueba-suficientemente-largo")


def test_sitio_y_origen_salen_de_la_url_publica() -> None:
    assert llaves.origen() == "https://prpagos.ejemplo.app"
    assert llaves.id_sitio() == "prpagos.ejemplo.app"


def test_id_de_usuario_sin_datos_personales() -> None:
    assert llaves.id_usuario(23) == b"23"


def test_reto_firmado_ida_y_vuelta() -> None:
    reto, token = llaves.crear_reto("registro", 23, "Portátil")
    datos = llaves.leer_reto(token, "registro")
    assert datos is not None and datos["reto"] == reto and datos["usuario_id"] == 23
    assert datos["nombre"] == "Portátil"


def test_reto_de_otro_fin_o_alterado_no_sirve() -> None:
    _, token = llaves.crear_reto("ingreso")
    assert llaves.leer_reto(token, "registro") is None
    assert llaves.leer_reto(token[:-2] + "xx", "ingreso") is None
    assert llaves.leer_reto(None, "ingreso") is None


def test_nombre_del_dispositivo() -> None:
    assert llaves.nombre_valido("  Mi   portátil ") == "Mi portátil"
    assert llaves.nombre_valido("   ") is None
    assert len(llaves.nombre_valido("x" * 200) or "") == llaves.LONGITUD_NOMBRE


def test_opciones_de_registro_exigen_verificacion_y_llave_residente() -> None:
    opciones = json.loads(llaves.opciones_registro(23, "ana@correo.com", "Ana", [], b"r" * 32))
    assert opciones["rp"]["id"] == "prpagos.ejemplo.app"
    assert opciones["authenticatorSelection"]["userVerification"] == "required"
    assert opciones["authenticatorSelection"]["residentKey"] == "required"
    assert sorted(p["alg"] for p in opciones["pubKeyCredParams"]) == [-257, -7]


def test_opciones_de_ingreso_sin_lista_de_llaves() -> None:
    opciones = json.loads(llaves.opciones_ingreso(b"r" * 32))
    assert opciones["rpId"] == "prpagos.ejemplo.app"
    assert opciones["userVerification"] == "required"
    assert opciones.get("allowCredentials", []) == []


def test_registro_invalido_da_mensaje_claro() -> None:
    with pytest.raises(llaves.ErrorLlave, match="No se pudo verificar"):
        llaves.verificar_registro({"id": "x", "rawId": "x", "type": "public-key", "response": {}}, b"r" * 32)
