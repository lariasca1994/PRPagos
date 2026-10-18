-- Registros de un usuario con filtros opcionales (NULL = sin filtro).
-- :categoria 0 = los registros sin categoría.
SELECT p.id_pago, p.tipo, p.monto, p.fecha, p.concepto, p.categoria_id,
       NVL(c.nombre, 'Sin categoría') AS categoria
FROM TBPagos p
LEFT JOIN TBCategorias c ON c.id = p.categoria_id
WHERE p.usuario_id = :usuario_id
  AND (:tipo IS NULL OR p.tipo = :tipo)
  AND (:fecha_desde IS NULL OR p.fecha >= :fecha_desde)
  AND (:fecha_hasta IS NULL OR p.fecha <= :fecha_hasta)
  AND (:concepto IS NULL OR UPPER(p.concepto) LIKE '%' || UPPER(:concepto) || '%')
  AND (:monto_min IS NULL OR p.monto >= :monto_min)
  AND (:monto_max IS NULL OR p.monto <= :monto_max)
  AND (:categoria IS NULL OR NVL(p.categoria_id, 0) = :categoria)
ORDER BY p.fecha DESC, p.id_pago DESC
