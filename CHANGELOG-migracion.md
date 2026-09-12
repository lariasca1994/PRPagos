# Migración: PRPagos de escritorio a web

## Qué cambió

PRPagos era una aplicación de escritorio en Java/Swing. El repositorio ahora
se divide en dos carpetas independientes:

- **`escritorio/`** — la aplicación Java original, para quien prefiera
  seguir usándola localmente. No se modificó ninguna regla de negocio.
- **`web/`** — una aplicación nueva en FastAPI (Python) con las mismas
  pantallas y el mismo flujo, pensada para desplegarse en la nube (OCI).

`sql/`, `wallet/` y `docs/` se quedan compartidos en la raíz del repo: los
usa tanto `escritorio/` como `web/`, porque ambas versiones hablan con la
misma base de datos.

## Qué NO cambió

- El esquema de la base de datos (`sql/DBPagos.sql`): mismas tablas,
  secuencias y procedimientos almacenados.
- La conexión a Oracle Autonomous Database vía Wallet (mTLS).
- El hash de contraseñas (SHA-256 hexadecimal), para que las cuentas ya
  creadas sigan funcionando desde cualquiera de las dos interfaces.
- Las reglas de negocio: un usuario solo ve y modifica sus propios pagos,
  el registro exige un correo `@EAN.com`, la recuperación de contraseña
  exige que el nombre coincida con el registrado.

Todas las pantallas de la versión web llaman a los mismos procedimientos
almacenados que ya usaba la versión de escritorio
(`validar_login`, `insertar_tbpago`, `actualizar_tbpago`, `buscar_tbpagos`).
No se reimplementó ninguna lógica de negocio en Python: solo cambió el
cliente que la invoca.

## Qué se agregó (por ser necesario en un entorno web, no por cambio de reglas)

- Una sesión basada en JWT en una cookie `httponly` — la versión de
  escritorio no la necesitaba porque una ventana de Swing vive mientras
  el proceso vive; una request HTTP no.
- Una consulta directa de solo lectura (`obtener_pago_de_usuario`) para
  precargar el formulario de "Modificar pago": en Swing, esa ventana
  recibía los datos directamente de la fila ya seleccionada en la tabla;
  en la web, cada request es independiente y necesita traer el pago de
  la base antes de mostrar el formulario. La verificación de que el pago
  pertenece al usuario sigue haciéndola el procedimiento almacenado al
  guardar, exactamente igual que antes.

## Por qué FastAPI

Es la misma pila que ya usa el resto del portafolio (por ejemplo taskflow,
gestor-casos-qa, verificador-api): FastAPI + Jinja2 + JWT en cookie. Se
mantiene la consistencia entre proyectos en vez de introducir una pila
nueva (como Spring Boot) solo para este.

## Próximo paso

Despliegue de `web/` en Oracle Cloud Infrastructure (OCI), reutilizando el
mismo Wallet que ya usa la Autonomous Database.
