"""
Segundo factor común al ingreso, la recuperación y el perfil: acepta el
código de 6 dígitos de la app autenticadora, el del correo (si la cuenta usa
ese método y pidió uno) o uno de los códigos de respaldo (ABCD-EFGH). Cada
fallo cuenta para el bloqueo de 5 minutos.
"""
from .. import dos_pasos, repositorio
from ..seguridad import descifrar_o_none, hash_corto, normalizar_respaldo, verificar_totp


def verificar_segundo_factor(cred: dict, codigo: str, motivo_exito: str) -> bool:
    # 8 caracteres = código de respaldo; 6 dígitos = código de la app o del correo.
    respaldo = normalizar_respaldo(codigo)
    if respaldo:
        ok = repositorio.usar_codigo_respaldo(cred["usuario_id"], hash_corto(respaldo))
        if ok:
            repositorio.reiniciar_intentos(cred["usuario_id"])
    else:
        secreto = descifrar_o_none(cred["totp_secreto"]) if cred["totp_activo"] else None
        paso = verificar_totp(secreto, codigo, cred["totp_ultimo_paso"]) if secreto else None
        ok = paso is not None and repositorio.registrar_acceso_exitoso(cred["usuario_id"], paso, motivo_exito)
        limpio = "".join(c for c in (codigo or "") if c.isdigit())
        if not ok and dos_pasos.de_cuenta(cred)["correo"] and len(limpio) == 6:
            ok = repositorio.usar_codigo_correo(cred["usuario_id"], hash_corto(limpio))
            if ok:
                repositorio.reiniciar_intentos(cred["usuario_id"])
    if not ok:
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_TOTP")
    return ok
