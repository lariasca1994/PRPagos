-- Categorías disponibles para un usuario: las sugeridas y las propias.
-- propia = 1 si la creó el usuario (se puede eliminar si no tiene registros).
SELECT id, nombre, tipo, grupo, CASE WHEN usuario_id IS NULL THEN 0 ELSE 1 END AS propia,
       (SELECT COUNT(*) FROM TBPagos p WHERE p.categoria_id = c.id) AS usos
FROM TBCategorias c
WHERE usuario_id IS NULL OR usuario_id = :usuario_id
ORDER BY tipo DESC, CASE grupo WHEN 'NECESIDADES' THEN 1 WHEN 'DESEOS' THEN 2 WHEN 'AHORRO' THEN 3 ELSE 0 END,
         propia, nombre
