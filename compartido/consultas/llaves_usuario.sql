-- Llaves de acceso de un usuario, para Perfil. Las fechas salen ya en hora de
-- Colombia (UTC-5) y con el mismo formato en la web y el escritorio.
SELECT id, nombre,
       TO_CHAR(FROM_TZ(creada, 'UTC') AT TIME ZONE '-05:00', 'YYYY-MM-DD HH24:MI') AS creada,
       TO_CHAR(FROM_TZ(ultimo_uso, 'UTC') AT TIME ZONE '-05:00', 'YYYY-MM-DD HH24:MI') AS ultimo_uso,
       credencial_id
FROM TBLlavesAcceso
WHERE usuario_id = :usuario_id
ORDER BY creada
