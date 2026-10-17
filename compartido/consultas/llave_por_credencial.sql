-- Llave por su identificador (base64url), al ingresar con ella.
SELECT id, usuario_id, clave_publica, contador
FROM TBLlavesAcceso
WHERE credencial_id = :credencial_id
