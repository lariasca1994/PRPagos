-- finanzas.sql
-- Migracion ADITIVA (no borra ni cambia datos existentes) para convertir
-- PRPagos en un control de ingresos y gastos con plan 50/30/20, y para la
-- recuperacion de acceso cuando se pierde el telefono del autenticador.
--
-- Se aplica con web/migrar.py finanzas --aplicar (o pegando cada bloque en
-- SQL Developer). Cada sentencia termina en una linea con "/" sola; se puede
-- correr mas de una vez (el script salta lo que ya existe).
--
-- Los registros existentes quedan como GASTO sin categoria.
--
-- Los calculos del dashboard y de los resumenes por correo NO estan aqui:
-- son las consultas de compartido/consultas/*.sql, que leen tal cual la web
-- y el escritorio (una sola fuente, los mismos numeros en ambas).

-- ── Categorias ───────────────────────────────────────────────────────
-- usuario_id NULL = categoria sugerida para todos; si no, es propia del
-- usuario. Los gastos llevan su grupo del plan 50/30/20; los ingresos no.
CREATE TABLE TBCategorias (
    id NUMBER PRIMARY KEY,
    usuario_id NUMBER REFERENCES TBPLogin(id),
    nombre VARCHAR2(60) NOT NULL,
    tipo VARCHAR2(10) NOT NULL,
    grupo VARCHAR2(12),
    CONSTRAINT ck_categoria_tipo CHECK (tipo IN ('INGRESO', 'GASTO')),
    CONSTRAINT ck_categoria_grupo CHECK (
        (tipo = 'GASTO' AND grupo IN ('NECESIDADES', 'DESEOS', 'AHORRO'))
        OR (tipo = 'INGRESO' AND grupo IS NULL))
)
/

CREATE SEQUENCE tbcategoria_sequence START WITH 100 INCREMENT BY 1 NOCACHE NOCYCLE
/

INSERT ALL
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (1, NULL, 'Salario', 'INGRESO', NULL)
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (2, NULL, 'Ingresos extra', 'INGRESO', NULL)
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (3, NULL, 'Otros ingresos', 'INGRESO', NULL)
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (4, NULL, 'Vivienda', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (5, NULL, 'Servicios', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (6, NULL, 'Mercado', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (7, NULL, 'Transporte', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (8, NULL, 'Salud', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (9, NULL, 'Educación', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (10, NULL, 'Restaurantes', 'GASTO', 'DESEOS')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (11, NULL, 'Ocio', 'GASTO', 'DESEOS')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (12, NULL, 'Compras', 'GASTO', 'DESEOS')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (13, NULL, 'Viajes', 'GASTO', 'DESEOS')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (14, NULL, 'Ahorro', 'GASTO', 'AHORRO')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (15, NULL, 'Inversión', 'GASTO', 'AHORRO')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (16, NULL, 'Pago de deudas', 'GASTO', 'AHORRO')
SELECT 1 FROM dual
/

-- ── Registros: tipo y categoria ──────────────────────────────────────
ALTER TABLE TBPagos ADD (
    tipo VARCHAR2(10) DEFAULT 'GASTO' NOT NULL,
    categoria_id NUMBER REFERENCES TBCategorias(id)
)
/

ALTER TABLE TBPagos ADD CONSTRAINT ck_pagos_tipo CHECK (tipo IN ('INGRESO', 'GASTO'))
/

-- ── Plan 50/30/20 por usuario (sin fila = 50/30/20) ──────────────────
CREATE TABLE TBPresupuestos (
    usuario_id NUMBER PRIMARY KEY REFERENCES TBPLogin(id),
    pct_necesidades NUMBER(5, 2) DEFAULT 50 NOT NULL,
    pct_deseos NUMBER(5, 2) DEFAULT 30 NOT NULL,
    pct_ahorro NUMBER(5, 2) DEFAULT 20 NOT NULL,
    CONSTRAINT ck_presupuesto_suma CHECK (pct_necesidades + pct_deseos + pct_ahorro = 100),
    CONSTRAINT ck_presupuesto_rango CHECK (
        pct_necesidades BETWEEN 0 AND 100 AND pct_deseos BETWEEN 0 AND 100 AND pct_ahorro BETWEEN 0 AND 100)
)
/

-- ── Recuperacion de acceso y correos ─────────────────────────────────
-- Codigos de respaldo de un solo uso (se guarda solo su SHA-256).
CREATE TABLE TBCodigosRespaldo (
    id NUMBER PRIMARY KEY,
    usuario_id NUMBER NOT NULL REFERENCES TBPLogin(id),
    codigo_hash VARCHAR2(64) NOT NULL,
    usado_en TIMESTAMP
)
/

CREATE SEQUENCE tbrespaldo_sequence START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE
/

-- recup_hash / recup_expira: codigo de 6 digitos enviado al correo cuando se
-- pierde el telefono (15 minutos). notificaciones: 1 = recibe los resumenes.
ALTER TABLE TBPLogin ADD (
    recup_hash VARCHAR2(64),
    recup_expira TIMESTAMP,
    notificaciones NUMBER(1) DEFAULT 1 NOT NULL
)
/

-- Un resumen programado por usuario y periodo: si la tarea se repite, no se
-- envia dos veces.
CREATE TABLE TBEnviosResumen (
    usuario_id NUMBER NOT NULL REFERENCES TBPLogin(id),
    tipo VARCHAR2(10) NOT NULL,
    periodo VARCHAR2(20) NOT NULL,
    enviado_en TIMESTAMP DEFAULT SYSTIMESTAMP,
    CONSTRAINT pk_envios_resumen PRIMARY KEY (usuario_id, tipo, periodo)
)
/

-- ── Procedimientos de registros (con tipo y categoria) ───────────────
-- insertar_tbpago y actualizar_tbpago (los de antes) siguen existiendo.
CREATE OR REPLACE PROCEDURE insertar_registro(
    p_tipo IN VARCHAR2,
    p_monto IN NUMBER,
    p_fecha IN DATE,
    p_concepto IN VARCHAR2,
    p_categoria_id IN NUMBER,
    p_usuario_id IN NUMBER
) AS
    v_id_pago NUMBER;
    v_valida NUMBER;
BEGIN
    IF p_categoria_id IS NOT NULL THEN
        SELECT COUNT(*) INTO v_valida FROM TBCategorias
        WHERE id = p_categoria_id AND tipo = p_tipo
          AND (usuario_id IS NULL OR usuario_id = p_usuario_id);
        IF v_valida = 0 THEN
            RAISE_APPLICATION_ERROR(-20010, 'Categoría no válida para este registro');
        END IF;
    END IF;

    v_id_pago := tbpago_sequence.NEXTVAL;
    INSERT INTO TBPagos (id_pago, monto, fecha, concepto, usuario_id, tipo, categoria_id)
    VALUES (v_id_pago, p_monto, p_fecha, p_concepto, p_usuario_id, p_tipo, p_categoria_id);

    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, v_id_pago, 'INSERTAR',
            p_tipo || ' ' || p_monto || ', Concepto: ' || SUBSTR(p_concepto, 1, 120));
    COMMIT;
END insertar_registro;
/

CREATE OR REPLACE PROCEDURE actualizar_registro(
    p_id_pago IN NUMBER,
    p_tipo IN VARCHAR2,
    p_monto IN NUMBER,
    p_fecha IN DATE,
    p_concepto IN VARCHAR2,
    p_categoria_id IN NUMBER,
    p_usuario_id IN NUMBER,
    p_filas_afectadas OUT NUMBER
) AS
    v_valida NUMBER;
BEGIN
    IF p_categoria_id IS NOT NULL THEN
        SELECT COUNT(*) INTO v_valida FROM TBCategorias
        WHERE id = p_categoria_id AND tipo = p_tipo
          AND (usuario_id IS NULL OR usuario_id = p_usuario_id);
        IF v_valida = 0 THEN
            RAISE_APPLICATION_ERROR(-20010, 'Categoría no válida para este registro');
        END IF;
    END IF;

    UPDATE TBPagos
    SET tipo = p_tipo, monto = p_monto, fecha = p_fecha, concepto = p_concepto,
        categoria_id = p_categoria_id
    WHERE id_pago = p_id_pago AND usuario_id = p_usuario_id;
    p_filas_afectadas := SQL%ROWCOUNT;

    IF p_filas_afectadas > 0 THEN
        INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
        VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, p_id_pago, 'ACTUALIZAR',
                p_tipo || ' ' || p_monto || ', Concepto: ' || SUBSTR(p_concepto, 1, 120));
    END IF;
    COMMIT;
END actualizar_registro;
/
