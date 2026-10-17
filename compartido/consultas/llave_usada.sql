-- Ingreso válido con una llave: guarda el contador de firmas, reinicia los
-- intentos fallidos (como un ingreso con código) y deja la auditoría.
BEGIN
    UPDATE TBLlavesAcceso SET contador = :contador, ultimo_uso = SYSTIMESTAMP WHERE id = :id;
    UPDATE TBPLogin SET intentos_fallidos = 0, bloqueo_hasta = NULL WHERE id = :usuario_id;
    INSERT INTO TBAuditoria (id, usuario_id, id_pago, accion, detalle)
    VALUES (tbauditoria_sequence.NEXTVAL, :usuario_id, NULL, 'INGRESO_LLAVE', 'Ingreso con llave de acceso');
    COMMIT;
END;
