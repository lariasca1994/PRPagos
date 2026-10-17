-- Registra una llave de acceso nueva y deja la auditoría.
BEGIN
    INSERT INTO TBLlavesAcceso (id, usuario_id, credencial_id, clave_publica, contador, nombre)
    VALUES (tbllave_sequence.NEXTVAL, :usuario_id, :credencial_id, :clave_publica, :contador, :nombre);
    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, :usuario_id, NULL, 'LLAVE_NUEVA',
            SUBSTR('Llave de acceso: ' || :nombre, 1, 200));
    COMMIT;
END;
