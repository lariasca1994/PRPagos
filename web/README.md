# PRPagos Web

<p>
  <a href="https://prpagos-web-1087929107584.southamerica-east1.run.app"><img src="../docs/demo-badge.svg" alt="Abrir la demo en vivo" height="32"></a>
  <a href="https://frontend-nine-topaz-99.vercel.app"><img src="https://portafolio-status.onrender.com/api/status/prpagos/badge.svg" alt="Estado en vivo del proyecto" height="32"></a>
  <a href="https://d4i3vsgw7xwmh.cloudfront.net"><img src="https://portafolio-status.onrender.com/api/status/prpagos/qa-badge.svg" alt="Fecha y resultado de la última prueba E2E" height="32"></a>
</p>

![Python](https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![Jinja](https://img.shields.io/badge/Jinja2-B41717?style=for-the-badge&logo=jinja&logoColor=white)
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

Versión web de PRPagos, construida con FastAPI. Habla con la **misma** base
de datos, el **mismo** Wallet de Oracle y los **mismos** procedimientos
almacenados que la versión de escritorio ([`../escritorio`](../escritorio)):
no es una reescritura de las reglas de negocio, es la misma aplicación
expuesta como páginas web en vez de ventanas Swing.

Además es una **PWA** (aplicación web progresiva): se puede instalar como app
en Android, iPhone/iPad, Windows, macOS y Linux desde el navegador, con su
propio ícono y ventana, sin pasar por ninguna tienda de aplicaciones.

Ver el [`README.md`](../README.md) de la raíz para la descripción general del
proyecto y [`CHANGELOG-migracion.md`](../CHANGELOG-migracion.md) para el
detalle de la migración.

### En pocas palabras

- **Qué hace:** permite registrar, consultar, modificar y exportar pagos desde
  el navegador o desde la app instalada (PWA). Cada persona ve solo sus propios
  pagos.
- **Qué lo hace interesante:** la capa web no reimplementa las reglas: la
  autorización y la auditoría viven en procedimientos almacenados de Oracle,
  los mismos que usa la versión de escritorio.
- **Cómo probarlo:** entra a la [demo](https://prpagos-web-1087929107584.southamerica-east1.run.app)
  y crea un usuario. Para correrlo en tu equipo, ve a
  [Instalación para pruebas](#6-instalación-para-pruebas).

### Demo en vivo

**Aplicación:** [abrir la demo en vivo](https://prpagos-web-1087929107584.southamerica-east1.run.app)

### Funcionalidades

**Cuentas**
- Registro e ingreso con contraseñas hasheadas (el usuario es un correo
  `@EAN.com`)
- Recuperación de contraseña
- Sesión de hasta 8 horas en una cookie `httponly`
- La misma cuenta sirve en la versión de escritorio

**Pagos**
- Ingresar, consultar y modificar pagos
- Filtros por concepto, monto y rango de fechas
- Exportar la consulta a CSV
- Cada usuario ve y modifica únicamente sus propios pagos

**Auditoría**
- Cada operación queda registrada por la base de datos

**App instalable (PWA)**
- Se instala en el celular, la tablet o el computador desde el navegador
- Abre en su propia ventana, con ícono y nombre propios
- Muestra una página propia cuando no hay conexión

Las rutas de pagos usan `/registros` en la URL en lugar de `/pagos`: algunos
antivirus con protección de banca en línea interceptan los formularios con
contraseña cuando la URL contiene "pagos".

### Stack

| Capa | Tecnología |
|---|---|
| Servidor | FastAPI, Uvicorn |
| Interfaz | Jinja2, HTML y CSS |
| Acceso a datos | python-oracledb (modo thin) |
| Base de datos | Oracle Autonomous Database |
| Reglas de negocio | Procedimientos almacenados PL/SQL |
| Sesiones | JWT (HS256) en cookie httponly |
| Contraseñas | SHA-256, compatible con la versión de escritorio |
| App instalable | PWA: manifiesto web y service worker |
| Despliegue | Docker en Google Cloud Run |

---

## 2. Estructura del proyecto

```
web/
├── requirements.txt
├── .env.example          Plantilla de configuración
└── app/
    ├── main.py           Punto de entrada y manejo de errores
    ├── config.py         Carga del .env
    ├── database.py       Conexión a Oracle con el Wallet
    ├── repositorio.py    Llamadas a los procedimientos almacenados
    ├── seguridad.py      Hash de contraseñas y sesiones JWT
    ├── dependencias.py   Identificación del usuario en cada petición
    ├── routers/          Rutas de autenticación y de pagos
    ├── templates/        Plantillas Jinja2
    └── static/           Hoja de estilos, tema, íconos y PWA
                          (manifest.webmanifest, sw.js, offline.html)
```

Las tablas y procedimientos (`../sql/`), el Wallet (`../wallet/`) y el
`Dockerfile` están en la raíz del repositorio, compartidos con la versión de
escritorio.

---

## 3. Arquitectura

<p align="center">
  <img src="../docs/arquitectura-web.svg" alt="Diagrama de arquitectura: el navegador llega por HTTPS a PRPagos Web (FastAPI) en Google Cloud Run, que valida la sesión JWT y, mediante repositorio.py y el Wallet, se conecta por TLS a los procedimientos almacenados de Oracle Autonomous Database" width="100%">
</p>

- Una sola app FastAPI sirve páginas Jinja2 protegidas por una sesión JWT en
  cookie.
- Cada petición **valida la sesión** antes de responder.
- `repositorio.py` concentra el acceso a datos: usa `python-oracledb` en modo
  thin y el **Wallet**, copiado a la imagen al construirla, para conectarse
  por TLS.
- Los **procedimientos almacenados** de Oracle deciden qué pagos ve cada
  usuario, verifican la autorización y registran la auditoría.

### Seguridad

- Las contraseñas se guardan con el mismo hash SHA-256 que usa la tabla
  `TBPLogin` desde la versión de escritorio, para que un usuario pueda
  iniciar sesión desde cualquiera de las dos interfaces sin migrar datos.
- Sesión en cookie `httponly` y `SameSite=Lax`: JavaScript no puede leer el
  token.
- Un usuario solo puede ver y modificar sus propios pagos; esa regla vive en
  los procedimientos almacenados (`buscar_tbpagos`, `actualizar_tbpago`), no
  se reimplementa en la capa web.
- Los errores de base de datos muestran un mensaje genérico; el detalle queda
  solo en el log del servidor.
- El service worker de la PWA solo guarda en caché archivos estáticos
  (estilos, scripts, íconos) y la página sin conexión: las páginas con pagos,
  la sesión y los CSV siempre van a la red y no quedan guardados en el
  dispositivo.
- El Wallet y el `.env` no se suben al repositorio.

---

## 4. Plataformas y su función

| Plataforma | Función en el proyecto |
|---|---|
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Corre la app FastAPI en un contenedor Docker construido con el `Dockerfile` de la raíz. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Oracle Autonomous Database guarda usuarios y pagos, y ejecuta los procedimientos almacenados con las reglas y la auditoría. Conexión por TLS con el Wallet; no requiere ningún otro servicio, cuenta ni clave de API. |
| ![qa-evidencia](https://img.shields.io/badge/qa--evidencia-2EAD33?style=for-the-badge&logo=playwright&logoColor=white) | Prueba la demo automáticamente dos veces al día y publica la evidencia. |

---

## 5. Cómo usar la plataforma

### 5.1 Crear una cuenta

1. En la portada, pulsa **Crear cuenta** (o **Registrarme** en el menú).
2. Completa:

   | Campo | Dato |
   |---|---|
   | Usuario (correo @EAN.com) | Tu usuario de acceso |
   | Nombre completo | Se usa también para recuperar la contraseña |
   | Contraseña | Tu contraseña |

3. Pulsa **Crear usuario**. La misma cuenta sirve en la versión de escritorio.

### 5.2 Iniciar sesión

1. Pulsa **Ya tengo cuenta** o **Ingresar**.
2. Escribe **Usuario (correo @EAN.com)** y **Contraseña** y pulsa **Ingresar**.
3. La sesión dura hasta 8 horas.

### 5.3 Registrar un pago

1. En el menú, elige **Nuevo registro**.
2. Completa **Monto**, **Fecha** y **Concepto** y pulsa **Guardar**.

### 5.4 Consultar, modificar y exportar

1. Elige **Consultar registros**.
2. Filtra con **Concepto contiene**, **Monto mínimo**, **Monto máximo**, **Desde** y
   **Hasta**, y pulsa **Buscar** (**Limpiar** quita los filtros).
3. La tabla muestra **ID**, **Monto**, **Fecha** y **Concepto** de tus pagos.
4. **Modificar** abre el pago para cambiar monto, fecha o concepto; pulsa
   **Guardar cambios**.
5. **Exportar a CSV** descarga la consulta con los filtros aplicados.

### 5.5 Recuperar la contraseña

1. En el inicio de sesión, pulsa **Olvidé mi contraseña**.
2. Escribe tu **Correo (@EAN.com)**, el **Nombre registrado** y la **Nueva
   contraseña**, y pulsa **Actualizar contraseña**.

### 5.6 Instalar la app (PWA)

Abre la [demo](https://prpagos-web-1087929107584.southamerica-east1.run.app) y:

| Plataforma | Cómo instalarla |
|---|---|
| Android (Chrome) | Menú **⋮** → **Instalar app** (o **Agregar a la pantalla principal**). |
| iPhone / iPad (Safari) | Botón **Compartir** → **Agregar a inicio**. |
| Windows, macOS y Linux (Chrome o Edge) | Ícono de instalar en la barra de direcciones, o menú → **Instalar PRPagos**. |

La app queda con su ícono junto a las demás y abre en su propia ventana. Usa
la misma cuenta y los mismos datos que la versión web y la de escritorio, y
necesita conexión para consultar y guardar pagos.

---

## 6. Instalación para pruebas

### Requisitos

- Python 3.11 o superior
- Una base Oracle accesible, preparada con `../sql/DBPagos.sql` (una sola vez)
- El Wallet de esa base descomprimido en `../wallet`. No viene en el
  repositorio: se descarga desde la consola de Oracle Cloud.

### Instalación

```bash
git clone https://github.com/lariasca1994/PRPagos.git
cd PRPagos/web

python -m venv .venv
.venv\Scripts\Activate.ps1        # Windows
source .venv/bin/activate         # Linux o macOS

pip install -r requirements.txt
cp .env.example .env
```

Si PowerShell bloquea la activación del entorno:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned
```

### Configuración

El `.env` necesita la ruta y la contraseña del Wallet, el alias de conexión
(de `wallet/tnsnames.ora`), el usuario y la contraseña de la base, y un
secreto propio para las sesiones:

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

- `JWT_SECRET` firma las sesiones

La plantilla indica el nombre y el propósito de cada variable. El `.env`
nunca se sube a git.

### Ejecución

```bash
cd web
uvicorn app.main:app --reload
```

El `cd web` es necesario: las rutas de plantillas y archivos estáticos se
resuelven desde ahí.

| Recurso | Dirección |
|---|---|
| Aplicación | `http://127.0.0.1:8000` |
| Documentación de la API | `http://127.0.0.1:8000/docs` |

### Despliegue

La aplicación corre en Google Cloud Run, con Oracle Autonomous Database como
base de datos. El despliegue es continuo: cada push a `main` que cambie
`web/`, el `Dockerfile` o el propio flujo ejecuta
[`.github/workflows/desplegar.yml`](../.github/workflows/desplegar.yml), que:

1. Construye la imagen con el `Dockerfile` de la **raíz** del repositorio
   (necesita copiar tanto `web/app/` como `wallet/`).
2. La publica en Artifact Registry.
3. Actualiza el servicio de Cloud Run, conservando sus variables de entorno.

- El Wallet no está en Git: el flujo lo toma de Secret Manager al construir
  la imagen.
- GitHub se autentica en Google Cloud por federación de identidades, sin
  llaves guardadas en el repositorio.
- Las variables del `.env` viven como variables de entorno del servicio en
  Cloud Run, nunca como un archivo dentro de la imagen.

---

## 7. Autor y licencia

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)

Licencia: MIT.
