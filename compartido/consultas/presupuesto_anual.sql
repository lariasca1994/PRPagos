-- Montos presupuestados de un usuario en [:desde, :hasta) por categoría y mes.
SELECT categoria_id, mes, monto
FROM TBPresupuestoMensual
WHERE usuario_id = :usuario_id
  AND mes >= :desde
  AND mes < :hasta
