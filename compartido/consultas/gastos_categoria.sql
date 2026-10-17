-- Gastos por categoría en [:desde, :hasta), de mayor a menor (gráfico de dona
-- y tabla de los resúmenes). Los gastos sin categoría van juntos.
SELECT NVL(c.nombre, 'Sin categoría') AS categoria,
       NVL(c.grupo, 'SIN_GRUPO') AS grupo,
       SUM(p.monto) AS total
FROM TBPagos p
LEFT JOIN TBCategorias c ON c.id = p.categoria_id
WHERE p.usuario_id = :usuario_id
  AND p.tipo = 'GASTO'
  AND p.fecha >= :desde
  AND p.fecha < :hasta
GROUP BY NVL(c.nombre, 'Sin categoría'), NVL(c.grupo, 'SIN_GRUPO')
ORDER BY total DESC, categoria
