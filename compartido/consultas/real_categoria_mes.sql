-- Ingresos y gastos reales de un usuario en [:desde, :hasta) por categoría y
-- mes, para compararlos con el presupuesto. categoria_id NULL = sin categoría.
SELECT p.categoria_id, TRUNC(p.fecha, 'MM') AS mes, p.tipo, SUM(p.monto) AS total
FROM TBPagos p
WHERE p.usuario_id = :usuario_id
  AND p.fecha >= :desde
  AND p.fecha < :hasta
GROUP BY p.categoria_id, TRUNC(p.fecha, 'MM'), p.tipo
