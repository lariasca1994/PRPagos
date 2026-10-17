-- Gastos por grupo del plan 50/30/20 en [:desde, :hasta).
-- grupo: NECESIDADES, DESEOS, AHORRO o SIN_GRUPO (gastos sin categoría).
SELECT NVL(c.grupo, 'SIN_GRUPO') AS grupo, SUM(p.monto) AS total
FROM TBPagos p
LEFT JOIN TBCategorias c ON c.id = p.categoria_id
WHERE p.usuario_id = :usuario_id
  AND p.tipo = 'GASTO'
  AND p.fecha >= :desde
  AND p.fecha < :hasta
GROUP BY NVL(c.grupo, 'SIN_GRUPO')
