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

Gestor de presupuesto personal con persistencia en Oracle Autonomous Database:
registra ingresos y gastos por categoría, los compara con el plan 50/30/20 en
un dashboard con gráficas y envía un resumen por correo cada semana y cada mes.
Nació como un registro de pagos en el curso de Desarrollo de Software de la
Universidad EAN.

El repositorio tiene dos interfaces de la misma aplicación, alimentadas por
**un solo origen**: la lógica, las reglas y los datos viven en el servidor web
y las dos interfaces los muestran.

- **[`web/`](./web)** — versión web en Python (FastAPI + Jinja2 + Chart.js),
  desplegada en Google Cloud Run. Es además una **PWA**: se instala como app en
  Android, iPhone/iPad, Windows, macOS y Linux desde el navegador.
- **[`escritorio/`](./escritorio)** — aplicación de escritorio en Java/Swing
  (FlatLaf + JFreeChart), publicada como **PRPagos.exe** con Java incluido y
  con actualización automática. No tiene base de datos ni claves: pide todo a
  la API de la web.

Ver [`CHANGELOG-migracion.md`](./CHANGELOG-migracion.md) para el detalle de la
migración de escritorio a web.

### En pocas palabras

- **Qué hace:** registra ingresos y gastos, muestra en un dashboard cuánto se
  va en cada categoría y si el mes va dentro del plan 50/30/20, y manda el
  resumen por correo. Cada persona ve solo lo suyo.
- **Qué lo hace interesante:** una sola lógica para dos interfaces. Las reglas,
  los cálculos y los mensajes viven en la capa de servicios del servidor
  (`web/app/servicios/`); las páginas web la usan directamente y la app de
  escritorio por una API JSON. Un dato es uno solo, se vea donde se vea.
- **Cómo probarlo:** entra a la [demo web](https://prpagos-web-1087929107584.southamerica-east1.run.app)
  y crea un usuario, o descarga la [aplicación de escritorio](https://github.com/lariasca1994/PRPagos/releases/latest).
  Para correrlo desde el código, ve a [Instalación para pruebas](#6-instalación-para-pruebas).

### Demo en vivo

**Aplicación web:** [abrir la demo en vivo](https://prpagos-web-1087929107584.southamerica-east1.run.app)

**Aplicación de escritorio:** [descargar la última versión](https://github.com/lariasca1994/PRPagos/releases/latest)
(instalador o portable para Windows; se instala y se usa, sin configuración).

### Funcionalidades

**Presupuesto (igual en web y escritorio)**
- Registro de **ingresos y gastos** con tipo, monto, fecha, categoría y concepto
- **Categorías** sugeridas y propias; cada categoría de gasto pertenece a un
  grupo del plan (necesidades, deseos, ahorro y deudas)
- **Plan 50/30/20** con porcentajes editables por cuenta
- **Dashboard** del mes: ingresos, gastos, balance y ahorro; ingresos vs. gastos
  de los últimos 6 meses, gastos por categoría y avance del plan con su estado
- **Presupuesto** por categoría y mes (como una hoja de presupuesto anual), comparado con
  los registros: tarjetas, gastos presupuestados vs. reales del año, distribución frente
  al plan, mayores gastos, estado por categoría y resumen de los 12 meses
- Interactivo: un mes de las gráficas abre ese mes, una categoría abre sus registros y
  cada registro nuevo avisa cómo va el presupuesto de su categoría
- Consulta con filtros (tipo, concepto, montos, fechas y categoría), totales y exportación a CSV

**Resúmenes por correo**
- Semanal cada viernes y mensual cada día 29 (en febrero, el último día), a las
  08:00 de Colombia, programados desde GitHub Actions; se pueden desactivar en Perfil
- Botones para recibirlos al momento desde la web o el escritorio, con el mismo contenido

**Cuentas y seguridad (una sola para web y escritorio)**
- Ingreso en dos pasos obligatorio: contraseña y código de una app autenticadora (TOTP)
- **Llaves de acceso (passkeys):** ingreso con Windows Hello, huella, rostro o PIN, en la web
  (Windows, Android y iPhone) y en el escritorio; la misma llave sirve en Edge y en la app del mismo Windows
- **Códigos de respaldo** y **código por correo** para recuperar el acceso si se pierde el teléfono
- **Perfil** con datos, plan, categorías, autenticador, códigos de respaldo y contraseña
- Bloqueo de 5 minutos tras 5 intentos fallidos seguidos
- Sesión que se cierra tras 6 horas sin uso (máximo 8 horas), con un mensaje del motivo

**Escritorio**
- **PRPagos.exe** con Java incluido: instalador por usuario o versión portable,
  sin configuración ni claves dentro
- **Actualización automática** desde GitHub Releases, verificada con SHA-256

**Interfaz**
- Web y escritorio con la misma estructura, menú, ícono, portada animada y
  colores (rojo Oracle y azul de Google Cloud Run), en tema claro y oscuro

### Stack

| Capa | Web | Escritorio |
|---|---|---|
| Interfaz | Jinja2, HTML, CSS y Chart.js | Java Swing con FlatLaf y JFreeChart |
| Lógica y reglas | `web/app/servicios/` (FastAPI) | La misma, por la API JSON (`/api/...`) |
| Acceso a datos | python-oracledb (modo thin), usuario de la app sin permisos de DDL | — (solo la API) |
| Base de datos | Oracle Autonomous Database | La misma, a través del servidor |
| Correos | Brevo (API HTTPS) desde el servidor | Los envía el servidor |
| Sesiones | JWT en cookie httponly; 6 h sin uso, máximo 8 h | El mismo JWT, en la cabecera Bearer |
| Contraseñas y TOTP | PBKDF2-SHA256; secreto TOTP cifrado con AES-256-GCM | Validados en el servidor |
| Llaves de acceso | WebAuthn en el navegador (librería `webauthn`) | Windows Hello (`webauthn.dll` vía JNA), verificadas en el servidor |
| Distribución | Docker en Google Cloud Run (GitHub Actions) | PRPagos.exe en GitHub Releases (jpackage) |
| App instalable | PWA (Android, iOS, Windows, macOS, Linux) | Instalador o portable para Windows |

---

## 2. Estructura del proyecto

```
web/           Aplicación web en Python (FastAPI + Jinja2)
escritorio/    Aplicación de escritorio en Java (Swing), cliente de la API, y su empaquetado
compartido/    Consultas SQL del dashboard y plantillas de correo (las usa el servidor)
sql/           Tablas, procedimientos y migraciones (seguridad, finanzas, actualizaciones, llaves)
wallet/        Wallet de Oracle Autonomous Database (no se sube a git)
docs/          Manuales, diagramas y capturas
.github/       Despliegue web, resúmenes programados y publicación del escritorio
Dockerfile     Imagen de la versión web para Cloud Run (se construye desde la raíz)
```

Solo el servidor (`web/`) usa `compartido/`, `sql/` y `wallet/`: es el único
que habla con la base y envía correos. El escritorio no tiene acceso a nada de
eso; recibe los datos ya calculados por la API.

---

## 3. Arquitectura

<p align="center">
  <img src="docs/arquitectura.svg" alt="Diagrama de arquitectura: el navegador y PRPagos Escritorio llegan por HTTPS a PRPagos Web (FastAPI) en Google Cloud Run, que tiene la única lógica y es el único que se conecta por TLS con Wallet a Oracle Autonomous Database y envía los correos por Brevo; GitHub Actions despliega, programa las tareas y publica los Releases" width="100%">
</p>

- **Un solo origen:** la web (FastAPI) corre en un contenedor Docker en Cloud
  Run y concentra toda la lógica en `app/servicios/`. Las páginas HTML y la API
  JSON (`/api/...`) llaman a las mismas funciones.
- La **versión de escritorio** (Java Swing) corre en el equipo del usuario y es
  un cliente de esa API: recibe los datos, las cifras y los mensajes ya listos.
- Solo el servidor llega a la base (TLS con Wallet, con un usuario propio de la
  app que no puede crear ni borrar tablas) y llama a los procedimientos
  almacenados, que verifican la autorización y registran la auditoría.
- **GitHub Actions** despliega la web, la llama cada día a las 08:00 para enviar
  los resúmenes por **Brevo**, y publica el ejecutable de escritorio como
  Release, del que se actualizan las aplicaciones instaladas.

Cada versión tiene su propio diagrama detallado en
[`web/README.md`](./web/README.md#3-arquitectura) y
[`escritorio/README.md`](./escritorio/README.md#3-arquitectura).

### Seguridad

- **Runtimes con parches de seguridad:** la web fija Python 3.12.15 y certifi 2026.10.19 o
  posterior, que deja de confiar en los certificados intermedios comprometidos de eMudhra; el
  escritorio trae Java 21 al día y sin las raíces que firmaron esos intermedios.
- **Una sola seguridad:** contraseña, autenticador, códigos de respaldo, llaves
  de acceso, bloqueos y sesiones se validan en el servidor, para la web y para
  el escritorio. El escritorio no guarda claves ni datos de conexión.
- Contraseñas con PBKDF2-SHA256 (600.000 iteraciones y sal única); las cuentas
  con el SHA-256 anterior se actualizan solas en su siguiente ingreso.
- Verificación en dos pasos obligatoria (TOTP, RFC 6238); un código no se puede
  reutilizar. El secreto se guarda cifrado con AES-256-GCM y la clave vive
  fuera de la base.
- Llaves de acceso (WebAuthn): en la base solo se guarda la clave pública y un contador; exigen
  la verificación del dispositivo, solo valen para el sitio de PRPagos y cada firma se comprueba
  con un reto nuevo. Agregar una pide un código vigente.
- Recuperación sin el teléfono: 10 códigos de respaldo de un solo uso (solo se
  guarda su huella SHA-256) o un código por correo que vence en 15 minutos; cada
  uso queda en la auditoría.
- La visibilidad de los registros está limitada por usuario, y el procedimiento
  de actualización verifica la autorización antes de modificar un registro.
- La tarea de resúmenes y el registro de versiones exigen un token; las
  actualizaciones del escritorio solo se aceptan desde GitHub por HTTPS y con la
  huella SHA-256 que entrega el servidor.
- La base se usa con un usuario de la aplicación de privilegios mínimos (sin
  permisos para crear ni borrar tablas) sobre el esquema dueño de los datos.
- Conexión a la base por TLS con Wallet. El Wallet y el `.env` no se suben al
  repositorio; solo la plantilla de ejemplo.

---

## 4. Plataformas y su función

| Plataforma | Función en el proyecto |
|---|---|
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Corre la versión web (FastAPI + Jinja2) en un contenedor Docker. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Oracle Autonomous Database guarda cuentas, registros, categorías, planes y versiones del escritorio, y ejecuta los procedimientos almacenados con las reglas de negocio y la auditoría. Conexión por TLS con Wallet. |
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | La versión de escritorio corre en el equipo de cada usuario como PRPagos.exe, con Java incluido; pide los datos a la API de la web. |
| ![Brevo](https://img.shields.io/badge/Brevo-0B996E?style=for-the-badge&logo=brevo&logoColor=white) | Envía los resúmenes semanales y mensuales y los códigos de recuperación (plan gratuito). |
| ![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white) | Despliega la web, programa los resúmenes diarios y publica el ejecutable de escritorio en GitHub Releases. |
| ![qa-evidencia](https://img.shields.io/badge/qa--evidencia-2EAD33?style=for-the-badge&logo=playwright&logoColor=white) | Prueba la versión web automáticamente dos veces al día y publica la evidencia. |

---

## 5. Cómo usar la plataforma

Las dos versiones muestran los mismos datos del mismo servidor: lo que registras en una
aparece en la otra al instante. Cada una tiene su manual con capturas:

- **Versión web:** [Manual web](docs/MANUAL-WEB.md) (incluye cómo instalarla como app)
- **Versión de escritorio:** [Manual de escritorio](docs/MANUAL-ESCRITORIO.md) (incluye la
  instalación y las actualizaciones automáticas)

Resumen del flujo, igual en ambas:

1. **Crear cuenta:** correo, nombre completo y contraseña (mínimo 8 caracteres).
2. **Iniciar sesión** con correo y contraseña, y luego el código de 6 dígitos de tu
   app autenticadora. La primera vez se configura escaneando un QR y se reciben
   10 códigos de respaldo.
3. **Dashboard:** el mes con ingresos, gastos, balance, ahorro y el avance del plan 50/30/20.
4. **Presupuesto:** planea cada categoría por mes (solo un mes, hasta diciembre o todo el año)
   y compáralo con lo registrado; toca una categoría para ver sus movimientos.
5. **Nuevo registro:** tipo (ingreso o gasto), monto, fecha, categoría y concepto.
6. **Consultar registros:** filtra, revisa totales, **modifica** o **exporta** a CSV.
7. **Perfil:** nombre, resúmenes por correo, porcentajes del plan, categorías,
   autenticador, códigos de respaldo y contraseña.
8. **Perdí mi teléfono:** entra con un código de respaldo o pide un código al correo.
8. **Llave de acceso:** agrega tu dispositivo en Perfil y luego entra con **Ingresar con llave
   de acceso** (Windows Hello, huella, rostro o PIN), sin contraseña ni código.

---

## 6. Instalación para pruebas

### Requisitos

- Una base Oracle accesible (por ejemplo, Oracle Autonomous Database)
- El Wallet de esa base, descargado desde la consola de Oracle Cloud
- Para la versión web: Python 3.12 (3.12.15 o posterior, con los parches de seguridad de octubre de 2026)
- Para compilar la versión de escritorio: JDK 17 o superior (para usarla basta
  el ejecutable de Releases; no necesita base ni configuración)
- Para los correos: una cuenta de Brevo (plan gratuito) con un remitente verificado

### Base de datos (una sola vez)

Ejecuta, en orden, `sql/DBPagos.sql` y las migraciones `seguridad`, `finanzas`,
`actualizaciones` y `llaves` con el usuario **dueño** del esquema (el de la app
no tiene permisos para crear tablas). Desde `web/`, con su `.env` configurado:

```bash
python migrar.py seguridad --aplicar
python migrar.py finanzas --aplicar
python migrar.py actualizaciones --aplicar
python migrar.py llaves --aplicar
```

Sin `--aplicar` solo muestra el SQL. Las migraciones son aditivas y se pueden
repetir. Luego descomprime el Wallet en `wallet/`, en la raíz del repositorio
(está en `.gitignore`).

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

Completa el `.env` con los datos del Wallet y de la base (usuario de la app y
`DB_ESQUEMA`), un `JWT_SECRET` propio, la `TOTP_CLAVE` y, para los correos,
`BREVO_API_KEY` y `BREVO_REMITENTE`. Detalle en [`web/README.md`](./web/README.md#configuración).

### Versión de escritorio

No necesita configuración: usa la web de producción. Para probarla contra la
web local, ejecútala con `PRPAGOS_URL=http://localhost:8000`. Detalle en
[`escritorio/README.md`](./escritorio/README.md#ejecución).

### Ejecución

| Versión | Comando | Dónde |
|---|---|---|
| Web | `uvicorn app.main:app --reload` (desde `web/`) | `http://127.0.0.1:8000` |
| Escritorio (código) | Ejecutar `com.prpagos.ui.VentanaLogin` | Ventana local |
| Escritorio (ejecutable) | `.\empaquetar.ps1` (desde `escritorio/`) | `build\dist\PRPagos\PRPagos.exe` |

### Despliegue

Todo sale de GitHub: cada commit y push a `main` despliega la web en Cloud Run
(`desplegar.yml`) y publica el escritorio como Release (`escritorio.yml`), que
las aplicaciones instaladas toman solas; `resumenes.yml` llama cada día a la
tarea de los correos. Nada se despliega a mano.

---

## 7. Autor y licencia

> 📘 **Manuales de usuario:** [web](docs/MANUAL-WEB.md) y [escritorio](docs/MANUAL-ESCRITORIO.md): cómo usar cada versión campo por campo, qué datos se ingresan y qué garantiza en seguridad.

**Luis Felipe Arias Carriazo**
[GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1)

Licencia: MIT.
