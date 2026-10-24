"""
Acceso a datos. Llama a los MISMOS procedimientos almacenados y hace las
MISMAS sentencias SQL que la version de escritorio (ver sql/DBPagos.sql en
la raiz del repo) -- no se reimplementa ninguna regla de negocio en Python,
solo se le habla a la base desde un cliente distinto.
"""
from datetime import date, datetime

import oracledb

from .compartido import consulta
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
            # totp_activo guarda las banderas de los métodos (ver app/dos_pasos.py):
            # la app es el bit 1; "dos_pasos" conserva el valor completo.
            "totp_activo": bool(int(v_activo or 0) & 1),
            "dos_pasos": int(v_activo or 0),
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
    """Activa la app autenticadora. guardar_totp deja totp_activo = 1: si la
    cuenta ya usaba el correo, se conservan sus banderas (app/dos_pasos.py)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT totp_activo FROM TBPLogin WHERE id = :id", id=usuario_id)
        fila = cur.fetchone()
        previo = int(fila[0] or 0) if fila else 0
        cur.callproc("guardar_totp", [usuario_id, secreto_cifrado, paso])
        if previo & ~1:
            cur.execute("UPDATE TBPLogin SET totp_activo = :v WHERE id = :id", v=previo | 1, id=usuario_id)
            conn.commit()


def guardar_dos_pasos(usuario_id: int, valor: int, detalle: str) -> None:
    """Métodos de la verificación en dos pasos (banderas de app/dos_pasos.py)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE TBPLogin SET totp_activo = :v WHERE id = :id", v=valor, id=usuario_id)
        cur.execute(
            "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
            "VALUES (tbauditoria_sequence.NEXTVAL, :id, NULL, 'DOS_PASOS', :d)",
            id=usuario_id, d=detalle[:200],
        )
        conn.commit()


def envios_codigo_recientes(usuario_id: int, minutos: int) -> int:
    """Códigos enviados por correo en los últimos minutos (tope de envíos)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM TBAuditoria WHERE usuario_id = :id AND accion = 'CODIGO_CORREO' "
            "AND fecha_hora > SYSTIMESTAMP - NUMTODSINTERVAL(:m, 'MINUTE')",
            id=usuario_id, m=minutos,
        )
        return int(cur.fetchone()[0])


def registrar_envio_codigo(usuario_id: int, detalle: str) -> None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
            "VALUES (tbauditoria_sequence.NEXTVAL, :id, NULL, 'CODIGO_CORREO', :d)",
            id=usuario_id, d=detalle[:200],
        )
        conn.commit()


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
        # Lo que cuelga de la cuenta (no tiene registros, así que tampoco usa sus categorías).
        for tabla in ("TBCodigosRespaldo", "TBPresupuestoMensual", "TBPresupuestos", "TBEnviosResumen", "TBCategorias", "TBLlavesAcceso"):
            cur.execute(f"DELETE FROM {tabla} WHERE usuario_id = :id", id=usuario_id)
        cur.execute("DELETE FROM TBPLogin WHERE id = :id", id=usuario_id)
        afectadas = cur.rowcount
        conn.commit()
        return (afectadas == 1), ("" if afectadas == 1 else "No se encontró el usuario")






# ───────────────────────── Registros (ingresos y gastos) ─────────────────────────
# Las consultas vienen de compartido/consultas/ (las mismas del escritorio).


def _registro(fila, con_dueno: bool = False) -> dict:
    datos = {
        "id_pago": fila[0],
        "tipo": fila[1],
        "monto": float(fila[2]),
        "fecha": _solo_fecha(fila[3]),
        "concepto": fila[4],
        "categoria_id": fila[5],
        "categoria": fila[6],
    }
    if con_dueno:
        datos["usuario"], datos["nombre"] = fila[7], fila[8]
    return datos


def buscar_registros(usuario_id: int | None, filtros: dict) -> list[dict]:
    """usuario_id None = todos los usuarios (admin), con el dueño de cada uno."""
    params = {k: filtros.get(k) for k in ("tipo", "fecha_desde", "fecha_hasta", "concepto", "monto_min", "monto_max",
                                      "categoria")}
    with obtener_conexion() as conn:
        cur = conn.cursor()
        if usuario_id is None:
            cur.execute(consulta("registros_todos"), params)
            return [_registro(f, con_dueno=True) for f in cur]
        cur.execute(consulta("registros"), {**params, "usuario_id": usuario_id})
        return [_registro(f) for f in cur]


_SELECT_UNO = (
    "SELECT p.id_pago, p.tipo, p.monto, p.fecha, p.concepto, p.categoria_id, "
    "NVL(c.nombre, 'Sin categoría') FROM TBPagos p LEFT JOIN TBCategorias c ON c.id = p.categoria_id "
    "WHERE p.id_pago = :id_pago"
)


def obtener_registro(id_pago: int, usuario_id: int | None) -> dict | None:
    """El registro si es del usuario; con usuario_id None (admin), de cualquiera."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        if usuario_id is None:
            cur.execute(_SELECT_UNO, id_pago=id_pago)
        else:
            cur.execute(_SELECT_UNO + " AND p.usuario_id = :usuario_id", id_pago=id_pago, usuario_id=usuario_id)
        fila = cur.fetchone()
        return _registro(fila) if fila else None


def dueno_registro(id_pago: int) -> int | None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT usuario_id FROM TBPagos WHERE id_pago = :id", id=id_pago)
        fila = cur.fetchone()
        return fila[0] if fila else None


def insertar_registro(tipo: str, monto: float, fecha: date, concepto: str,
                      categoria_id: int | None, usuario_id: int) -> None:
    with obtener_conexion() as conn:
        conn.cursor().callproc("insertar_registro", [tipo, monto, fecha, concepto, categoria_id, usuario_id])


def actualizar_registro(id_pago: int, tipo: str, monto: float, fecha: date, concepto: str,
                        categoria_id: int | None, usuario_id: int) -> int:
    """Filas afectadas: 0 si el registro no es del usuario."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        v_filas = cur.var(int)
        cur.callproc("actualizar_registro",
                     [id_pago, tipo, monto, fecha, concepto, categoria_id, usuario_id, v_filas])
        return int(v_filas.getvalue() or 0)


def actualizar_registro_admin(id_pago: int, tipo: str, monto: float, fecha: date, concepto: str,
                              categoria_id: int | None, admin_id: int) -> bool:
    """El admin edita el registro de cualquiera; la auditoría queda a su nombre."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPagos SET tipo = :tipo, monto = :monto, fecha = :fecha, concepto = :concepto, "
            "categoria_id = :categoria_id WHERE id_pago = :id_pago",
            tipo=tipo, monto=monto, fecha=fecha, concepto=concepto, categoria_id=categoria_id, id_pago=id_pago,
        )
        afectadas = cur.rowcount
        if afectadas:
            cur.execute(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                "VALUES (tbauditoria_sequence.NEXTVAL, :admin_id, :id_pago, 'ACTUALIZAR (admin)', :detalle)",
                admin_id=admin_id, id_pago=id_pago,
                detalle=f"{tipo} {monto}, Concepto: {concepto[:120]}",
            )
        conn.commit()
        return afectadas == 1


def eliminar_pago(id_pago: int, admin_id: int) -> bool:
    """Solo el admin. Se deja el registro de la eliminación en la auditoría."""
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


# ───────────────────────── Categorías y plan ─────────────────────────


def categorias(usuario_id: int) -> list[dict]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("categorias"), usuario_id=usuario_id)
        return [
            {"id": f[0], "nombre": f[1], "tipo": f[2], "grupo": f[3], "propia": bool(f[4]), "usos": int(f[5])}
            for f in cur
        ]


def crear_categoria(usuario_id: int, nombre: str, tipo: str, grupo: str | None) -> bool:
    """False si ya tiene una categoría con ese nombre y tipo (propia o sugerida)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT COUNT(*) FROM TBCategorias WHERE UPPER(nombre) = UPPER(:nombre) AND tipo = :tipo "
            "AND (usuario_id IS NULL OR usuario_id = :usuario_id)",
            nombre=nombre, tipo=tipo, usuario_id=usuario_id,
        )
        if cur.fetchone()[0]:
            return False
        cur.execute(
            "INSERT INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) "
            "VALUES (tbcategoria_sequence.NEXTVAL, :usuario_id, :nombre, :tipo, :grupo)",
            usuario_id=usuario_id, nombre=nombre, tipo=tipo, grupo=grupo,
        )
        conn.commit()
        return True


def eliminar_categoria(usuario_id: int, categoria_id: int) -> str | None:
    """None si se eliminó; si no, el motivo."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM TBPagos WHERE categoria_id = :id", id=categoria_id)
        if cur.fetchone()[0]:
            return "Esa categoría tiene registros: cámbialos de categoría antes de eliminarla"
        # Su presupuesto se va con ella (solo el de su dueño: es una categoría propia).
        cur.execute("DELETE FROM TBPresupuestoMensual WHERE categoria_id = :id AND usuario_id = :usuario_id "
                    "AND EXISTS (SELECT 1 FROM TBCategorias c WHERE c.id = :id AND c.usuario_id = :usuario_id)",
                    id=categoria_id, usuario_id=usuario_id)
        cur.execute("DELETE FROM TBCategorias WHERE id = :id AND usuario_id = :usuario_id",
                    id=categoria_id, usuario_id=usuario_id)
        afectadas = cur.rowcount
        conn.commit()
        return None if afectadas == 1 else "Solo puedes eliminar tus propias categorías"


def plan(usuario_id: int) -> dict[str, float]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("plan"), usuario_id=usuario_id)
        n, d, a = cur.fetchone()
        return {"NECESIDADES": float(n), "DESEOS": float(d), "AHORRO": float(a)}


def guardar_plan(usuario_id: int, necesidades: float, deseos: float, ahorro: float) -> None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "MERGE INTO TBPresupuestos p USING (SELECT :usuario_id AS usuario_id FROM dual) s "
            "ON (p.usuario_id = s.usuario_id) "
            "WHEN MATCHED THEN UPDATE SET pct_necesidades = :n, pct_deseos = :d, pct_ahorro = :a "
            "WHEN NOT MATCHED THEN INSERT (usuario_id, pct_necesidades, pct_deseos, pct_ahorro) "
            "VALUES (:usuario_id, :n, :d, :a)",
            usuario_id=usuario_id, n=necesidades, d=deseos, a=ahorro,
        )
        conn.commit()


# ───────────────────────── Perfil ─────────────────────────


def perfil(usuario_id: int) -> dict | None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT usuario, nombre, notificaciones, "
            "(SELECT COUNT(*) FROM TBCodigosRespaldo r WHERE r.usuario_id = l.id AND r.usado_en IS NULL) "
            "FROM TBPLogin l WHERE id = :id",
            id=usuario_id,
        )
        f = cur.fetchone()
        if not f:
            return None
        return {"usuario": f[0], "nombre": f[1], "notificaciones": bool(f[2]), "respaldo_restantes": int(f[3])}


def guardar_datos_perfil(usuario_id: int, nombre: str, notificaciones: bool) -> None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE TBPLogin SET nombre = :nombre, notificaciones = :n WHERE id = :id",
                    nombre=nombre, n=1 if notificaciones else 0, id=usuario_id)
        conn.commit()


# ───────────────────── Recuperación (respaldo y correo) ─────────────────────


def reemplazar_codigos_respaldo(usuario_id: int, hashes: list[str]) -> None:
    """Los códigos nuevos anulan todos los anteriores."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("DELETE FROM TBCodigosRespaldo WHERE usuario_id = :id", id=usuario_id)
        cur.executemany(
            "INSERT INTO TBCodigosRespaldo (id, usuario_id, codigo_hash) "
            "VALUES (tbrespaldo_sequence.NEXTVAL, :usuario_id, :codigo_hash)",
            [{"usuario_id": usuario_id, "codigo_hash": h} for h in hashes],
        )
        conn.commit()


def usar_codigo_respaldo(usuario_id: int, codigo_hash: str) -> bool:
    """Lo marca como usado; False si no existe o ya se había usado."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBCodigosRespaldo SET usado_en = SYSTIMESTAMP "
            "WHERE usuario_id = :usuario_id AND codigo_hash = :codigo_hash AND usado_en IS NULL",
            usuario_id=usuario_id, codigo_hash=codigo_hash,
        )
        afectadas = cur.rowcount
        if afectadas:
            cur.execute(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                "VALUES (tbauditoria_sequence.NEXTVAL, :id, NULL, 'USO_RESPALDO', 'Código de respaldo usado')",
                id=usuario_id,
            )
        conn.commit()
        return afectadas == 1


def reiniciar_intentos(usuario_id: int) -> None:
    """Tras un ingreso válido con código de respaldo (que no tiene paso TOTP)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE TBPLogin SET intentos_fallidos = 0, bloqueo_hasta = NULL WHERE id = :id", id=usuario_id)
        conn.commit()


def guardar_codigo_correo(usuario_id: int, codigo_hash: str, minutos: int) -> None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPLogin SET recup_hash = :h, "
            "recup_expira = SYSTIMESTAMP + NUMTODSINTERVAL(:m, 'MINUTE') WHERE id = :id",
            h=codigo_hash, m=minutos, id=usuario_id,
        )
        conn.commit()


def usar_codigo_correo(usuario_id: int, codigo_hash: str) -> bool:
    """Válido una sola vez y antes de vencer; al usarse se borra."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "UPDATE TBPLogin SET recup_hash = NULL, recup_expira = NULL "
            "WHERE id = :id AND recup_hash = :h AND recup_expira > SYSTIMESTAMP",
            id=usuario_id, h=codigo_hash,
        )
        afectadas = cur.rowcount
        if afectadas:
            cur.execute(
                "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
                "VALUES (tbauditoria_sequence.NEXTVAL, :id, NULL, 'RECUPERA_CORREO', 'Código por correo usado')",
                id=usuario_id,
            )
        conn.commit()
        return afectadas == 1


# ───────────────────────── Dashboard y resúmenes ─────────────────────────


def totales(usuario_id: int, desde: date, hasta: date) -> tuple[float, float, int]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("totales_periodo"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        i, g, n = cur.fetchone()
        return float(i), float(g), int(n)


def serie_meses(usuario_id: int, desde: date, hasta: date) -> list[tuple[date, float, float]]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("serie_meses"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        return [(_solo_fecha(m), float(i), float(g)) for m, i, g in cur]


def gastos_por_categoria(usuario_id: int, desde: date, hasta: date) -> list[tuple[str, str, float]]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("gastos_categoria"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        return [(c, g, float(t)) for c, g, t in cur]


def gastos_por_grupo(usuario_id: int, desde: date, hasta: date) -> dict[str, float]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("gastos_grupo"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        return {g: float(t) for g, t in cur}


def destinatarios_resumen() -> list[dict]:
    """Cuentas activas con los resúmenes activados."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT id, usuario, nombre FROM TBPLogin WHERE activo = 1 AND notificaciones = 1 ORDER BY id")
        return [{"usuario_id": f[0], "usuario": f[1], "nombre": f[2]} for f in cur]


def marcar_envio(usuario_id: int, tipo: str, periodo: str) -> bool:
    """False si ese resumen ya se había enviado (la tarea se repitió)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        try:
            cur.execute(
                "INSERT INTO TBEnviosResumen (usuario_id, tipo, periodo) VALUES (:u, :t, :p)",
                u=usuario_id, t=tipo, p=periodo,
            )
        except oracledb.IntegrityError:
            return False
        conn.commit()
        return True


def desmarcar_envio(usuario_id: int, tipo: str, periodo: str) -> None:
    """Si el envío falló, se borra la marca para reintentarlo después."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("DELETE FROM TBEnviosResumen WHERE usuario_id = :u AND tipo = :t AND periodo = :p",
                    u=usuario_id, t=tipo, p=periodo)
        conn.commit()


# ───────────────────────── Presupuesto mensual ─────────────────────────


def presupuesto_anual(usuario_id: int, desde: date, hasta: date) -> dict[tuple[int, date], float]:
    """{(categoria_id, mes): monto} presupuestado en [desde, hasta)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("presupuesto_anual"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        return {(int(c), _solo_fecha(m)): float(t) for c, m, t in cur}


def real_por_categoria_mes(usuario_id: int, desde: date,
                           hasta: date) -> dict[tuple[int | None, date, str], float]:
    """{(categoria_id o None, mes, tipo): total} registrado en [desde, hasta)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("real_categoria_mes"), usuario_id=usuario_id, desde=desde, hasta=hasta)
        return {(None if c is None else int(c), _solo_fecha(m), t): float(v) for c, m, t, v in cur}


def guardar_presupuesto(usuario_id: int, montos: list[tuple[int, date, float]], detalle: str) -> None:
    """Fija cada (categoría, mes, monto) en una sola transacción; monto 0 borra la fila."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        borrar = [{"u": usuario_id, "c": c, "m": m} for c, m, v in montos if v <= 0]
        fijar = [{"u": usuario_id, "c": c, "m": m, "v": v} for c, m, v in montos if v > 0]
        if borrar:
            cur.executemany("DELETE FROM TBPresupuestoMensual WHERE usuario_id = :u AND categoria_id = :c "
                            "AND mes = :m", borrar)
        if fijar:
            cur.executemany(
                "MERGE INTO TBPresupuestoMensual p USING (SELECT :u u, :c c, :m m FROM dual) s "
                "ON (p.usuario_id = s.u AND p.categoria_id = s.c AND p.mes = s.m) "
                "WHEN MATCHED THEN UPDATE SET monto = :v "
                "WHEN NOT MATCHED THEN INSERT (usuario_id, categoria_id, mes, monto) VALUES (:u, :c, :m, :v)",
                fijar,
            )
        cur.execute(
            "INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle) "
            "VALUES (tbauditoria_sequence.NEXTVAL, :u, NULL, 'PRESUPUESTO', :d)",
            u=usuario_id, d=detalle[:200],
        )
        conn.commit()


# ───────────────────────── Versiones del escritorio ─────────────────────────


def publicar_version_escritorio(version: str, url: str, sha256: str) -> None:
    """Registra (o corrige) una versión publicada en GitHub Releases."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(
            "MERGE INTO TBVersionesEscritorio v USING (SELECT :v version FROM dual) n "
            "ON (v.version = n.version) "
            "WHEN MATCHED THEN UPDATE SET url = :u, sha256 = :s, publicada = SYSTIMESTAMP "
            "WHEN NOT MATCHED THEN INSERT (version, url, sha256) VALUES (:v, :u, :s)",
            v=version, u=url, s=sha256,
        )
        conn.commit()


# ───────────────────────── Llaves de acceso ─────────────────────────


def llaves(usuario_id: int) -> list[dict]:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("llaves_usuario"), usuario_id=usuario_id)
        return [{"id": int(i), "nombre": n, "creada": c, "ultimo_uso": u, "credencial_id": cid}
                for i, n, c, u, cid in cur]


def llave_por_credencial(credencial_id: str) -> dict | None:
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute(consulta("llave_por_credencial"), credencial_id=credencial_id)
        fila = cur.fetchone()
        if fila is None:
            return None
        return {"id": int(fila[0]), "usuario_id": int(fila[1]), "clave_publica": fila[2],
                "contador": int(fila[3])}


def guardar_llave(usuario_id: int, credencial_id: str, clave_publica: str, contador: int,
                  nombre: str) -> None:
    with obtener_conexion() as conn:
        conn.cursor().execute(consulta("llave_nueva"), usuario_id=usuario_id, credencial_id=credencial_id,
                              clave_publica=clave_publica, contador=contador, nombre=nombre)


def usar_llave(llave_id: int, usuario_id: int, contador: int) -> None:
    with obtener_conexion() as conn:
        conn.cursor().execute(consulta("llave_usada"), id=llave_id, usuario_id=usuario_id, contador=contador)


def eliminar_llave(usuario_id: int, llave_id: int) -> None:
    with obtener_conexion() as conn:
        conn.cursor().execute(consulta("llave_eliminar"), id=llave_id, usuario_id=usuario_id)


def ultima_version_escritorio() -> dict | None:
    """Última versión publicada en GitHub Releases (la registra escritorio.yml)."""
    with obtener_conexion() as conn:
        cur = conn.cursor()
        cur.execute("SELECT version, url, sha256 FROM TBVersionesEscritorio "
                    "ORDER BY publicada DESC FETCH FIRST 1 ROWS ONLY")
        fila = cur.fetchone()
        return {"version": fila[0], "url": fila[1], "sha256": fila[2]} if fila else None
