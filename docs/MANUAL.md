# 📘 Manual de usuario · PRPagos

Guía para usar la versión web paso a paso: qué significa cada campo, qué datos se ingresan y qué
protege la plataforma. La versión de escritorio usa las mismas cuentas, campos y datos (ver
[`escritorio/README.md`](../escritorio/README.md)). Para la parte técnica vuelve al
[README](../README.md).

**Demo web:** [PRPagos (Google Cloud Run)](https://prpagos-web-1087929107584.southamerica-east1.run.app)

## Contenido

1. [Proyecto: objetivos y alcances](#1-proyecto-objetivos-y-alcances)
2. [Glosario](#2-glosario)
3. [Plataformas, base de datos y servicios](#3-plataformas-base-de-datos-y-servicios)
4. [Paso a paso](#4-paso-a-paso)
5. [Seguridad: tu información protegida](#5-seguridad-tu-información-protegida)
6. [Agradecimientos](#6-agradecimientos)

---

## 1. Proyecto: objetivos y alcances

### Objetivo

Simulador de registro de pagos: cada persona anota sus pagos (monto, fecha y concepto), los
consulta con filtros, los modifica y los exporta a CSV. Las reglas de negocio viven en
procedimientos almacenados de Oracle, compartidos por la versión web y la de escritorio.

### Alcance: qué necesitas y qué esperar

- Solo necesitas un navegador; también se puede **instalar como app** (PWA) desde el navegador.
- Un **correo real**: es tu usuario y ahí llegarán las notificaciones.
- Una **app autenticadora** en el celular (Google Authenticator, Microsoft Authenticator, Authy…):
  la verificación en dos pasos es obligatoria. Se configura una sola vez y sirve en la web y en la
  versión de escritorio.

### Límites

- **Registra solo datos de ejemplo**: es un simulador, no un sistema de pagos real.
- Si pierdes el teléfono con el autenticador, no podrás ingresar ni recuperar la contraseña por tu
  cuenta: la recuperación pide un código del autenticador.
- Las cuentas creadas antes de la verificación en dos pasos pueden recuperar la contraseña con el
  correo y el nombre registrado hasta que configuren su autenticador en el primer ingreso.

## 2. Glosario

Palabras técnicas que aparecen en este manual, explicadas en lenguaje simple.

| Término | Qué significa |
|---|---|
| **Administrador** | Cuenta con permisos para gestionar usuarios y configuraciones de la aplicación. |
| **API** | Puerta de entrada de un programa para que otros programas le pidan o le envíen datos. |
| **Base de datos** | Lugar donde la aplicación guarda la información de forma ordenada y permanente. |
| **Cifrado / TLS / HTTPS** | Protección que codifica la información mientras viaja, para que nadie en el camino pueda leerla. |
| **Contraseña cifrada (hash)** | La contraseña no se guarda tal cual, sino como una "huella" de la que no se puede recuperar el texto original. |
| **Cookie** | Pequeño dato que el navegador guarda para recordar tu sesión en ese sitio. |
| **CSV** | Archivo de texto con datos en columnas, que se abre en Excel o Google Sheets. |
| **JWT** | Tipo de token firmado digitalmente: si alguien lo altera, el servidor lo rechaza. |
| **Navegador** | Programa con el que abres páginas web: Chrome, Edge, Firefox, Safari. |
| **Nube** | Servidores de una empresa (Amazon, Microsoft, Google, Oracle…) donde la aplicación funciona sin depender de un computador propio. |
| **PWA (app instalable)** | Página web que se puede instalar en el celular o el computador y abrir como una app. |
| **Rate limit (límite de intentos)** | Tope de intentos en poco tiempo, para frenar a quien intenta adivinar contraseñas. |
| **TOTP (código de verificación)** | Código de 6 dígitos que cambia cada 30 segundos en tu app autenticadora; se pide además de la contraseña. |
| **Rol** | Tipo de cuenta que define qué puede hacer cada persona (por ejemplo, usuario o administrador). |
| **Servidor** | Computador en la nube que atiende las peticiones de la aplicación. |
| **Sesión** | El tiempo en que la aplicación te reconoce después de iniciar sesión, sin pedirte la contraseña de nuevo. |
| **Variables de entorno / secretos** | Claves y contraseñas internas de la aplicación, guardadas fuera del código para que no queden expuestas. |

## 3. Plataformas, base de datos y servicios

Qué servicio de nube, base de datos o API usa el proyecto y para qué sirve cada uno.

| Plataforma | Tipo | Qué hace en el proyecto |
|---|---|---|
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Nube | Corre la versión web (FastAPI + Jinja2) en un contenedor Docker. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Base de datos | Oracle Autonomous Database guarda usuarios y pagos, y ejecuta los procedimientos almacenados con las reglas de negocio y la auditoría. La conexión es por TLS con el Wallet; no requiere ningún otro servicio, cuenta ni clave de API. |
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | Aplicación de escritorio | La versión de escritorio corre en el equipo de cada usuario, sin servidor. |
| ![qa-evidencia](https://img.shields.io/badge/qa--evidencia-2EAD33?style=for-the-badge&logo=playwright&logoColor=white) | Pruebas automatizadas | Prueba la versión web automáticamente dos veces al día y publica la evidencia. |

## 4. Paso a paso

### 4.1 Portada

![Portada de PRPagos](img/manual/01-portada.png)

La portada es el inicio de sesión. Desde ahí eliges **Regístrate** u **Olvidé mi contraseña**. El
botón ◐ cambia entre tema claro y oscuro.

### 4.2 Crear una cuenta

![Formulario de registro con sus campos numerados](img/manual/02-registro.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Correo | Tu correo real; es tu usuario | Forma `nombre@dominio.com` y único |
| 2 | Nombre completo | Tu nombre | Se usa también para recuperar la contraseña |
| 3 | Contraseña | Tu contraseña | Mínimo 8 caracteres |
| 4 | Repite la contraseña | La misma | Debe coincidir |
| 5 | Crear cuenta | Botón | Crea la cuenta; sirve en web y en escritorio |

### 4.3 Iniciar sesión (paso 1: contraseña)

![Formulario de inicio de sesión](img/manual/03-login.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Correo | Tu correo |
| 2 | Contraseña | Tu contraseña |
| 3 | Continuar | Pasa al código de verificación |
| 4 | Olvidé mi contraseña | Abre la recuperación (ver 4.10) |

### 4.4 Configurar la verificación en dos pasos (solo la primera vez)

![Configuración del autenticador con código QR](img/manual/04-configurar.png)

| # | Campo | Qué hacer |
|:-:|---|---|
| 1 | Código QR | Escanéalo con tu app autenticadora |
| 2 | Clave | La misma información, para escribirla a mano si no puedes escanear |
| 3 | Código de 6 dígitos | El que muestra la app para **PRPagos** |
| 4 | Activar e ingresar | Guarda el autenticador y abre **Consultar registros** |

Si ya lo configuraste en la versión de escritorio, este paso no aparece.

### 4.5 Iniciar sesión (paso 2: código)

![Código de verificación](img/manual/05-codigo.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Código de 6 dígitos | El código actual de tu app (cambia cada 30 segundos) |
| 2 | Ingresar | Abre **Consultar registros**. La sesión dura hasta 8 horas |

### 4.6 Registrar un pago

![Formulario de nuevo registro](img/manual/06-nuevo.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Monto | Valor del pago, sin puntos ni signo `$` | Número válido |
| 2 | Fecha | Día del pago | Obligatoria |
| 3 | Concepto | Para qué fue el pago | Obligatorio |
| 4 | Guardar | Botón | Registra el pago a tu nombre |

### 4.7 Consultar y exportar

![Consulta de registros con filtros](img/manual/07-consulta.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Concepto contiene | Parte del concepto que buscas |
| 2 | Monto mínimo | Valor desde el que se muestran pagos |
| 3 | Monto máximo | Valor hasta el que se muestran pagos |
| 4 | Desde | Fecha inicial |
| 5 | Hasta | Fecha final |
| 6 | Buscar | Aplica los filtros (**Limpiar** los quita). Un campo vacío no filtra |
| 7 | Exportar a CSV | Descarga los pagos de la consulta, con los filtros aplicados |
| 8 | Modificar | Abre el pago para corregirlo |

El **Total** suma los pagos que cumplen los filtros. Solo ves **tus** pagos.

### 4.8 Modificar un pago

![Formulario de modificación](img/manual/08-editar.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Monto | El valor corregido |
| 2 | Fecha | La fecha corregida |
| 3 | Concepto | El concepto corregido |
| 4 | Guardar cambios | Aplica la corrección |

### 4.9 Seguridad

![Pantalla Seguridad](img/manual/09-seguridad.png)

| # | Campo | Qué es / qué escribir |
|:-:|---|---|
| 1 | Estado | **Activa** cuando tu autenticador está configurado |
| 2 | Código del autenticador actual | Para **Cambiar autenticador** (nuevo teléfono o nueva app): luego escaneas un QR nuevo y lo confirmas |
| 3 | Contraseña actual | La que usas hoy |
| 4 | Nueva contraseña | Mínimo 8 caracteres |
| 5 | Repite la nueva contraseña | La misma |
| 6 | Código del autenticador | El código actual de tu app; con **Guardar contraseña** se aplica el cambio |

### 4.10 Recuperar la contraseña

![Formulario de recuperación de contraseña](img/manual/10-recuperar.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Correo | Tu correo |
| 2 | Nombre registrado | El nombre completo con el que te registraste (no distingue mayúsculas) |
| 3 | Código del autenticador | El código actual de tu app (vacío solo si nunca lo configuraste) |
| 4 | Nueva contraseña | La contraseña nueva (mínimo 8 caracteres) |
| 5 | Repite la nueva contraseña | La misma |
| 6 | Actualizar contraseña | Aplica el cambio |

### 4.11 Preguntas frecuentes

**¿Qué correo uso?** Uno real tuyo: es tu usuario y ahí llegarán las notificaciones.

**¿Tengo que configurar el autenticador dos veces, en la web y en el escritorio?** No. Es uno solo
por cuenta: el que configures en una versión sirve en la otra.

**¿Lo que registro en la web aparece en la app de escritorio?** Sí. Ambas usan la misma base de
datos y las mismas cuentas.

**¿Puedo eliminar un pago?** No desde una cuenta normal; corrígelo con **Modificar**. Eliminar está
reservado al administrador.

## 5. Seguridad: tu información protegida

Tu confianza es lo primero: la plataforma solo pide los datos necesarios para funcionar, los protege y no los comparte con terceros. Esto es lo que se guarda y cómo se protege.

### Qué datos se guardan y dónde

| Dato | Para qué se usa | Dónde se guarda | Quién lo ve |
|---|---|---|---|
| Usuario y nombre | Identificar tu cuenta y recuperar la contraseña | Oracle Autonomous Database (tabla `TBPLogin`) | Tú y el administrador |
| Contraseña | Iniciar sesión | Oracle, solo su huella PBKDF2-SHA256 con sal | Nadie la ve en texto plano |
| Clave del autenticador | Verificar tus códigos | Oracle, cifrada con AES-256-GCM | Nadie; la clave de cifrado vive fuera de la base |
| Pagos (monto, fecha, concepto) | Tu registro de pagos | Oracle, marcados con tu cuenta | Tú y el administrador |
| Sesión | Mantenerte conectado | Cookie en tu navegador | Firmada, invisible para JavaScript, vence a las 8 horas |

### Cómo se protege tu información

Comprobado en el código del proyecto (`web/app/seguridad.py`, `routers/auth.py`,
`routers/registros.py`, `repositorio.py`):

- **Cada quien ve solo sus pagos:** la consulta, la exportación y la edición filtran por la cuenta
  que inició sesión. Cambiar el número del pago en la URL no abre pagos ajenos.
- **Eliminar pagos y administrar cuentas** (suspender, reactivar, eliminar) es exclusivo del rol
  administrador, que no se puede obtener desde el registro.
- **Verificación en dos pasos obligatoria (TOTP):** sin el código de tu app, la contraseña sola no
  basta para entrar, recuperar la cuenta ni cambiar la contraseña. Un código no se puede usar dos
  veces.
- **Contraseñas con PBKDF2-SHA256** (600.000 iteraciones y sal única por cuenta).
- **Bloqueo temporal:** 5 intentos fallidos seguidos bloquean la cuenta 5 minutos, en la web y en
  el escritorio a la vez.
- **Sesión con JWT en cookie `HttpOnly`, `Secure` y `SameSite=Lax`**, válida por 8 horas, que solo
  se emite después del código de verificación.
- **Reglas de negocio en la base:** el inicio de sesión y las operaciones pasan por
  procedimientos almacenados de Oracle con auditoría, y las consultas usan parámetros (sin
  concatenar texto del usuario en el SQL).
- **Conexión cifrada a la base:** Oracle Autonomous Database exige TLS con Wallet.
- **Plantillas con escape automático** (Jinja2).
- **Secretos fuera del repositorio:** credenciales y Wallet de Oracle en la configuración de Cloud
  Run.

## 6. Agradecimientos

A mi familia, por su apoyo incondicional, su paciencia y por creer en cada proyecto incluso cuando apenas era una idea. Este trabajo también es de ustedes.

Y a ti, que usas esta plataforma: cada prueba, comentario y sugerencia ayuda a que siga mejorando.
