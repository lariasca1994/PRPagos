-- Ingresos y gastos por mes en [:desde, :hasta), para el gráfico de barras.
-- Los meses sin movimientos los completa cada cliente con 0.
SELECT TRUNC(fecha, 'MM') AS mes,
       NVL(SUM(CASE WHEN tipo = 'INGRESO' THEN monto END), 0) AS ingresos,
       NVL(SUM(CASE WHEN tipo = 'GASTO' THEN monto END), 0) AS gastos
FROM TBPagos
WHERE usuario_id = :usuario_id
  AND fecha >= :desde
  AND fecha < :hasta
GROUP BY TRUNC(fecha, 'MM')
ORDER BY mes
