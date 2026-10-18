"""Gestión de cuentas (rol admin): listar, suspender, reactivar y eliminar."""
from .. import repositorio
from . import ErrorServicio


def listar() -> list[dict]:
    return repositorio.listar_usuarios()


def suspender(admin_id: int, usuario_id: int) -> str:
    # Un admin no puede suspender su propia cuenta: se quedaría afuera sin
    # que nadie más pueda reactivarla.
    if usuario_id == admin_id:
        raise ErrorServicio("No puedes suspender tu propia cuenta")
    repositorio.suspender_usuario(usuario_id)
    return "Cuenta suspendida"


def reactivar(usuario_id: int) -> str:
    repositorio.reactivar_usuario(usuario_id)
    return "Cuenta reactivada"


def eliminar(admin_id: int, usuario_id: int) -> str:
    if usuario_id == admin_id:
        raise ErrorServicio("No puedes eliminar tu propia cuenta")
    exito, motivo = repositorio.eliminar_usuario(usuario_id)
    if not exito:
        raise ErrorServicio(motivo)
    return "Cuenta eliminada"
