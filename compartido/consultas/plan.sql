-- Porcentajes del plan del usuario; sin configuración propia, 50/30/20.
SELECT NVL(MAX(pct_necesidades), 50) AS pct_necesidades,
       NVL(MAX(pct_deseos), 30) AS pct_deseos,
       NVL(MAX(pct_ahorro), 20) AS pct_ahorro
FROM TBPresupuestos
WHERE usuario_id = :usuario_id
