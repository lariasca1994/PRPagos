# PRPagos

<p>
  <a href="https://prpagos-web-1087929107584.southamerica-east1.run.app"><img src="docs/demo-badge.svg" alt="Abrir la demo en vivo" height="32"></a>
  <a href="https://frontend-nine-topaz-99.vercel.app"><img src="https://portafolio-status.onrender.com/api/status/prpagos/badge.svg" alt="Estado en vivo del proyecto" height="32"></a>
  <a href="https://d4i3vsgw7xwmh.cloudfront.net"><img src="https://portafolio-status.onrender.com/api/status/prpagos/qa-badge.svg" alt="Fecha y resultado de la última prueba E2E" height="32"></a>
</p>

![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white)
![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white)

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

Aplicación para la gestión de pagos, con persistencia en Oracle Autonomous
Database. Desarrollada originalmente durante el curso de Desarrollo de
Software de la Universidad EAN.

El repositorio tiene dos versiones independientes de la misma aplicación,
que comparten la misma base de datos:

- **[`web/`](./web)** — versión web en Python (FastAPI + Jinja2),
  desplegada en Google Cloud Run. Es además una **PWA**: se instala como app
  en Android, iPhone/iPad, Windows, macOS y Linux desde el navegador.
- **[`escritorio/`](./escritorio)** — la aplicación original en Java/Swing,
  para quien prefiera correrla localmente.

Ver [`CHANGELOG-migracion.md`](./CHANGELOG-migracion.md) para el detalle de
la migración de escritorio a web.

### En pocas palabras

- **Qué hace:** permite registrar, consultar, modificar y exportar pagos. Cada
  persona ve solo sus propios pagos.
- **Qué lo hace interesante:** hay dos aplicaciones (web y escritorio) que usan
  **la misma base de datos y las mismas reglas**, porque la autorización y la
  auditoría viven en procedimientos almacenados de Oracle, no en el código de
  cada cliente.
- **Cómo probarlo:** entra a la [demo web](https://prpagos-web-1087929107584.southamerica-east1.run.app)
  y crea un usuario. Para correrlo en tu equipo, ve a
  [Instalación para pruebas](#6-instalación-para-pruebas).

### Demo en vivo

**Aplicación web:** [abrir la demo en vivo](https://prpagos-web-1087929107584.southamerica-east1.run.app)

La versión de escritorio no tiene demo en línea: se ejecuta en el equipo de
cada usuario (ver [`escritorio/README.md`](./escritorio/README.md)).

### Funcionalidades

**Cuentas y seguridad (compartida por web y escritorio)**
- Registro con cualquier correo real (ahí llegan las notificaciones)
- Ingreso en dos pasos: contraseña y código de una app autenticadora (TOTP),
  obligatorio para todas las cuentas
- Un solo autenticador por cuenta: el que se configure en la web sirve en el
  escritorio, y al revés
- Cambio de contraseña y de autenticador, y recuperación con el código
- Bloqueo de 5 minutos tras 5 intentos fallidos seguidos

**Interfaz**
- Web y escritorio con la misma estructura, menú, ícono y colores (rojo Oracle
  y azul de Google Cloud Run), en tema claro y oscuro

**Pagos**
- Registro, consulta y actualización de pagos
- Filtros por concepto, monto y rango de fechas
- Exportar pagos a CSV
- Cada usuario ve únicamente sus propios pagos

**Auditoría**
- Registro de auditoría de las operaciones, hecho por la base de datos

**App instalable (PWA)**
- La versión web se instala como app en cualquier plataforma desde el
  navegador, con ícono y ventana propios

### Stack

| Capa | Web | Escritorio |
|---|---|---|
| Interfaz | Jinja2, HTML y CSS | Java Swing con FlatLaf |
| Servidor | FastAPI, Uvicorn | — (aplicación local) |
| Acceso a datos | python-oracledb (modo thin) | JDBC (ojdbc17 + oraclepki) |
| Base de datos | Oracle Autonomous Database | Oracle Autonomous Database |
| Reglas de negocio | Procedimientos almacenados PL/SQL | Procedimientos almacenados PL/SQL |
| Sesiones | JWT en cookie httponly | Sesión en memoria de la aplicación |
| Contraseñas | PBKDF2-SHA256 | PBKDF2-SHA256 (mismo formato) |
| Verificación en dos pasos | TOTP; secreto cifrado con AES-256-GCM | TOTP; mismo secreto y misma clave |
| Despliegue | Docker en Google Cloud Run | Equipo local |
| App instalable | PWA (Android, iOS, Windows, macOS, Linux) | — |

---

## 2. Estructura del proyecto

```
web/           Aplicación web en Python (FastAPI + Jinja2)
escritorio/    Aplicación de escritorio en Java (Swing)
sql/           Tablas, procedimientos y migración de seguridad (compartido)
wallet/        Wallet de Oracle Autonomous Database (compartido, no se sube a git)
docs/          Documentación y diagramas (compartido)
Dockerfile     Imagen de la versión web para Cloud Run (se construye desde la raíz)
```

`sql/`, `wallet/` y `docs/` están en la raíz porque los usan tanto
`escritorio/` como `web/`: ambas versiones hablan con la misma base de
datos y usan el mismo Wallet para conectarse.

---

## 3. Arquitectura

<p align="center">
  <img src="docs/arquitectura.svg" alt="Diagrama de arquitectura: PRPagos Web (FastAPI) en Google Cloud Run y PRPagos Escritorio (Java Swing) en local, ambos conectados por TLS con Wallet a los procedimientos almacenados de Oracle Autonomous Database" width="100%">
</p>

- La versión web (FastAPI + Jinja2) corre en un contenedor Docker; la sesión
  viaja en un JWT dentro de una cookie.
- La **versión de escritorio** (Java Swing) corre en el equipo del usuario y se
  conecta por JDBC.
- Ambas llegan a la base con TLS y Wallet, y llaman a los mismos procedimientos
  almacenados, que verifican la autorización y registran la auditoría.

Cada versión tiene su propio diagrama detallado en
[`web/README.md`](./web/README.md#3-arquitectura) y
[`escritorio/README.md`](./escritorio/README.md#3-arquitectura).

### Seguridad

- **Una sola seguridad:** contraseña, autenticador, último código usado y
  bloqueo por intentos fallidos viven en la misma fila de la base, y las dos
  versiones aplican las mismas reglas (los procedimientos de
  `sql/seguridad.sql`).
- Contraseñas con PBKDF2-SHA256 (600.000 iteraciones y sal única); las cuentas
  con el SHA-256 anterior se actualizan solas en su siguiente ingreso.
- Verificación en dos pasos obligatoria (TOTP, RFC 6238); un código no se puede
  reutilizar. El secreto se guarda cifrado con AES-256-GCM y la clave vive
  fuera de la base.
- La visibilidad de los pagos está limitada por usuario, y el procedimiento
  de actualización verifica la autorización antes de modificar un registro.
  Esa regla vive en la base de datos, no en ninguna de las dos aplicaciones
  cliente.
- Conexión a la base por TLS con Wallet.
- El Wallet, `.env` y `config.properties` no se suben al repositorio; solo
  sus plantillas de ejemplo.

---

## 4. Plataformas y su función

| Plataforma | Función en el proyecto |
|---|---|
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Corre la versión web (FastAPI + Jinja2) en un contenedor Docker. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Oracle Autonomous Database guarda usuarios y pagos, y ejecuta los procedimientos almacenados con las reglas de negocio y la auditoría. La conexión es por TLS con el Wallet; no requiere ningún otro servicio, cuenta ni clave de API. |
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | La versión de escritorio corre en el equipo de cada usuario, sin servidor. |
| ![qa-evidencia](https://img.shields.io/badge/qa--evidencia-2EAD33?style=for-the-badge&logo=playwright&logoColor=white) | Prueba la versión web automáticamente dos veces al día y publica la evidencia. |

---

## 5. Cómo usar la plataforma

Las dos versiones comparten las mismas cuentas y los mismos datos: lo que registras en
una aparece en la otra. El paso a paso detallado está en cada carpeta:

- **Versión web:** [`web/README.md`](./web/README.md#5-cómo-usar-la-plataforma)
  (incluye cómo [instalarla como app](./web/README.md#56-instalar-la-app-pwa))
- **Versión de escritorio:** [`escritorio/README.md`](./escritorio/README.md#5-cómo-usar-la-plataforma)

Resumen del flujo, igual en ambas:

1. **Crear cuenta:** correo, nombre completo y contraseña (mínimo 8 caracteres).
2. **Iniciar sesión** con correo y contraseña, y luego el código de 6 dígitos de tu
   app autenticadora. La primera vez se configura escaneando un QR.
3. **Ingresar un pago:** monto, fecha y concepto.
4. **Consultar pagos:** filtra por concepto, monto mínimo y máximo, y rango de fechas.
5. **Modificar** un pago propio o **exportar** la consulta a CSV.
6. En **Seguridad** cambias la contraseña o el autenticador.
7. Si olvidaste la contraseña, **recupérala** con tu correo, el nombre con el que te
   registraste y el código del autenticador.

---

## 6. Instalación para pruebas

### Requisitos

- Una base Oracle accesible (por ejemplo, Oracle Autonomous Database)
- El Wallet de esa base, descargado desde la consola de Oracle Cloud
- Para la versión web: Python 3.11 o superior
- Para la versión de escritorio: JDK 17 o superior

### Base de datos (una sola vez)

Ejecutar `sql/DBPagos.sql` y después `sql/seguridad.sql` en la base crea las
tablas, las secuencias, los procedimientos almacenados (incluidos los de
acceso y verificación en dos pasos) y la tabla de auditoría. Solo hace falta
hacerlo una vez, sin importar qué versión se vaya a usar.
`web/migrar_seguridad.py --aplicar` aplica `sql/seguridad.sql` sin otras
herramientas.

Luego descomprime el Wallet en `wallet/`, en la raíz del repositorio. No viene
en el repositorio: está en `.gitignore`.

### Versión web

```bash
git clone https://github.com/lariasca1994/PRPagos.git
cd PRPagos/web

python -m venv .venv
.venv\Scripts\Activate.ps1        # Windows
source .venv/bin/activate         # Linux o macOS

pip install -r requirements.txt
cp .env.example .env
```

Completa el `.env` con los datos del Wallet y de la base, un `JWT_SECRET`
propio y la `TOTP_CLAVE` (la misma del escritorio). Detalle en [`web/README.md`](./web/README.md#configuración).

### Versión de escritorio

En `escritorio/`, copia `config.properties.example` como `config.properties`
y completa el alias, las credenciales de la base y `totp.clave` (la misma
`TOTP_CLAVE` de la web). Agrega todos los `.jar` de `lib/` al classpath. Detalle en
[`escritorio/README.md`](./escritorio/README.md#configuración).

### Ejecución

| Versión | Comando | Dónde |
|---|---|---|
| Web | `uvicorn app.main:app --reload` (desde `web/`) | `http://127.0.0.1:8000` |
| Escritorio | Ejecutar `com.prpagos.ui.VentanaLogin` | Ventana local |

Los comandos de consola para compilar y ejecutar la versión de escritorio
están en [`escritorio/README.md`](./escritorio/README.md#ejecución).

### Despliegue

La base de datos corre en Oracle Autonomous Database (OCI). La aplicación web
(`web/`) está desplegada en Google Cloud Run. La versión de escritorio se
ejecuta localmente.

---

## 7. Autor y licencia

> 📘 **[Manual de usuario](docs/MANUAL.md)**: cómo usar la plataforma campo por campo, qué datos se ingresan y qué garantiza en seguridad.

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)

Licencia: MIT.
