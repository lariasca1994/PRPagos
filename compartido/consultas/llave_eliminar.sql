-- Elimina una llave propia (solo la del usuario de la sesión) y deja la auditoría.
BEGIN
    DELETE FROM TBLlavesAcceso WHERE id = :id AND usuario_id = :usuario_id;
    IF SQL%ROWCOUNT = 1 THEN
        INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
        VALUES (tbauditoria_sequence.NEXTVAL, :usuario_id, NULL, 'LLAVE_ELIMINADA', 'Llave de acceso eliminada');
    END IF;
    COMMIT;
END;
