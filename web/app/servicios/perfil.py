"""
Perfil: tus datos y los resúmenes por correo, el plan 50/30/20, tus
categorías, la contraseña, el autenticador, los códigos de respaldo y las
llaves de acceso. Las acciones de seguridad piden un código vigente (de la
app o de respaldo), así que una sesión robada no alcanza para tomar la cuenta.
"""
import oracledb

from .. import dos_pasos, finanzas, llaves, repositorio
from ..seguridad import (
    cifrar_secreto,
    hash_corto,
    generar_secreto_totp,
    hash_password,
    problema_contrasena,
    verificar_password,
    verificar_totp,
)
from .verificacion import verificar_segundo_factor
from . import ErrorServicio
from .acceso import enviar_codigo, nuevos_codigos_respaldo, texto_codigo_enviado
from .registros import GRUPOS

MENSAJE_BLOQUEO = "Cuenta bloqueada temporalmente; espera 5 minutos"


def datos(usuario_id: int) -> dict:
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    pcts = repositorio.plan(usuario_id)
    return {
        "perfil": repositorio.perfil(usuario_id),
        "totp_activo": bool(cred and cred["totp_activo"]),
        "dos_pasos": dos_pasos.de_cuenta(cred) if cred else dos_pasos.metodos(0),
        "pcts": pcts,
        "textos_pct": {g: finanzas.texto_pct(v) for g, v in pcts.items()},
        "categorias": repositorio.categorias(usuario_id),
        "grupos": GRUPOS,
        "llaves": repositorio.llaves(usuario_id),
    }


def guardar_datos(usuario_id: int, nombre: str, notificaciones: bool) -> str:
    nombre = (nombre or "").strip()
    if not nombre:
        raise ErrorServicio("Escribe tu nombre completo")
    repositorio.guardar_datos_perfil(usuario_id, nombre[:100], notificaciones)
    return "Datos guardados"


def guardar_plan(usuario_id: int, necesidades: str, deseos: str, ahorro: str) -> str:
    try:
        valores = [round(float(str(v).strip().replace(",", ".")), 2) for v in (necesidades, deseos, ahorro)]
    except ValueError:
        raise ErrorServicio("Los porcentajes deben ser números") from None
    if any(v < 0 or v > 100 for v in valores) or round(sum(valores), 2) != 100:
        raise ErrorServicio("Los tres porcentajes deben sumar 100")
    repositorio.guardar_plan(usuario_id, *valores)
    return "Plan actualizado"


def crear_categoria(usuario_id: int, nombre: str, tipo: str, grupo: str) -> str:
    nombre = (nombre or "").strip()[:60]
    if not nombre:
        raise ErrorServicio("Escribe el nombre de la categoría")
    if tipo not in ("INGRESO", "GASTO"):
        raise ErrorServicio("Elige si la categoría es de ingresos o de gastos")
    if tipo == "GASTO" and grupo not in GRUPOS:
        raise ErrorServicio("Elige el grupo del plan para la categoría de gastos")
    if not repositorio.crear_categoria(usuario_id, nombre, tipo, grupo if tipo == "GASTO" else None):
        raise ErrorServicio("Ya tienes una categoría con ese nombre")
    return "Categoría creada"


def eliminar_categoria(usuario_id: int, categoria_id: int) -> str:
    motivo = repositorio.eliminar_categoria(usuario_id, categoria_id)
    if motivo:
        raise ErrorServicio(motivo)
    return "Categoría eliminada"


def _cuenta_con_codigo(usuario_id: int, codigo: str) -> dict:
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None or cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    if not verificar_segundo_factor(cred, codigo or "", "VERIFICA_TOTP"):
        raise ErrorServicio("Código incorrecto o ya usado")
    return cred


def cambiar_contrasena(usuario_id: int, actual: str, nueva: str, confirmacion: str, codigo: str) -> str:
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None or cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    if not verificar_password(actual, cred["password"]):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CLAVE")
        raise ErrorServicio("La contraseña actual no es correcta")
    motivo = problema_contrasena(nueva)
    if motivo:
        raise ErrorServicio(motivo)
    if nueva != confirmacion:
        raise ErrorServicio("Las contraseñas nuevas no coinciden")
    if not verificar_segundo_factor(cred, codigo or "", "VERIFICA_TOTP"):
        raise ErrorServicio("Código incorrecto o ya usado")
    repositorio.cambiar_password(cred["usuario_id"], hash_password(nueva), "CAMBIO_CLAVE")
    return "Contraseña actualizada"


def regenerar_codigos(usuario_id: int, codigo: str) -> list[str]:
    _cuenta_con_codigo(usuario_id, codigo)
    return nuevos_codigos_respaldo(usuario_id)


def iniciar_cambio_autenticador(usuario_id: int, codigo: str) -> str:
    """Paso 1: con un código del autenticador actual o de respaldo. Devuelve el
    secreto nuevo, que solo se guarda al confirmarlo."""
    _cuenta_con_codigo(usuario_id, codigo)
    return generar_secreto_totp()


def confirmar_autenticador(usuario_id: int, secreto: str, codigo: str) -> list[str]:
    """Paso 2: el secreto nuevo se guarda si su primer código es válido; se
    entregan códigos de respaldo nuevos."""
    paso = verificar_totp(secreto, codigo or "")
    if paso is None:
        repositorio.registrar_fallo_acceso(usuario_id, "FALLO_TOTP")
        raise ErrorServicio("El código no coincide con el nuevo autenticador")
    repositorio.guardar_totp(usuario_id, cifrar_secreto(secreto), paso)
    return nuevos_codigos_respaldo(usuario_id)


# ───────────────────── Verificación en dos pasos ─────────────────────


def _cuenta(usuario_id: int) -> dict:
    cred = repositorio.obtener_credenciales(usuario_id=usuario_id)
    if cred is None or cred["bloqueada"]:
        raise ErrorServicio(MENSAJE_BLOQUEO)
    return cred


def enviar_codigo_perfil(usuario_id: int) -> str:
    """Código por correo para confirmar una acción de seguridad del perfil (cuentas con el correo activo)."""
    cred = _cuenta(usuario_id)
    if not dos_pasos.de_cuenta(cred)["correo"]:
        raise ErrorServicio("Tu cuenta no usa el código por correo")
    enviar_codigo(cred, "Tu código para confirmar un cambio en PRPagos")
    return texto_codigo_enviado(cred["usuario"])


def iniciar_agregar_correo(usuario_id: int) -> str:
    """Agregar el código por correo: se envía un código para confirmarlo."""
    cred = _cuenta(usuario_id)
    if dos_pasos.de_cuenta(cred)["correo"]:
        raise ErrorServicio("El código por correo ya está activo")
    enviar_codigo(cred, "Activa el código por correo en PRPagos")
    return texto_codigo_enviado(cred["usuario"])


def confirmar_correo(usuario_id: int, codigo: str) -> str:
    cred = _cuenta(usuario_id)
    metodos = dos_pasos.de_cuenta(cred)
    limpio = "".join(c for c in (codigo or "") if c.isdigit())
    if len(limpio) != 6 or not repositorio.usar_codigo_correo(cred["usuario_id"], hash_corto(limpio)):
        repositorio.registrar_fallo_acceso(cred["usuario_id"], "FALLO_CORREO")
        raise ErrorServicio("Código incorrecto o vencido")
    repositorio.guardar_dos_pasos(
        cred["usuario_id"], dos_pasos.banderas(metodos["app"], True, metodos["preferido"] or dos_pasos.CORREO),
        "Código por correo agregado")
    return "El código por correo quedó activo"


def elegir_preferido(usuario_id: int, metodo: str) -> str:
    cred = _cuenta(usuario_id)
    metodos = dos_pasos.de_cuenta(cred)
    if metodo not in (dos_pasos.APP, dos_pasos.CORREO) or not metodos[metodo]:
        raise ErrorServicio("Ese método no está activo en tu cuenta")
    repositorio.guardar_dos_pasos(
        cred["usuario_id"], dos_pasos.banderas(metodos["app"], metodos["correo"], metodo),
        f"Método por defecto: {metodo}")
    return ("Ahora se pide la app autenticadora por defecto" if metodo == dos_pasos.APP
            else "Ahora se pide el código por correo por defecto")


# ───────────────────────── Llaves de acceso ─────────────────────────


def opciones_llave(usuario_id: int, nombre: str, codigo: str) -> tuple[str, str]:
    """(opciones WebAuthn en JSON, token del reto) para agregar este dispositivo."""
    nombre = llaves.nombre_valido(nombre)
    if nombre is None:
        raise ErrorServicio("Escribe un nombre para este dispositivo")
    cred = _cuenta_con_codigo(usuario_id, codigo)
    existentes = [llave["credencial_id"] for llave in repositorio.llaves(usuario_id)]
    reto, token = llaves.crear_reto("registro", usuario_id, nombre)
    return llaves.opciones_registro(usuario_id, cred["usuario"], cred["nombre"], existentes, reto), token


def registrar_llave(usuario_id: int, token_reto: str | None, credencial: dict) -> str:
    reto = llaves.leer_reto(token_reto, "registro")
    if reto is None or reto.get("usuario_id") != usuario_id:
        raise ErrorServicio("La solicitud expiró; empieza de nuevo.")
    try:
        credencial_id, clave_publica, contador = llaves.verificar_registro(credencial, reto["reto"])
    except llaves.ErrorLlave as error:
        raise ErrorServicio(str(error)) from error
    try:
        repositorio.guardar_llave(usuario_id, credencial_id, clave_publica, contador, reto["nombre"])
    except oracledb.IntegrityError:
        raise ErrorServicio("Esa llave de acceso ya está registrada") from None
    return "Llave de acceso guardada"


def eliminar_llave(usuario_id: int, llave_id: int) -> str:
    repositorio.eliminar_llave(usuario_id, llave_id)
    return "Llave de acceso eliminada"
