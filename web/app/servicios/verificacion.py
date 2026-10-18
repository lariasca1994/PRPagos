"""
Segundo factor común al ingreso, la recuperación y el perfil: acepta el
código de 6 dígitos de la app autenticadora o uno de los códigos de
respaldo (ABCD-EFGH). Cada fallo cuenta para el bloqueo de 5 minutos.
Igual que escritorio/.../seguridad/Verificacion.java.
"""
from .. import repositorio
from ..seguridad import descifrar_o_none, hash_corto, normalizar_respaldo, verificar_totp


def verificar_segundo_factor(cred: dict, codigo: str, motivo_exito: str) -> bool:
    # 8 caracteres = código de respaldo; 6 dígitos = código de la app.
    respaldo = normalizar_respaldo(codigo)
    if respaldo:
        ok = repositorio.usar_codigo_respaldo(cred["usuario_id"], hash_corto(respaldo))
        if ok:
            repositorio.reiniciar_intentos(cred["usuario_id"])
    else:
        secreto = descifrar_o_none(cred["totp_secreto"])
        paso = verificar_totp(secreto, codigo, cred["totp_ultimo_paso"]) if secreto else None
        ok = paso is not None and repositorio.registrar_acceso_exitoso(cred["usuario_id"], paso, motivo_exito)
    if not ok:
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_TOTP")
    return ok
