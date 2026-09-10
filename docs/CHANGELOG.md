# Changelog — correcciones sobre la versión original

## Seguridad

- **Contraseñas en texto plano**: se guardaban y se comparaban sin cifrar,
  tanto en el procedimiento `validar_login` como en creación y
  recuperación de contraseña. Corregido con SHA-256 (`Seguridad.java`) —
  documentado como una simplificación consciente frente a bcrypt/PBKDF2,
  razonable para el alcance de este proyecto.
- **Recuperación de contraseña sin verificación real**: con solo saber
  el correo de alguien, se le podía asignar una contraseña nueva sin
  ninguna validación de identidad — cualquiera podía tomar el control de
  la cuenta de otra persona. Se agregó una verificación adicional (el
  nombre exacto con el que se registró esa cuenta).
- **Credenciales de base de datos escritas directamente en 6 archivos
  distintos** (`localhost`, usuario y contraseña en texto plano). Se
  centralizó en `ConexionBD.java`, que lee la configuración desde
  `config.properties` (nunca comiteado) y usa el Wallet de Oracle Cloud
  en vez de una base local.

## Bugs reales encontrados

- El script SQL usaba `pago_sequence.NEXTVAL` en el procedimiento de
  insertar pagos (y en sus propios datos de prueba), pero la secuencia
  que en verdad se creaba se llamaba `tbpago_sequence` — nunca habría
  funcionado tal cual estaba.
- El script SQL creaba el usuario como `ConexionDB`, pero los permisos y
  la conexión posterior usaban `ConexionDBA` (con "A" al final) — nombres
  que no coincidían.
- `GestorConsultaPagos.java` era un borrador abandonado del menú
  principal (duplicado de `VentanaPrincipal.java`), con un bug propio: el
  botón "Consultar Pagos" se creaba a sí mismo en vez de abrir la ventana
  de consulta real. Se eliminó — no se migró nada de código, era código
  muerto.
- Inconsistencia: "recuperar contraseña" exigía que el usuario fuera un
  correo `@EAN.com`, pero "crear usuario" no validaba ese formato en
  absoluto. Se agregó la misma validación en la creación.

## Arquitectura

- Se organizó el código en paquetes (`conexion`, `seguridad`, `ui`) en
  vez de clases sueltas sin paquete.
- Migración de una base Oracle local a Oracle Autonomous Database
  (Always Free), usando conexión segura por Wallet.

## Control de acceso por usuario + auditoría

- **Hueco real encontrado**: `TBPagos` no tenía ninguna relación con
  `TBPLogin` — cualquier usuario autenticado veía y podía modificar los
  pagos de todos los demás. Se agregó `usuario_id` a `TBPagos`, y tanto
  la consulta como la actualización ahora filtran/verifican por ese
  campo. `actualizar_tbpago` verifica que el pago pertenezca al usuario
  que hace la petición antes de tocarlo — si no coincide, no afecta
  ninguna fila, en vez de permitirlo.
- Se agregó `TBAuditoria`: registra usuario, acción (insertar/actualizar),
  pago afectado y marca de tiempo en cada operación — trazabilidad real,
  no solo el dato final.
- `validar_login` ahora también devuelve el `id` del usuario (antes solo
  el nombre), necesario para llevar esa identidad a través de toda la
  aplicación (menú → consulta/ingreso → modificar).
