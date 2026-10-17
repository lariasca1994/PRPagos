"""
Acceso a datos. Llama a los MISMOS procedimientos almacenados y hace las
MISMAS sentencias SQL que la version de escritorio (ver sql/DBPagos.sql en
la raiz del repo) -- no se reimplementa ninguna regla de negocio en Python,
solo se le habla a la base desde un cliente distinto.
"""
from datetime import date, datetime

import oracledb

from .database import obtener_conexion


def _solo_fecha(valor):
    """Oracle devuelve las columnas DATE como datetime (con 00:00:00). Se
    entrega solo la fecha, como la muestra el escritorio y como la espera
    <input type="date">."""
    return valor.date() if isinstance(valor, datetime) else valor




def obtener_credenciales(
    usuario: str | None = None, usuario_id: int | None = None
) -> dict | None:
    """Datos de acceso de una cuenta activa, por correo o por id (ver el
    procedimiento obtener_credenciales en sql/seguridad.sql). La contraseña
    y el código TOTP se verifican en seguridad.py, no en la base."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        salidas = [
            cur.var(int), cur.var(str), cur.var(str), cur.var(str),
            cur.var(str), cur.var(int), cur.var(int), cur.var(int),
        ]
        cur.callproc("obtener_credenciales", [usuario, usuario_id, *salidas])
        (v_id, v_correo, v_nombre, v_password, v_secreto, v_activo, v_paso,
         v_bloqueada) = (s.getvalue() for s in salidas)
        if v_id is None:
            return None
        return {
            "usuario_id": int(v_id),
            "usuario": v_correo,
            "nombre": v_nombre,
            "password": v_password,
            "totp_secreto": v_secreto,
            "totp_activo": bool(v_activo),
            "totp_ultimo_paso": int(v_paso or 0),
            "bloqueada": bool(v_bloqueada),
        }


def registrar_fallo_acceso(usuario_id: int, motivo: str) -> None:
    """motivo: FALLO_CLAVE, FALLO_TOTP o FALLO_RECUPERAR. Al quinto fallo
    seguido la cuenta queda bloqueada 5 minutos."""
    with obtener_conexion() as conn:
        conn.cursor().callproc("registrar_fallo_acceso", [usuario_id, motivo])


def registrar_acceso_exitoso(usuario_id: int, paso: int, motivo: str = "INGRESO") -> bool:
    """False si ese paso TOTP ya se había usado (otra petición ganó con el
    mismo código)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        v_aceptado = cur.var(int)
        cur.callproc("registrar_acceso_exitoso", [usuario_id, paso, motivo, v_aceptado])
        return bool(v_aceptado.getvalue())


def guardar_totp(usuario_id: int, secreto_cifrado: str, paso: int) -> None:
    with obtener_conexion() as conn:
        conn.cursor().callproc("guardar_totp", [usuario_id, secreto_cifrado, paso])


def cambiar_password(usuario_id: int, contrasena_hash: str, motivo: str) -> None:
    """motivo: CAMBIO_CLAVE, RECUPERAR_CLAVE o MIGRAR_HASH."""
    with obtener_conexion() as conn:
        conn.cursor().callproc("cambiar_password", [usuario_id, contrasena_hash, motivo])


def crear_usuario(usuario: str, nombre: str, contrasena_hash: str, rol: str = "usuario") -> bool:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        try:
            cur.execute(
                "INSERT INTO TBPLogin (id, usuario, nombre, password, rol, activo) "
                "VALUES (tblogin_sequence.NEXTVAL, :usuario, :nombre, :password, :rol, 1)",
                usuario=usuario, nombre=nombre, password=contrasena_hash, rol=rol,
            )
        except oracledb.IntegrityError:
            # Choca con el UNIQUE de la columna 'usuario' -- ya existe esa cuenta.
            return False
        conn.commit()
        return True


def ascender_a_admin(usuario: str, contrasena_hash: str) -> bool:
    """Solo la usa el script de semilla (comandos/crear_admin.py) para las
    2 cuentas admin reales del portafolio. Si la cuenta ya existia (por
    ejemplo porque ya se habia registrado como usuario comun antes), la
    asciende a rol='admin', la reactiva si estaba suspendida, y le
    actualiza la contrasena a la indicada -- asi el valor que se le pasa
    al script es siempre, de forma predecible, la contrasena que queda."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPLogin SET rol = 'admin', activo = 1, password = :password "
            "WHERE usuario = :usuario",
            password=contrasena_hash, usuario=usuario,
        )
        afectadas = cur.rowcount
        conn.commit()
        return afectadas == 1


def obtener_estado_usuario(usuario_id: int) -> dict | None:
    """Rol y estado activo/suspendido, consultados DIRECTO (no via
    validar_login, que la version de escritorio tambien llama y por eso no
    cambia de firma -- ver sql/DBPagos.sql). Se re-consulta en cada
    request (dependencias.py), no alcanza con lo que dice el JWT, para que
    una suspension o un cambio de rol hecho por el admin tengan efecto
    inmediato -- mismo patron que el resto del portafolio."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT rol, activo FROM TBPLogin WHERE id = :id", id=usuario_id)
        fila = cur.fetchone()
        if not fila:
            return None
        return {"rol": fila[0], "activo": bool(fila[1])}


def listar_usuarios() -> list[dict]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT id, usuario, nombre, rol, activo FROM TBPLogin ORDER BY nombre")
        usuarios = []
        for id_, usuario, nombre, rol, activo in cur:
            usuarios.append(
                {"id": id_, "usuario": usuario, "nombre": nombre, "rol": rol, "activo": bool(activo)}
            )
        return usuarios


def suspender_usuario(usuario_id: int) -> bool:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE TBPLogin SET activo = 0 WHERE id = :id", id=usuario_id)
        afectadas = cur.rowcount
        conn.commit()
        return afectadas == 1


def reactivar_usuario(usuario_id: int) -> bool:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE TBPLogin SET activo = 1 WHERE id = :id", id=usuario_id)
        afectadas = cur.rowcount
        conn.commit()
        return afectadas == 1


def eliminar_usuario(usuario_id: int) -> tuple[bool, str]:
    """No elimina si la cuenta tiene pagos o auditoria de pagos asociados
    (choca con el FK de TBPagos/TBAuditoria hacia TBPLogin) -- en ese caso
    hay que suspender en vez de eliminar, mismo patron que verificador-api.
    La auditoria de ACCESO (ingresos, intentos fallidos, cambios de
    contrasena; id_pago NULL) se borra junto con la cuenta: si no, ninguna
    cuenta que haya ingresado alguna vez se podria eliminar.
    Devuelve (exito, motivo_si_fallo)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM TBPagos WHERE usuario_id = :id", id=usuario_id)
        if cur.fetchone()[0] > 0:
            return False, "Esa cuenta tiene pagos registrados -- suspéndela en vez de eliminarla"
        cur.execute(
            "SELECT COUNT(*) FROM TBAuditoria WHERE usuario_id = :id AND id_pago IS NOT NULL",
            id=usuario_id,
        )
        if cur.fetchone()[0] > 0:
            return False, "Esa cuenta tiene historial de auditoría -- suspéndela en vez de eliminarla"
        cur.execute(
            "DELETE FROM TBAuditoria WHERE usuario_id = :id AND id_pago IS NULL", id=usuario_id
        )
        cur.execute("DELETE FROM TBPLogin WHERE id = :id", id=usuario_id)
        afectadas = cur.rowcount
        conn.commit()
        return (afectadas == 1), ("" if afectadas == 1 else "No se encontró el usuario")






def insertar_pago(monto: float, fecha: date, concepto: str, usuario_id: int) -> None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.callproc("insertar_tbpago", [monto, fecha, concepto, usuario_id])
        conn.commit()


def actualizar_pago(
    id_pago: int, nuevo_monto: float, nueva_fecha: date, nuevo_concepto: str, usuario_id: int
) -> int:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        v_filas_afectadas = cur.var(int)
        cur.callproc(
            "actualizar_tbpago",
            [id_pago, nuevo_monto, nueva_fecha, nuevo_concepto, usuario_id, v_filas_afectadas],
        )
        conn.commit()
        return int(v_filas_afectadas.getvalue() or 0)


def buscar_pagos(
    usuario_id: int,
    fecha_desde: date | None = None,
    fecha_hasta: date | None = None,
    concepto: str | None = None,
    monto_min: float | None = None,
    monto_max: float | None = None,
) -> list[dict]:
    """Antes llamaba a buscar_tbpagos (que devuelve un SYS_REFCURSOR como
    parametro de salida) via cur.callproc(...) + cur.var(oracledb.CURSOR).
    En el contenedor de Cloud Run esa combinacion se quedaba colgada sin
    error ni timeout de Python (confirmado: insertar/actualizar, que usan
    solo parametros escalares, funcionaban bien; solo el REF CURSOR se
    trababa). Se reemplaza por la MISMA consulta y los MISMOS filtros que
    tiene buscar_tbpagos en sql/DBPagos.sql, pero como SELECT directo -- no
    se cambia ninguna regla de negocio, solo la forma de traer el
    resultado."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT id_pago, monto, fecha, concepto
            FROM TBPagos
            WHERE usuario_id = :usuario_id
              AND (:fecha_desde IS NULL OR fecha >= :fecha_desde)
              AND (:fecha_hasta IS NULL OR fecha <= :fecha_hasta)
              AND (:concepto IS NULL OR UPPER(concepto) LIKE '%' || UPPER(:concepto) || '%')
              AND (:monto_min IS NULL OR monto >= :monto_min)
              AND (:monto_max IS NULL OR monto <= :monto_max)
            ORDER BY fecha DESC
            """,
            usuario_id=usuario_id,
            fecha_desde=fecha_desde,
            fecha_hasta=fecha_hasta,
            concepto=concepto,
            monto_min=monto_min,
            monto_max=monto_max,
        )
        pagos = []
        for id_pago, monto, fecha, concepto_fila in cur:
            pagos.append({
                "id_pago": id_pago,
                "monto": float(monto),
                "fecha": _solo_fecha(fecha),
                "concepto": concepto_fila,
            })
        return pagos


def buscar_todos_los_pagos(
    fecha_desde: date | None = None,
    fecha_hasta: date | None = None,
    concepto: str | None = None,
    monto_min: float | None = None,
    monto_max: float | None = None,
) -> list[dict]:
    """Como buscar_pagos, pero para el admin: sin filtrar por dueño, y con
    el correo/nombre del dueño de cada pago (join contra TBPLogin) para
    que se pueda ver de quién es cada uno en la lista."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            SELECT p.id_pago, p.monto, p.fecha, p.concepto, p.usuario_id, u.usuario, u.nombre
            FROM TBPagos p
            JOIN TBPLogin u ON u.id = p.usuario_id
            WHERE (:fecha_desde IS NULL OR p.fecha >= :fecha_desde)
              AND (:fecha_hasta IS NULL OR p.fecha <= :fecha_hasta)
              AND (:concepto IS NULL OR UPPER(p.concepto) LIKE '%' || UPPER(:concepto) || '%')
              AND (:monto_min IS NULL OR p.monto >= :monto_min)
              AND (:monto_max IS NULL OR p.monto <= :monto_max)
            ORDER BY p.fecha DESC
            """,
            fecha_desde=fecha_desde,
            fecha_hasta=fecha_hasta,
            concepto=concepto,
            monto_min=monto_min,
            monto_max=monto_max,
        )
        pagos = []
        for id_pago, monto, fecha, concepto_fila, usuario_id, correo_dueno, nombre_dueno in cur:
            pagos.append({
                "id_pago": id_pago,
                "monto": float(monto),
                "fecha": _solo_fecha(fecha),
                "concepto": concepto_fila,
                "usuario_id": usuario_id,
                "usuario": correo_dueno,
                "nombre": nombre_dueno,
            })
        return pagos


def obtener_pago_por_id(id_pago: int) -> dict | None:
    """Como obtener_pago_de_usuario, pero sin exigir dueño -- para que el
    admin pueda abrir el formulario de editar el pago de cualquiera."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT id_pago, monto, fecha, concepto FROM TBPagos WHERE id_pago = :id_pago",
            id_pago=id_pago,
        )
        fila = cur.fetchone()
        if not fila:
            return None
        return {"id_pago": fila[0], "monto": float(fila[1]), "fecha": _solo_fecha(fila[2]), "concepto": fila[3]}


def actualizar_pago_admin(
    id_pago: int, nuevo_monto: float, nueva_fecha: date, nuevo_concepto: str, admin_id: int
) -> bool:
    """Como actualizar_pago, pero sin exigir que el pago sea del que edita
    -- por eso no llama al procedimiento actualizar_tbpago (que filtra
    a proposito por usuario_id = dueño; esa es justo la regla que un admin
    tiene que poder saltarse). Deja el mismo rastro de auditoria que deja
    el procedimiento, con admin_id como quien hizo el cambio."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPagos SET monto = :monto, fecha = :fecha, concepto = :concepto "
            "WHERE id_pago = :id_pago",
            monto=nuevo_monto, fecha=nueva_fecha, concepto=nuevo_concepto, id_pago=id_pago,
        )
        afectadas = cur.rowcount
        if afectadas:
            cur.execute(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                "VALUES (tbauditoria_sequence.NEXTVAL, :admin_id, :id_pago, 'ACTUALIZAR (admin)', :detalle)",
                admin_id=admin_id,
                id_pago=id_pago,
                detalle=f"Nuevo monto: {nuevo_monto}, Nuevo concepto: {nuevo_concepto}",
            )
        conn.commit()
        return afectadas == 1


def eliminar_pago(id_pago: int, admin_id: int) -> bool:
    """Eliminar un pago no existía ni en la version de escritorio ni en la
    web hasta ahora -- es una accion exclusiva del admin. TBAuditoria.id_pago
    no tiene FK hacia TBPagos (es solo un NUMBER suelto), así que no hay
    que borrar auditoria antes; se deja un registro de la eliminación en
    vez de borrar el rastro."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
            "VALUES (tbauditoria_sequence.NEXTVAL, :admin_id, :id_pago, 'ELIMINAR (admin)', 'Pago eliminado')",
            admin_id=admin_id, id_pago=id_pago,
        )
        cur.execute("DELETE FROM TBPagos WHERE id_pago = :id_pago", id_pago=id_pago)
        afectadas = cur.rowcount
        conn.commit()
        return afectadas == 1


def obtener_pago_de_usuario(id_pago: int, usuario_id: int) -> dict | None:
    """No existe un procedimiento para traer un solo pago en la version
    Java (la ventana de modificar recibia los datos directo de la fila ya
    seleccionada en la tabla) -- aca hace falta porque cada request es
    independiente. Es una consulta directa, de solo lectura, no una regla
    de negocio nueva."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT id_pago, monto, fecha, concepto FROM TBPagos "
            "WHERE id_pago = :id_pago AND usuario_id = :usuario_id",
            id_pago=id_pago, usuario_id=usuario_id,
        )
        fila = cur.fetchone()
        if not fila:
            return None
        return {"id_pago": fila[0], "monto": float(fila[1]), "fecha": _solo_fecha(fila[2]), "concepto": fila[3]}
