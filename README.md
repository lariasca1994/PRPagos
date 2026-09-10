# PRPagos

Aplicación de escritorio en Java para la gestión de pagos, con interfaz Swing y
persistencia en Oracle. Desarrollada durante el curso de Desarrollo de Software
de la Universidad EAN.

## Funcionalidades

- Registro e inicio de sesión de usuarios
- Registro, consulta y actualización de pagos
- Cada usuario ve únicamente sus propios pagos
- Selector de fechas mediante calendario
- Registro de auditoría de las operaciones

## Estructura

```
src/
├── vista/          Formularios Swing
├── controlador/    Lógica de la interfaz
├── modelo/         Entidades del dominio
└── util/
    └── ConexionBD  Punto único de conexión a la base
sql/
└── script.sql      Tablas, secuencias y procedimientos almacenados
```

## Requisitos

- JDK 17 o superior
- Una base de datos Oracle accesible (Oracle Autonomous Database tiene un plan
  gratuito permanente)
- Driver JDBC de Oracle (`ojdbc11`)

## Configuración de la conexión

La conexión se centraliza en `util/ConexionBD`. Sus parámetros se leen de
configuración externa, no están escritos en el código.

Si la base es Oracle Autonomous Database, se conecta por TLS mediante un Wallet.
Hay que descargarlo desde la consola de Oracle Cloud y apuntar la ruta en la
configuración.

## Preparación de la base

Ejecutar `sql/script.sql` en la base de datos. Crea las tablas, las secuencias,
los procedimientos almacenados y la tabla de auditoría.

## Ejecución

Desde un IDE: importar el proyecto, agregar `ojdbc11` al classpath y ejecutar la
clase principal.

Desde consola:

```bash
javac -d bin -cp lib/ojdbc11.jar $(find src -name "*.java")
java -cp bin:lib/ojdbc11.jar Main
```

En Windows, el separador del classpath es `;` en lugar de `:`.

## Seguridad

Las contraseñas se almacenan cifradas, nunca en texto plano. La
visibilidad de los pagos está limitada por usuario, y el procedimiento de
actualización verifica la autorización antes de modificar un registro.