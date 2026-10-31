-- seguridad.sql
-- Migracion ADITIVA para una base que ya tiene datos: no borra ni cambia
-- ningun dato existente. Agrega lo necesario para:
--   * contrasenas con PBKDF2 (el valor guardado ahora mide ~90 caracteres),
--   * verificacion en dos pasos (TOTP) obligatoria,
--   * bloqueo temporal tras varios intentos fallidos,
--   * auditoria de los eventos de acceso.
--
-- Se puede correr con el script web/migrar_seguridad.py (usa el .env de
-- web/), o pegando cada bloque en SQL Developer / Database Actions. Cada
-- sentencia termina en una linea con "/" sola. Correrla dos veces no rompe
-- nada: el script ignora "la columna ya existe".
--
-- validar_login (el procedimiento de antes) NO se toca: con PBKDF2 la
-- contrasena ya no se puede comparar dentro del SQL (cada hash tiene su
-- propia sal), asi que las dos aplicaciones leen el hash con
-- obtener_credenciales y lo verifican en el cliente.

ALTER TABLE TBPLogin MODIFY (password VARCHAR2(255))
/

ALTER TABLE TBPLogin ADD (
    -- Secreto TOTP cifrado con AES-256-GCM ("v1:..."); la clave vive fuera
    -- de la base (TOTP_CLAVE en la web, totp.clave en escritorio).
    totp_secreto VARCHAR2(255),
    -- 1 cuando el usuario ya confirmo su autenticador con un codigo valido.
    totp_activo NUMBER(1) DEFAULT 0 NOT NULL,
    -- Ultimo paso de 30 s aceptado: un codigo ya usado no vale de nuevo.
    totp_ultimo_paso NUMBER DEFAULT 0 NOT NULL,
    intentos_fallidos NUMBER DEFAULT 0 NOT NULL,
    bloqueo_hasta TIMESTAMP
)
/

-- Datos de acceso de una cuenta ACTIVA, por correo (p_usuario) o por id
-- (p_id_buscado, cuando p_usuario es NULL). p_bloqueada = 1 mientras dure
-- el bloqueo por intentos fallidos (se calcula aqui con la hora de la base,
-- no la del cliente).
CREATE OR REPLACE PROCEDURE obtener_credenciales(
    p_usuario IN VARCHAR2,
    p_id_buscado IN NUMBER,
    p_usuario_id OUT NUMBER,
    p_correo OUT VARCHAR2,
    p_nombre OUT VARCHAR2,
    p_password OUT VARCHAR2,
    p_totp_secreto OUT VARCHAR2,
    p_totp_activo OUT NUMBER,
    p_totp_ultimo_paso OUT NUMBER,
    p_bloqueada OUT NUMBER
)
IS
BEGIN
    SELECT id, usuario, nombre, password, totp_secreto, totp_activo, totp_ultimo_paso,
           CASE WHEN bloqueo_hasta > SYSTIMESTAMP THEN 1 ELSE 0 END
    INTO p_usuario_id, p_correo, p_nombre, p_password, p_totp_secreto, p_totp_activo,
         p_totp_ultimo_paso, p_bloqueada
    FROM TBPLogin
    WHERE activo = 1
      AND ((p_usuario IS NOT NULL AND usuario = p_usuario)
           OR (p_usuario IS NULL AND id = p_id_buscado));
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_usuario_id := NULL;
        p_correo := NULL;
        p_nombre := NULL;
        p_password := NULL;
        p_totp_secreto := NULL;
        p_totp_activo := NULL;
        p_totp_ultimo_paso := NULL;
        p_bloqueada := NULL;
END obtener_credenciales;
/

-- Un intento fallido (contrasena, codigo TOTP o datos de recuperacion). Al
-- quinto seguido la cuenta queda bloqueada 15 minutos (mismo criterio en todo el portafolio).
CREATE OR REPLACE PROCEDURE registrar_fallo_acceso(
    p_usuario_id IN NUMBER,
    p_motivo IN VARCHAR2
)
IS
BEGIN
    UPDATE TBPLogin
    SET intentos_fallidos = intentos_fallidos + 1
    WHERE id = p_usuario_id;

    UPDATE TBPLogin
    SET bloqueo_hasta = SYSTIMESTAMP + INTERVAL '15' MINUTE,
        intentos_fallidos = 0
    WHERE id = p_usuario_id
      AND intentos_fallidos >= 5;

    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, NULL, p_motivo,
            'Intento de acceso fallido');

    COMMIT;
END registrar_fallo_acceso;
/

-- Ingreso completo (contrasena + codigo TOTP). Solo acepta el codigo si su
-- paso es posterior al ultimo usado: si dos peticiones llegan con el mismo
-- codigo, solo una gana (p_aceptado = 1).
CREATE OR REPLACE PROCEDURE registrar_acceso_exitoso(
    p_usuario_id IN NUMBER,
    p_paso IN NUMBER,
    p_motivo IN VARCHAR2,
    p_aceptado OUT NUMBER
)
IS
BEGIN
    UPDATE TBPLogin
    SET totp_ultimo_paso = p_paso,
        intentos_fallidos = 0,
        bloqueo_hasta = NULL
    WHERE id = p_usuario_id
      AND totp_ultimo_paso < p_paso;

    p_aceptado := SQL%ROWCOUNT;

    IF p_aceptado > 0 THEN
        INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
        VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, NULL, p_motivo,
                'Acceso verificado con codigo TOTP');
    END IF;

    COMMIT;
END registrar_acceso_exitoso;
/

-- Activa (o reemplaza) el autenticador de la cuenta, ya confirmado con un
-- codigo valido del secreto nuevo.
CREATE OR REPLACE PROCEDURE guardar_totp(
    p_usuario_id IN NUMBER,
    p_secreto_cifrado IN VARCHAR2,
    p_paso IN NUMBER
)
IS
BEGIN
    UPDATE TBPLogin
    SET totp_secreto = p_secreto_cifrado,
        totp_activo = 1,
        totp_ultimo_paso = p_paso,
        intentos_fallidos = 0,
        bloqueo_hasta = NULL
    WHERE id = p_usuario_id;

    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, NULL, 'TOTP_ACTIVADO',
            'Autenticador configurado');

    COMMIT;
END guardar_totp;
/

-- Cambio del hash de la contrasena: cambio voluntario (CAMBIO_CLAVE),
-- recuperacion (RECUPERAR_CLAVE) o migracion automatica de SHA-256 a PBKDF2
-- al ingresar (MIGRAR_HASH).
CREATE OR REPLACE PROCEDURE cambiar_password(
    p_usuario_id IN NUMBER,
    p_hash IN VARCHAR2,
    p_motivo IN VARCHAR2
)
IS
BEGIN
    UPDATE TBPLogin
    SET password = p_hash
    WHERE id = p_usuario_id;

    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, p_usuario_id, NULL, p_motivo,
            'Contrasena actualizada');

    COMMIT;
END cambiar_password;
/
