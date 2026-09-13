"""
Acceso a datos. Llama a los MISMOS procedimientos almacenados y hace las
MISMAS sentencias SQL que la version de escritorio (ver sql/DBPagos.sql en
la raiz del repo) -- no se reimplementa ninguna regla de negocio en Python,
solo se le habla a la base desde un cliente distinto.
"""
from datetime import date

import oracledb

from .database import obtener_conexion


def validar_login(usuario: str, contrasena_hash: str) -> tuple[str, int] | None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        v_nombre = cur.var(str)
        v_usuario_id = cur.var(int)
        cur.callproc("validar_login", [usuario, contrasena_hash, v_nombre, v_usuario_id])
        nombre = v_nombre.getvalue()
        usuario_id = v_usuario_id.getvalue()
        if nombre is None:
            return None
        return nombre, int(usuario_id)


def crear_usuario(usuario: str, nombre: str, contrasena_hash: str) -> bool:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        try:
            cur.execute(
                "INSERT INTO TBPLogin (id, usuario, nombre, password) "
                "VALUES (tblogin_sequence.NEXTVAL, :usuario, :nombre, :password)",
                usuario=usuario, nombre=nombre, password=contrasena_hash,
            )
        except oracledb.IntegrityError:
            # Choca con el UNIQUE de la columna 'usuario' -- ya existe esa cuenta.
            return False
        conn.commit()
        return True


def buscar_nombre_por_usuario(usuario: str) -> str | None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT nombre FROM TBPLogin WHERE usuario = :usuario", usuario=usuario)
        fila = cur.fetchone()
        return fila[0] if fila else None


def actualizar_password(usuario: str, nueva_contrasena_hash: str) -> bool:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPLogin SET password = :password WHERE usuario = :usuario",
            password=nueva_contrasena_hash, usuario=usuario,
        )
        afectadas = cur.rowcount
        conn.commit()
        return afectadas == 1


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
                "fecha": fecha,
                "concepto": concepto_fila,
            })
        return pagos


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
        return {"id_pago": fila[0], "monto": float(fila[1]), "fecha": fila[2], "concepto": fila[3]}
