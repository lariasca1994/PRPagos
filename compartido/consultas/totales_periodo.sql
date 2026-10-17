-- Ingresos, gastos y número de movimientos de un usuario en [:desde, :hasta).
-- La usan el dashboard y los resúmenes por correo (web y escritorio).
SELECT NVL(SUM(CASE WHEN tipo = 'INGRESO' THEN monto END), 0) AS ingresos,
       NVL(SUM(CASE WHEN tipo = 'GASTO' THEN monto END), 0) AS gastos,
       COUNT(*) AS movimientos
FROM TBPagos
WHERE usuario_id = :usuario_id
  AND fecha >= :desde
  AND fecha < :hasta
