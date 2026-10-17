import base64
import hashlib

import pytest

from app import seguridad as s

# Vector oficial de RFC 6238 (apéndice B, SHA-1): clave ASCII
# "12345678901234567890" y T = 59 s -> 94287082; con 6 dígitos, 287082.
SECRETO_RFC = base64.b32encode(b"12345678901234567890").decode().rstrip("=")


def test_totp_vector_rfc6238():
    assert s.codigo_totp(SECRETO_RFC, 59 // 30) == "287082"
    assert s.codigo_totp(SECRETO_RFC, 1111111109 // 30) == "081804"


def test_totp_ventana_y_reutilizacion():
    ahora = 1_700_000_000.0
    paso = s.paso_actual(ahora)
    codigo = s.codigo_totp(SECRETO_RFC, paso)
    assert s.verificar_totp(SECRETO_RFC, codigo, ahora=ahora) == paso
    # Un paso de tolerancia hacia atrás (reloj del teléfono atrasado).
    anterior = s.codigo_totp(SECRETO_RFC, paso - 1)
    assert s.verificar_totp(SECRETO_RFC, anterior, ahora=ahora) == paso - 1
    # Ya usado: no vale de nuevo.
    assert s.verificar_totp(SECRETO_RFC, codigo, ultimo_paso=paso, ahora=ahora) is None
    # Fuera de la ventana.
    viejo = s.codigo_totp(SECRETO_RFC, paso - 3)
    assert s.verificar_totp(SECRETO_RFC, viejo, ahora=ahora) is None


@pytest.mark.parametrize("entrada", ["", "12345", "abcdef", "1234567", None])
def test_totp_rechaza_formatos_invalidos(entrada):
    assert s.verificar_totp(SECRETO_RFC, entrada, ahora=1_700_000_000.0) is None


def test_totp_acepta_codigo_con_espacio():
    ahora = 1_700_000_000.0
    codigo = s.codigo_totp(SECRETO_RFC, s.paso_actual(ahora))
    assert s.verificar_totp(SECRETO_RFC, f"{codigo[:3]} {codigo[3:]}", ahora=ahora) is not None


def test_secreto_generado_es_base32_de_160_bits():
    secreto = s.generar_secreto_totp()
    assert len(secreto) == 32
    assert len(base64.b32decode(secreto)) == 20


def test_uri_totp():
    uri = s.uri_totp("ABCD", "ana@correo.co")
    assert uri.startswith("otpauth://totp/PRPagos%3Aana%40correo.co?secret=ABCD")
    assert "issuer=PRPagos" in uri and "digits=6" in uri and "period=30" in uri


def test_pbkdf2_formato_y_verificacion():
    guardado = s.hash_password("Clave-Segura-1", iteraciones=1000)
    algoritmo, iteraciones, sal, hash_ = guardado.split("$")
    assert algoritmo == "pbkdf2_sha256" and iteraciones == "1000"
    assert len(base64.b64decode(sal)) == 16 and len(base64.b64decode(hash_)) == 32
    assert s.verificar_password("Clave-Segura-1", guardado)
    assert not s.verificar_password("clave-segura-1", guardado)


def test_pbkdf2_usa_sal_distinta():
    assert s.hash_password("igual", 1000) != s.hash_password("igual", 1000)


def test_sha256_heredado_se_acepta_y_pide_rehash():
    heredado = hashlib.sha256("vieja".encode()).hexdigest()
    assert s.verificar_password("vieja", heredado)
    assert not s.verificar_password("otra", heredado)
    assert s.necesita_rehash(heredado)
    assert s.necesita_rehash(s.hash_password("x", iteraciones=1000))
    assert not s.necesita_rehash(s.hash_password("x"))


@pytest.mark.parametrize("guardado", [None, "", "texto-plano", "pbkdf2_sha256$roto"])
def test_verificar_password_valores_invalidos(guardado):
    assert not s.verificar_password("x", guardado)


def test_cifrado_ida_y_vuelta_y_alteracion():
    cifrado = s.cifrar_secreto(SECRETO_RFC)
    assert cifrado.startswith("v1:")
    assert s.descifrar_secreto(cifrado) == SECRETO_RFC
    assert s.cifrar_secreto(SECRETO_RFC) != cifrado  # nonce distinto
    crudo = bytearray(base64.b64decode(cifrado[3:]))
    crudo[-1] ^= 1
    assert s.descifrar_o_none("v1:" + base64.b64encode(bytes(crudo)).decode()) is None
    assert s.descifrar_o_none(None) is None


@pytest.mark.parametrize(
    "correo, valido",
    [
        ("ana@gmail.com", True),
        ("ana.f+pruebas@ejemplo.org", True),
        ("x@sub.dominio.co", True),
        ("sin-arroba.com", False),
        ("a@b", False),
        ("a@b.c", False),
        ("con espacio@d.com", False),
        ("@dominio.com", False),
        ("a@@b.com", False),
        ("a" * 95 + "@d.com", False),
    ],
)
def test_correo_valido(correo, valido):
    assert s.correo_valido(correo) is valido


def test_contrasena_minima():
    assert s.problema_contrasena("corta") is not None
    assert s.problema_contrasena("suficiente") is None


def test_tokens_no_se_confunden_entre_tipos():
    sesion = s.crear_token(1, "a@b.co", "Ana")
    parcial = s.crear_token_parcial(1, "a@b.co", "Ana", "codigo")
    assert s.leer_token(sesion)["usuario_id"] == 1
    assert s.leer_token(parcial) is None
    assert s.leer_token_parcial(sesion) is None
    assert s.leer_token_parcial(parcial)["etapa"] == "codigo"


def test_token_de_configuracion_lleva_secreto_cifrado():
    token = s.crear_token_parcial(1, "a@b.co", "Ana", "configurar", secreto_pendiente="ABCD")
    datos = s.leer_token_parcial(token)
    assert datos["pendiente"] != "ABCD"
    assert s.descifrar_o_none(datos["pendiente"]) == "ABCD"


def test_token_de_reconfiguracion_es_del_mismo_usuario():
    token = s.crear_token_reconfiguracion(7, "ABCD")
    assert s.leer_token_reconfiguracion(token, 7) == "ABCD"
    assert s.leer_token_reconfiguracion(token, 8) is None
