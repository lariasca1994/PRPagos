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

Versión de escritorio de PRPagos, construida con Java Swing: el mismo gestor
de presupuesto de la web (ingresos, gastos, plan 50/30/20, dashboard y
resúmenes por correo) como aplicación de Windows. Habla con la **misma** base
de datos, el **mismo** Wallet de Oracle, los **mismos** procedimientos
almacenados y las **mismas** consultas y plantillas de correo
(`../compartido/`) que la versión web ([`../web`](../web)), así que muestra las
mismas cifras y gráficas.

Se distribuye como **PRPagos.exe** con Java incluido —instalador o versión
portable— en [GitHub Releases](https://github.com/lariasca1994/PRPagos/releases/latest),
y se **actualiza sola** cuando se publica una versión nueva.

Ver el [`README.md`](../README.md) de la raíz para la descripción general del
proyecto y [`CHANGELOG-migracion.md`](../CHANGELOG-migracion.md) para el
detalle de la migración.

### En pocas palabras

- **Qué hace:** registra ingresos y gastos por categoría, muestra el mes en un
  dashboard (JFreeChart) frente al plan 50/30/20 y envía el resumen por correo.
  Cada persona ve solo lo suyo.
- **Qué lo hace interesante:** funciona y se ve igual que la web sin duplicar
  reglas: la autorización y la auditoría viven en procedimientos de Oracle y el
  dashboard y los correos salen de archivos compartidos. Además se empaqueta con
  jpackage, se actualiza desde GitHub con verificación SHA-256 y protege su
  configuración con DPAPI.
- **Cómo probarlo:** descarga la [última versión](https://github.com/lariasca1994/PRPagos/releases/latest)
  o compílala desde el código ([Instalación para pruebas](#6-instalación-para-pruebas)).
  Para probar sin instalar nada, usa la [demo web](https://prpagos-web-1087929107584.southamerica-east1.run.app),
  que comparte la misma base.

### Demo en vivo

La versión de escritorio no tiene demo en línea: se ejecuta en el equipo de
cada usuario. **Descarga:** [última versión](https://github.com/lariasca1994/PRPagos/releases/latest).

**Versión web (misma base de datos):** [abrir la demo en vivo](https://prpagos-web-1087929107584.southamerica-east1.run.app)

### Funcionalidades

**Presupuesto**
- Ingresos y gastos con tipo, monto, fecha (con calendario), categoría y concepto
- Categorías sugeridas y propias, cada una de gasto en un grupo del plan
- Plan 50/30/20 con porcentajes editables en Perfil
- **Dashboard** por mes con los mismos gráficos de la web (JFreeChart): barras de
  6 meses, anillo de categorías y barras del plan con su estado
- Consulta con filtros (tipo, concepto, montos y fechas), totales y CSV

**Resúmenes por correo (Brevo)**
- Botones **Enviarme el resumen semanal / del mes**, con el mismo correo de la web
- Los envíos programados (viernes y día 29) los hace el servidor, aunque la
  aplicación esté cerrada

**Cuentas y seguridad**
- Ingreso en dos pasos obligatorio (TOTP), compartido con la web
- Códigos de respaldo y código por correo si se pierde el teléfono
- **Perfil** con datos, plan, categorías, autenticador, códigos y contraseña
- Bloqueo de 5 minutos tras 5 intentos fallidos seguidos
- Sesión que se cierra tras 6 horas sin uso (máximo 8 horas), con un mensaje del motivo

**Aplicación de Windows**
- **PRPagos.exe** con Java incluido: instalador por usuario (sin permisos elevados
  de Windows) o versión portable, con el ícono de la app
- **Actualización automática**: comprueba al abrir y cada hora; instala al
  reiniciar o al cerrar
- Configuración en `%APPDATA%\PRPagos\` con los secretos cifrados por **DPAPI**
- Misma portada animada, menú y colores que la web, en tema claro y oscuro

### Stack

| Capa | Tecnología |
|---|---|
| Interfaz | Java Swing con FlatLaf (tema claro y oscuro) y JFreeChart |
| Lenguaje | Java 17 (el ejecutable incluye su propio Java) |
| Acceso a datos | JDBC (ojdbc17 + oraclepki) |
| Consultas del dashboard | `../compartido/consultas/*.sql`, las mismas de la web |
| Base de datos | Oracle Autonomous Database |
| Reglas de negocio | Procedimientos almacenados PL/SQL |
| Correos | Brevo (API HTTPS, `java.net.http`) con `../compartido/correo/` |
| Contraseñas | PBKDF2-SHA256 (600.000 iteraciones, sal única) |
| Verificación en dos pasos | TOTP (RFC 6238); QR con ZXing; secreto cifrado con AES-256-GCM |
| Protección de la configuración | DPAPI de Windows (JNA) |
| Sesión | En memoria; 6 h sin uso, máximo 8 h (igual que la web) |
| Distribución | jpackage → GitHub Releases; actualización con verificación SHA-256 |

---

## 2. Estructura del proyecto

```
escritorio/
├── config.properties.example   Plantilla de configuración
├── empaquetar.ps1               Compila y arma PRPagos.exe (y el instalador) con jpackage
├── lib/                         Drivers de Oracle, FlatLaf, ZXing, JFreeChart y JNA
├── resources/                   Ícono de la aplicación (el mismo de la web)
└── src/main/
    ├── java/com/prpagos/
    │   ├── actualizaciones/ Actualizador y comprobación periódica de versiones
    │   ├── compartido/      Lee ../compartido/ (consultas SQL y plantillas de correo)
    │   ├── conexion/        ConexionBD y SecretosProtegidos (DPAPI)
    │   ├── datos/           Repositorios de usuarios, registros y finanzas
    │   ├── finanzas/        Plan 50/30/20, formatos, resúmenes y cliente de Brevo
    │   ├── seguridad/       PBKDF2, TOTP, AES-GCM y recuperación (gemelos de la web)
    │   └── ui/              Portada, ventana principal y paneles (Dashboard,
    │                        Consulta, Registro, Perfil), Tema y componentes
    └── resources/           Script que instala las actualizaciones
```

Las tablas y procedimientos (`../sql/`), las consultas y plantillas
compartidas (`../compartido/`) y el Wallet (`../wallet/`) están en la raíz del
repositorio, compartidos con la versión web. En el ejecutable, `compartido/`
va dentro del `.jar`.

---

## 3. Arquitectura

<p align="center">
  <img src="../docs/arquitectura-escritorio.svg" alt="Diagrama de arquitectura: las ventanas Swing de PRPagos Escritorio usan Seguridad y el Dashboard, y mediante ConexionBD y config.properties (protegido con DPAPI) se conectan por JDBC con TLS y Wallet a los procedimientos de Oracle Autonomous Database; los correos salen por Brevo y el Actualizador descarga las versiones nuevas de GitHub Releases" width="100%">
</p>

- La aplicación corre en el **equipo del usuario**; la ventana de inicio es
  `com.prpagos.ui.VentanaLogin`.
- **Seguridad** verifica la contraseña (PBKDF2) y el código del autenticador o
  de respaldo con las mismas reglas que la web.
- **ConexionBD** es el punto único de conexión: lee `config.properties`
  (descifrando con DPAPI lo protegido) y se conecta por JDBC con TLS usando el
  **Wallet** (SSO, sin contraseña).
- El **Dashboard** usa las consultas de `compartido/` y JFreeChart; los
  resúmenes y códigos se envían por **Brevo** con las plantillas compartidas.
- El **Actualizador** lee en Oracle la última versión publicada y su huella
  SHA-256, la descarga de **GitHub Releases** y la instala al cerrar.

### Seguridad

- **Una sola seguridad para las dos interfaces:** contraseña, autenticador,
  último código usado, códigos de respaldo y bloqueo viven en la base. Un
  autenticador configurado en la web sirve aquí, y al revés.
- Contraseñas con PBKDF2-SHA256 (600.000 iteraciones y sal única). Las cuentas
  creadas con el SHA-256 anterior se actualizan solas en su siguiente ingreso.
- Verificación en dos pasos obligatoria (TOTP); un código no se puede usar dos
  veces. El secreto se guarda cifrado con AES-256-GCM y la clave
  (`totp.clave`) vive fuera de la base.
- Cinco intentos fallidos seguidos bloquean la cuenta 5 minutos; lo decide la
  base, igual que para la web.
- Un usuario solo ve y modifica sus propios registros; esa regla vive en los
  procedimientos y en las consultas, siempre filtradas por la cuenta.
- **Configuración protegida:** al primer uso, `db.password`, `totp.clave` y
  `brevo.api.key` se cifran con DPAPI (`dpapi:…`), atados al usuario de Windows
  y al equipo; copiados a otra máquina no se pueden descifrar.
- **Actualizaciones verificadas** (mismo esquema que los agentes de SysPulse):
  solo HTTPS y solo GitHub (también tras las redirecciones), tamaño máximo,
  huella SHA-256 tomada de la base y no del sitio de descarga, y versión del
  `.jar` igual a la publicada. Si algo falla, se conserva la versión instalada.
- Conexión a la base por TLS con Wallet. El Wallet y `config.properties` no se
  suben al repositorio.

---

## 4. Plataformas y su función

| Plataforma | Función en el proyecto |
|---|---|
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | La aplicación corre en el equipo de cada usuario como PRPagos.exe, con su propio Java. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Oracle Autonomous Database guarda cuentas, registros, categorías, planes y versiones publicadas, y ejecuta los procedimientos con las reglas y la auditoría. Conexión por TLS con el Wallet. |
| ![JDBC](https://img.shields.io/badge/JDBC-5382A1?style=for-the-badge&logo=openjdk&logoColor=white) | Conecta la aplicación con la base mediante los drivers `ojdbc17` y `oraclepki`. |
| ![Brevo](https://img.shields.io/badge/Brevo-0B996E?style=for-the-badge&logo=brevo&logoColor=white) | Envía los resúmenes pedidos desde el dashboard y los códigos de recuperación. |
| ![GitHub](https://img.shields.io/badge/GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white) | GitHub Actions compila y publica cada versión (instalador y portable); de ahí se actualizan las aplicaciones instaladas. |
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Aloja la versión web y la tarea diaria de resúmenes; su azul y el rojo de Oracle forman la paleta común de las dos interfaces. |

---

## 5. Cómo usar la plataforma

El paso a paso con capturas de cada pantalla, incluida la instalación, está en
el **[Manual de escritorio](../docs/MANUAL-ESCRITORIO.md)**. En resumen:

La ventana tiene la misma barra que la web: **Dashboard**, **Consultar
registros**, **Nuevo registro**, **Perfil** y **Salir**, y el botón ◐ para
cambiar entre tema claro y oscuro.

### 5.1 Instalar

1. Descarga de [Releases](https://github.com/lariasca1994/PRPagos/releases/latest)
   el instalador `PRPagos-<versión>.exe` o el portable `PRPagos-Escritorio-<versión>.zip`.
2. Copia `config.properties` y la carpeta del Wallet en `%APPDATA%\PRPagos\`.
3. Abre **PRPagos** desde el acceso directo. Las versiones nuevas se instalan solas.

### 5.2 Crear una cuenta e ingresar

1. En la portada, pulsa **Regístrate**: correo, nombre completo y contraseña. La
   misma cuenta sirve en la web.
2. Ingresa con **Correo** y **Contraseña**; la primera vez escanea el QR con tu
   app autenticadora y guarda los 10 **códigos de respaldo**.
3. Las siguientes veces, escribe el código de 6 dígitos (o uno de respaldo).

### 5.3 Dashboard, registros y Perfil

- **Dashboard:** elige el mes; cifras, gráficas, plan 50/30/20 y botones de resumen por correo.
- **Nuevo registro:** tipo, monto, fecha, categoría y concepto.
- **Consultar registros:** filtros, totales, **Modificar** (o doble clic) y **Exportar a CSV**.
- **Perfil:** nombre y resúmenes, plan, categorías, autenticador, códigos de respaldo y contraseña.

### 5.4 Recuperar el acceso

- **Perdí mi teléfono** (paso del código): un código de respaldo, o un código
  al correo para configurar un autenticador nuevo.
- **Olvidé mi contraseña** (portada): correo, nombre registrado, un código del
  autenticador o de respaldo y la nueva contraseña dos veces.

---

## 6. Instalación para pruebas

### Requisitos

- JDK 17 o superior (para compilar; el ejecutable trae su propio Java)
- Una base Oracle accesible, preparada con `../sql/DBPagos.sql` y las
  migraciones (ver el [README de la raíz](../README.md#base-de-datos-una-sola-vez))
- El Wallet de esa base descomprimido en `../wallet`. No viene en el
  repositorio: se descarga desde la consola de Oracle Cloud.
- Las librerías de `lib/` (ya incluidas en el repositorio): drivers de Oracle,
  FlatLaf, ZXing, JFreeChart y JNA

### Instalación

```bash
git clone https://github.com/lariasca1994/PRPagos.git
cd PRPagos/escritorio

cp config.properties.example config.properties
```

### Configuración

La conexión se centraliza en `conexion/ConexionBD`. Sus parámetros se leen de
`config.properties`, no están escritos en el código: el alias de conexión
(de `wallet/tnsnames.ora`), el usuario y la contraseña de la base,
`totp.clave` (debe ser **la misma** `TOTP_CLAVE` de la web) y, para los
correos, `brevo.api.key` y `brevo.remitente` (los mismos de la web).

Dónde se busca el archivo, en orden: `-Dprpagos.config=<ruta>`, la carpeta
actual (al correr desde el código, `escritorio/`) y `%APPDATA%\PRPagos\` (la
aplicación instalada). `wallet.path` puede ser absoluto o relativo a la carpeta
del archivo; la plantilla trae `../wallet`.

En Windows, la primera vez que la aplicación lee el archivo cifra los secretos
con DPAPI; para cambiar uno, vuelve a escribirlo en claro. `config.properties`
nunca se sube a git.

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

Para armar el ejecutable (JDK 17+ con jpackage):

```powershell
.\empaquetar.ps1                  # build\dist\PRPagos\PRPagos.exe (portable)
.\empaquetar.ps1 -Instalador      # además, el instalador .exe (requiere WiX Toolset)
```

### Despliegue

Cada push a `main` que toque el escritorio o `compartido/` ejecuta
[`.github/workflows/escritorio.yml`](../.github/workflows/escritorio.yml), que:

1. Compila y empaqueta con `empaquetar.ps1` en un runner de Windows.
2. Publica el Release `escritorio-v1.0.<n>` con el instalador y el portable.
3. Registra la versión y la huella SHA-256 del portable en la base, de donde la
   leen las aplicaciones instaladas para actualizarse.

---

## 7. Autor y licencia

> 📘 **[Manual de escritorio](../docs/MANUAL-ESCRITORIO.md)**: cómo instalar y usar la aplicación campo por campo, qué datos se ingresan y qué garantiza en seguridad.

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)

Licencia: MIT.
