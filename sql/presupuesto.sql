-- presupuesto.sql
-- Migracion ADITIVA (no borra ni cambia datos existentes): presupuesto
-- mensual por categoria (cuanto se piensa recibir o gastar en cada categoria
-- cada mes del año). La ventana Presupuesto lo compara con los registros
-- reales del mismo mes; los grupos son los del plan 50/30/20.
--
-- Se aplica con web/migrar.py presupuesto --aplicar, con el usuario DUEÑO del
-- esquema. Despues hay que dar al usuario de la aplicacion:
--   GRANT SELECT, INSERT, UPDATE, DELETE ON TBPresupuestoMensual TO <usuario_app>;

-- Una fila por usuario, categoria y mes (mes = dia 1 del mes). Sin fila = 0.
CREATE TABLE TBPresupuestoMensual (
    usuario_id NUMBER NOT NULL REFERENCES TBPLogin(id),
    categoria_id NUMBER NOT NULL REFERENCES TBCategorias(id),
    mes DATE NOT NULL,
    monto NUMBER(14, 2) NOT NULL,
    CONSTRAINT pk_presupuesto_mensual PRIMARY KEY (usuario_id, categoria_id, mes),
    CONSTRAINT ck_presupuesto_monto CHECK (monto > 0),
    CONSTRAINT ck_presupuesto_mes CHECK (mes = TRUNC(mes, 'MM'))
)
/

-- Categorias sugeridas nuevas, para cubrir las lineas tipicas de un
-- presupuesto anual (primas, ingresos pasivos, reparaciones del hogar...).
INSERT ALL
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (17, NULL, 'Primas y bonificaciones', 'INGRESO', NULL)
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (18, NULL, 'Ingresos pasivos', 'INGRESO', NULL)
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (19, NULL, 'Hogar y reparaciones', 'GASTO', 'NECESIDADES')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (20, NULL, 'Tarjetas y créditos', 'GASTO', 'AHORRO')
    INTO TBCategorias (id, usuario_id, nombre, tipo, grupo) VALUES (21, NULL, 'Gastos varios', 'GASTO', 'DESEOS')
SELECT 1 FROM dual
/
