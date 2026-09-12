# PRPagos — versión de escritorio

Aplicación de escritorio en Java para la gestión de pagos, con interfaz
Swing y persistencia en Oracle.

Ver el `README.md` de la raíz del repositorio para una descripción general
del proyecto y su versión web (`../web`).

## Funcionalidades

- Registro e inicio de sesión de usuarios
- Registro, consulta y actualización de pagos
- Cada usuario ve únicamente sus propios pagos
- Selector de fechas mediante calendario
- Registro de auditoría de las operaciones

## Estructura

```
src/main/java/com/prpagos/
├── conexion/    ConexionBD — punto único de conexión a la base (Wallet)
├── seguridad/   Seguridad — hash de contraseñas (SHA-256)
└── ui/          Ventanas Swing (Login, Principal, ConsultaPagos,
                 IngresoPago, ModificarPago, CrearUsuario,
                 RecuperarContrasena, SelectorFecha, Iconos)
```

La clase principal (con el `main()` que arranca la aplicación) es
`com.prpagos.ui.VentanaLogin`.

Las tablas, secuencias y procedimientos almacenados (`../sql/DBPagos.sql`)
y el Wallet de conexión (`../wallet/`) están en la raíz del repositorio,
compartidos con la versión web.

## Requisitos

- JDK 17 o superior
- Una base de datos Oracle accesible (Oracle Autonomous Database tiene un
  plan gratuito permanente)
- Drivers en `lib/`: `ojdbc17.jar` (driver JDBC) y `oraclepki.jar`
  (necesario para el login automático del Wallet)

## Configuración de la conexión

La conexión se centraliza en `conexion/ConexionBD`. Sus parámetros se leen
de `config.properties` (copiar `config.properties.example`), no están
escritos en el código.

Como el Wallet quedó en la raíz del repositorio (`../wallet`) y no dentro
de esta carpeta, `wallet.path` en `config.properties` debe apuntar a
`../wallet`, no a `./wallet`.

## Preparación de la base

Ejecutar `../sql/DBPagos.sql` en la base de datos. Crea las tablas, las
secuencias, los procedimientos almacenados y la tabla de auditoría — solo
hace falta hacerlo una vez, sin importar qué versión se vaya a usar.

## Ejecución

Desde un IDE (Eclipse/VS Code): importar el proyecto, agregar `lib/ojdbc17.jar`
y `lib/oraclepki.jar` como Referenced Libraries / al classpath, y ejecutar
`com.prpagos.ui.VentanaLogin` como aplicación Java.

Desde consola (PowerShell, parado en esta carpeta):

```powershell
javac -d bin -cp "lib\ojdbc17.jar;lib\oraclepki.jar" (Get-ChildItem -Recurse -Path src -Filter *.java | % FullName)
java -cp "bin;lib\ojdbc17.jar;lib\oraclepki.jar" com.prpagos.ui.VentanaLogin
```

En Linux/Mac el separador del classpath es `:` en lugar de `;`.

## Seguridad

Las contraseñas se almacenan cifradas (SHA-256), nunca en texto plano. La
visibilidad de los pagos está limitada por usuario, y el procedimiento de
actualización verifica la autorización antes de modificar un registro.
