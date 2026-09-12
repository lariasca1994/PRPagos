-- DBPagos.sql
-- Pensado para correr directamente en el usuario ADMIN de una Oracle
-- Autonomous Database (Always Free).
--
-- Si ya corriste una version anterior de este script, corre primero:
--   DROP TABLE TBAuditoria PURGE;
--   DROP TABLE TBPagos PURGE;
--   DROP TABLE TBPLogin PURGE;
--   DROP SEQUENCE tbpago_sequence;
--   DROP SEQUENCE tblogin_sequence;
--   DROP SEQUENCE tbauditoria_sequence;
-- antes de volver a correr este script completo, o los CREATE fallaran
-- porque los objetos ya existen.

CREATE TABLE TBPLogin (
    id NUMBER PRIMARY KEY,
    usuario VARCHAR2(100) UNIQUE,
    nombre VARCHAR2(100),
    password VARCHAR2(100)
);

-- CORRECCION: TBPagos ahora sabe de quien es cada pago (usuario_id).
-- Antes cualquier usuario logueado veia y podia modificar los pagos de
-- todos los demas, porque no existia esta relacion.
CREATE TABLE TBPagos (
    id_pago NUMBER PRIMARY KEY,
    monto NUMBER,
    fecha DATE,
    concepto VARCHAR2(100),
    usuario_id NUMBER REFERENCES TBPLogin(id)
);

-- Tabla nueva: registra cada creacion/modificacion de un pago (quien,
-- cuando, y que accion) -- trazabilidad real, no solo el dato final.
CREATE TABLE TBAuditoria (
    id NUMBER PRIMARY KEY,
    usuario_id NUMBER REFERENCES TBPLogin(id),
    id_pago NUMBER,
    accion VARCHAR2(20),
    detalle VARCHAR2(200),
    fecha_hora TIMESTAMP DEFAULT SYSTIMESTAMP
);

CREATE SEQUENCE tbpago_sequence START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE tblogin_sequence START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE tbauditoria_sequence START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- Compara el hash de la contrasena recibida contra el hash guardado, y
-- ahora tambien devuelve el id del usuario (antes solo el nombre) --
-- hace falta ese id para filtrar sus pagos y para la auditoria.
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
/

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
/

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
/

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
/

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