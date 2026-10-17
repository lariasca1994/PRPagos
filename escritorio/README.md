# PRPagos Escritorio

<p>
  <a href="https://prpagos-web-1087929107584.southamerica-east1.run.app"><img src="../docs/demo-badge.svg" alt="Abrir la demo en vivo" height="32"></a>
  <a href="https://frontend-nine-topaz-99.vercel.app"><img src="https://portafolio-status.onrender.com/api/status/prpagos/badge.svg" alt="Estado en vivo del proyecto" height="32"></a>
  <a href="https://d4i3vsgw7xwmh.cloudfront.net"><img src="https://portafolio-status.onrender.com/api/status/prpagos/qa-badge.svg" alt="Fecha y resultado de la última prueba E2E" height="32"></a>
</p>

![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![JDBC](https://img.shields.io/badge/JDBC-5382A1?style=for-the-badge&logo=openjdk&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white)

## Contenido

1. [Presentación](#1-presentación)
2. [Estructura del proyecto](#2-estructura-del-proyecto)
3. [Arquitectura](#3-arquitectura)
4. [Plataformas y su función](#4-plataformas-y-su-función)
5. [Cómo usar la plataforma](#5-cómo-usar-la-plataforma)
6. [Instalación para pruebas](#6-instalación-para-pruebas)
7. [Autor y licencia](#7-autor-y-licencia)

---

## 1. Presentación

Versión de escritorio de PRPagos, construida con Java Swing. Es la aplicación
original, y habla con la **misma** base de datos, el **mismo** Wallet de
Oracle y los **mismos** procedimientos almacenados que la versión web
([`../web`](../web)).

Ver el [`README.md`](../README.md) de la raíz para la descripción general del
proyecto y [`CHANGELOG-migracion.md`](../CHANGELOG-migracion.md) para el
detalle de la migración.

### En pocas palabras

- **Qué hace:** permite registrar, consultar, modificar y exportar pagos desde
  una aplicación de escritorio. Cada persona ve solo sus propios pagos.
- **Qué lo hace interesante:** el código Java no reimplementa las reglas: la
  autorización y la auditoría viven en procedimientos almacenados de Oracle,
  los mismos que usa la versión web.
- **Cómo probarlo:** se ejecuta en tu equipo; ve a
  [Instalación para pruebas](#6-instalación-para-pruebas). Para probar sin
  instalar nada, usa la [demo web](https://prpagos-web-1087929107584.southamerica-east1.run.app),
  que comparte la misma base.

### Demo en vivo

La versión de escritorio no tiene demo en línea: se ejecuta en el equipo de
cada usuario.

**Versión web (misma base de datos):** [abrir la demo en vivo](https://prpagos-web-1087929107584.southamerica-east1.run.app)

### Funcionalidades

**Cuentas y seguridad**
- Registro con cualquier correo real (ahí llegan las notificaciones)
- Ingreso en dos pasos: contraseña y código de una app autenticadora (TOTP),
  obligatorio para todas las cuentas
- El autenticador se configura una sola vez y sirve también en la web
- Pantalla **Seguridad**: cambiar la contraseña o el autenticador
- Recuperación de contraseña con el código del autenticador
- Bloqueo de 5 minutos tras 5 intentos fallidos seguidos
- Sesión que se cierra tras 6 horas sin uso (máximo 8 horas), con un mensaje del motivo

**Interfaz**
- Misma estructura, menú y colores que la versión web, con tema claro y oscuro

**Pagos**
- Ingresar, consultar y modificar pagos
- Filtros por concepto, monto y rango de fechas
- Selector de fechas mediante calendario
- Exportar la consulta a CSV
- Cada usuario ve y modifica únicamente sus propios pagos

**Auditoría**
- Cada operación queda registrada por la base de datos

### Stack

| Capa | Tecnología |
|---|---|
| Interfaz | Java Swing con FlatLaf (tema claro y oscuro) |
| Lenguaje | Java 17 |
| Acceso a datos | JDBC (ojdbc17 + oraclepki) |
| Base de datos | Oracle Autonomous Database |
| Reglas de negocio | Procedimientos almacenados PL/SQL |
| Contraseñas | PBKDF2-SHA256 (600.000 iteraciones, sal única) |
| Verificación en dos pasos | TOTP (RFC 6238); QR con ZXing; secreto cifrado con AES-256-GCM |
| Sesión | En memoria; 6 h sin uso, máximo 8 h (igual que la web) |
| Despliegue | Equipo local |

---

## 2. Estructura del proyecto

```
escritorio/
├── config.properties.example   Plantilla de configuración
├── lib/                         Drivers de Oracle, FlatLaf y ZXing
├── resources/                   Ícono de la aplicación (el mismo de la web)
└── src/main/java/com/prpagos/
    ├── conexion/    ConexionBD — punto único de conexión a la base (Wallet)
    ├── datos/       Repositorios de usuarios y pagos (procedimientos almacenados)
    ├── seguridad/   PBKDF2, TOTP y cifrado AES-GCM (gemelos de la web)
    └── ui/          Portada de acceso, ventana principal con su menú,
                     paneles (Consulta, Pago, Seguridad), Tema y componentes
```

Las tablas y procedimientos (`../sql/`) y el Wallet (`../wallet/`) están en
la raíz del repositorio, compartidos con la versión web.

---

## 3. Arquitectura

<p align="center">
  <img src="../docs/arquitectura-escritorio.svg" alt="Diagrama de arquitectura: las ventanas Swing de PRPagos Escritorio usan Seguridad (PBKDF2, TOTP y AES-GCM), exportan pagos a CSV y, mediante ConexionBD y config.properties, se conectan por JDBC con TLS y Wallet a los procedimientos almacenados de Oracle Autonomous Database" width="100%">
</p>

- La aplicación corre en el **equipo del usuario**; la ventana de inicio es
  `com.prpagos.ui.VentanaLogin`.
- **Seguridad** verifica la contraseña (PBKDF2) y el código del autenticador
  (TOTP) con las mismas reglas que la web.
- **ConexionBD** es el punto único de conexión: lee `config.properties` y se
  conecta por JDBC con TLS usando el **Wallet** (SSO, sin contraseña).
- Los **procedimientos almacenados** de Oracle deciden qué pagos ve cada
  usuario, verifican la autorización y registran la auditoría.
- La consulta de pagos se puede exportar a un **archivo CSV** local.

### Seguridad

- **Una sola seguridad para las dos interfaces:** la contraseña, el
  autenticador, el último código usado y el bloqueo por intentos fallidos
  viven en la misma fila de la base. Un autenticador configurado en la web
  sirve aquí, y al revés.
- Contraseñas con PBKDF2-SHA256 (600.000 iteraciones y sal única). Las cuentas
  creadas con el SHA-256 anterior se actualizan solas en su siguiente ingreso.
- Verificación en dos pasos obligatoria (TOTP); un código no se puede usar dos
  veces. El secreto se guarda cifrado con AES-256-GCM y la clave
  (`totp.clave`) vive fuera de la base.
- Cinco intentos fallidos seguidos bloquean la cuenta 5 minutos; lo decide la
  base, igual que para la web.
- Un usuario solo puede ver y modificar sus propios pagos; esa regla vive en
  los procedimientos almacenados (`buscar_tbpagos`, `actualizar_tbpago`), no
  se reimplementa en el código Java.
- Conexión a la base por TLS con Wallet.
- El Wallet y `config.properties` (con la clave de cifrado) no se suben al
  repositorio.

---

## 4. Plataformas y su función

| Plataforma | Función en el proyecto |
|---|---|
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | La aplicación corre en el equipo de cada usuario, sin servidor. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Oracle Autonomous Database guarda usuarios y pagos, y ejecuta los procedimientos almacenados con las reglas y la auditoría. Conexión por TLS con el Wallet; no requiere ningún otro servicio, cuenta ni clave de API. |
| ![JDBC](https://img.shields.io/badge/JDBC-5382A1?style=for-the-badge&logo=openjdk&logoColor=white) | Conecta la aplicación con la base mediante los drivers `ojdbc17` y `oraclepki`. |
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Aloja la versión web; su azul y el rojo de Oracle forman la paleta común de las dos interfaces. |

---

## 5. Cómo usar la plataforma

La ventana tiene la misma barra que la web: **Consultar registros**, **Nuevo
registro**, **Seguridad** y **Salir**, y el botón ◐ para cambiar entre tema claro
y oscuro.

### 5.1 Crear una cuenta

1. En la portada, pulsa **Regístrate**.
2. Completa **Correo**, **Nombre completo**, **Contraseña** (mínimo 8 caracteres) y
   **Repite la contraseña**, y pulsa **Crear cuenta**. La misma cuenta sirve en la
   versión web.

### 5.2 Iniciar sesión (dos pasos)

1. Escribe **Correo** y **Contraseña** y pulsa **Continuar**.
2. **Primera vez:** escanea el QR con una app autenticadora (Google Authenticator,
   Microsoft Authenticator, Authy…) o escribe la clave a mano, y escribe el código
   de 6 dígitos en **Activar e ingresar**. Si ya lo configuraste en la web, este paso
   no aparece.
3. **Siguientes veces:** escribe el código de 6 dígitos de tu app y pulsa **Ingresar**.
4. La sesión se cierra tras 6 horas sin uso o a las 8 horas del ingreso, con un mensaje que explica el motivo.

### 5.3 Registrar un pago

1. Pulsa **Nuevo registro**.
2. Completa **Monto**, **Fecha** (con el calendario) y **Concepto**, y pulsa **Guardar**.

### 5.4 Consultar, modificar y exportar

1. Pulsa **Consultar registros**.
2. Filtra con **Concepto contiene**, **Monto mínimo**, **Monto máximo**, **Desde** y
   **Hasta** (las fechas pueden quedar vacías), y pulsa **Buscar** (**Limpiar** quita
   los filtros).
3. La tabla muestra tus pagos y el **Total**. **Modificar** (o doble clic en la fila)
   abre el pago; confirma con **Guardar cambios**.
4. **Exportar a CSV** guarda la consulta en un archivo local.

### 5.5 Seguridad

- **Cambiar contraseña:** contraseña actual, la nueva dos veces y un código del
  autenticador.
- **Cambiar autenticador:** escribe un código del actual, escanea el QR nuevo y
  confírmalo con su primer código.

### 5.6 Recuperar la contraseña

1. En la portada, pulsa **Olvidé mi contraseña**.
2. Escribe tu **Correo**, el **Nombre registrado**, el **Código del autenticador** y
   la nueva contraseña dos veces, y pulsa **Actualizar contraseña**.

---

## 6. Instalación para pruebas

### Requisitos

- JDK 17 o superior
- Una base Oracle accesible, preparada con `../sql/DBPagos.sql` (una sola vez)
- El Wallet de esa base descomprimido en `../wallet`. No viene en el
  repositorio: se descarga desde la consola de Oracle Cloud.
- Las librerías de `lib/` (ya incluidas en el repositorio): drivers de Oracle,
  FlatLaf (aspecto de la interfaz) y ZXing (código QR)

### Instalación

```bash
git clone https://github.com/lariasca1994/PRPagos.git
cd PRPagos/escritorio

cp config.properties.example config.properties
```

### Configuración

La conexión se centraliza en `conexion/ConexionBD`. Sus parámetros se leen de
`config.properties`, no están escritos en el código: el alias de conexión
(de `wallet/tnsnames.ora`), el usuario y la contraseña de la base, y
`totp.clave`, la clave con la que se cifran los autenticadores. Debe ser **la
misma** `TOTP_CLAVE` de la versión web; si no, ninguna de las dos podrá leer
los autenticadores configurados en la otra.

Como el Wallet está en la raíz del repositorio y no dentro de esta carpeta,
`wallet.path` debe apuntar a `../wallet`, que es el valor que ya trae la
plantilla. `config.properties` nunca se sube a git.

### Ejecución

Desde un IDE (Eclipse o VS Code): importar el proyecto, agregar todos los
`.jar` de `lib/` como Referenced Libraries / al classpath, y ejecutar
`com.prpagos.ui.VentanaLogin` como aplicación Java.

Desde consola (PowerShell, parado en `escritorio/`):

```powershell
javac --release 17 -encoding UTF-8 -d bin -cp "lib\*" (Get-ChildItem -Recurse -Path src -Filter *.java | % FullName)
java --enable-native-access=ALL-UNNAMED -cp "bin;lib\*" com.prpagos.ui.VentanaLogin
```

En Linux o macOS el separador del classpath es `:` en lugar de `;`.

### Despliegue

La versión de escritorio se ejecuta localmente, con Oracle Autonomous
Database como base de datos. No requiere servidor.

---

## 7. Autor y licencia

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)

Licencia: MIT.
