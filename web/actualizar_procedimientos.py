"""
Actualiza los procedimientos almacenados de PRPagos en la base de datos,
SIN tocar ninguna tabla ni dato existente (CREATE OR REPLACE PROCEDURE no
borra filas, solo redefine la logica del procedimiento).

Usa las mismas credenciales y el mismo Wallet que la app web (el .env de
esta carpeta), asi que no hace falta sqlplus ni SQLcl instalados.

Uso, parado en la carpeta web/ con la venv activada:
    python actualizar_procedimientos.py
"""
import oracledb

from app.config import settings

PROCEDIMIENTOS = [
    """
    CREATE OR REPLACE PROCEDURE validar_login(
        p_usuario IN VARCHAR2,
        p_contrasena_hash IN VARCHAR2,
        p_nombre OUT VARCHAR2,
        p_usuario_id OUT NUMBER
    )
    IS
    BEGIN
        SELECT nombre, id
        INTO p_nombre, p_usuario_id
        FROM TBPLogin
        WHERE usuario = p_usuario
          AND password = p_contrasena_hash;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_nombre := NULL;
            p_usuario_id := NULL;
    END validar_login;
    """,
    """
    CREATE OR REPLACE PROCEDURE insertar_tbpago(
        p_monto IN NUMBER,
        p_fecha IN DATE,
        p_concepto IN VARCHAR2,
        p_usuario_id IN NUMBER
    ) AS
        v_id_pago NUMBER;
    BEGIN
        v_id_pago := tbpago_sequence.NEXTVAL;

        INSERT INTO TBPagos (id_pago, monto, fecha, concepto, usuario_id)
        VALUES (v_id_pago, p_monto, p_fecha, p_concepto, p_usuario_id);

        INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
        VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, v_id_pago, 'INSERTAR',
                'Monto: ' || p_monto || ', Concepto: ' || p_concepto);

        COMMIT;
    END insertar_tbpago;
    """,
    """
    CREATE OR REPLACE PROCEDURE actualizar_tbpago(
        p_id_pago IN NUMBER,
        p_nuevo_monto IN NUMBER,
        p_nueva_fecha IN DATE,
        p_nuevo_concepto IN VARCHAR2,
        p_usuario_id IN NUMBER,
        p_filas_afectadas OUT NUMBER
    )
    AS
    BEGIN
        UPDATE TBPagos
        SET monto = p_nuevo_monto,
            fecha = p_nueva_fecha,
            concepto = p_nuevo_concepto
        WHERE id_pago = p_id_pago
          AND usuario_id = p_usuario_id;

        p_filas_afectadas := SQL%ROWCOUNT;

        IF p_filas_afectadas > 0 THEN
            INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
            VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, p_id_pago, 'ACTUALIZAR',
                    'Nuevo monto: ' || p_nuevo_monto || ', Nuevo concepto: ' || p_nuevo_concepto);
        END IF;

        COMMIT;
    END actualizar_tbpago;
    """,
    """
    CREATE OR REPLACE PROCEDURE seleccionar_TBPagos(
        p_usuario_id IN NUMBER,
        cur OUT SYS_REFCURSOR
    ) AS
    BEGIN
        OPEN cur FOR
        SELECT id_pago, monto, fecha, concepto
        FROM TBPagos
        WHERE usuario_id = p_usuario_id
        ORDER BY fecha DESC;
    END seleccionar_TBPagos;
    """,
    """
    CREATE OR REPLACE PROCEDURE buscar_tbpagos(
        p_usuario_id IN NUMBER,
        p_fecha_desde IN DATE DEFAULT NULL,
        p_fecha_hasta IN DATE DEFAULT NULL,
        p_concepto IN VARCHAR2 DEFAULT NULL,
        p_monto_min IN NUMBER DEFAULT NULL,
        p_monto_max IN NUMBER DEFAULT NULL,
        cur OUT SYS_REFCURSOR
    ) AS
    BEGIN
        OPEN cur FOR
        SELECT id_pago, monto, fecha, concepto
        FROM TBPagos
        WHERE usuario_id = p_usuario_id
          AND (p_fecha_desde IS NULL OR fecha >= p_fecha_desde)
          AND (p_fecha_hasta IS NULL OR fecha <= p_fecha_hasta)
          AND (p_concepto IS NULL OR UPPER(concepto) LIKE '%' || UPPER(p_concepto) || '%')
          AND (p_monto_min IS NULL OR monto >= p_monto_min)
          AND (p_monto_max IS NULL OR monto <= p_monto_max)
        ORDER BY fecha DESC;
    END buscar_tbpagos;
    """,
]


def main():
    settings.validar()
    conn = oracledb.connect(
        user=settings.db_usuario,
        password=settings.db_password,
        dsn=settings.db_alias,
        config_dir=settings.wallet_path,
        wallet_location=settings.wallet_path,
        wallet_password=settings.wallet_password,
    )
    try:
        cur = conn.cursor()
        for i, sql in enumerate(PROCEDIMIENTOS, start=1):
            cur.execute(sql)
            print(f"[{i}/{len(PROCEDIMIENTOS)}] procedimiento actualizado")
        conn.commit()
    finally:
        conn.close()
    print("Listo: los 5 procedimientos quedaron actualizados. Ninguna tabla se modificó.")


if __name__ == "__main__":
    main()
