# 📘 Manual de usuario · PRPagos

Guía para usar la versión web paso a paso: qué significa cada campo, qué datos se ingresan y qué
protege la plataforma. La versión de escritorio usa las mismas cuentas, campos y datos (ver
[`escritorio/README.md`](../escritorio/README.md)). Para la parte técnica vuelve al
[README](../README.md).

**Demo web:** [PRPagos (Google Cloud Run)](https://prpagos-web-1087929107584.southamerica-east1.run.app)

## Contenido

1. [En pocas palabras](#1-en-pocas-palabras)
2. [Antes de empezar](#2-antes-de-empezar)
3. [Paso a paso](#3-paso-a-paso)
4. [Qué información ingresas y dónde queda](#4-qué-información-ingresas-y-dónde-queda)
5. [Seguridad: qué se garantiza](#5-seguridad-qué-se-garantiza)
6. [Límites y recomendaciones](#6-límites-y-recomendaciones)
7. [Preguntas frecuentes](#7-preguntas-frecuentes)

---

## 1. En pocas palabras

Simulador de registro de pagos: cada persona anota sus pagos (monto, fecha y concepto), los
consulta con filtros, los modifica y los exporta a CSV. Las reglas de negocio viven en
procedimientos almacenados de Oracle, compartidos por la versión web y la de escritorio.

## 2. Antes de empezar

- Solo necesitas un navegador; también se puede **instalar como app** (PWA) desde el navegador.
- El **usuario** debe tener forma de correo terminado en `@EAN.com`. Es una regla heredada de la
  aplicación original: **no es un buzón real** y nunca se le envía nada.

## 3. Paso a paso

### 3.1 Portada

![Portada de PRPagos](img/manual/01-portada.png)

Desde la portada eliges **Crear cuenta** o **Ya tengo cuenta**.

### 3.2 Crear una cuenta

![Formulario de registro con sus campos numerados](img/manual/02-registro.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Usuario (correo @EAN.com) | Tu usuario de acceso | Debe terminar en `@EAN.com` y ser único |
| 2 | Nombre completo | Tu nombre | Se usa también para recuperar la contraseña |
| 3 | Contraseña | Tu contraseña | Obligatoria |
| 4 | Crear usuario | Botón | Crea la cuenta; sirve en web y en escritorio |

### 3.3 Iniciar sesión

![Formulario de inicio de sesión](img/manual/03-login.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Usuario | Tu usuario `@EAN.com` |
| 2 | Contraseña | Tu contraseña |
| 3 | Ingresar | Abre **Consultar registros**. La sesión dura hasta 8 horas |
| 4 | Olvidé mi contraseña | Abre la recuperación (ver 3.7) |

### 3.4 Registrar un pago

![Formulario de nuevo registro](img/manual/04-nuevo.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Monto | Valor del pago, sin puntos ni signo `$` | Número válido |
| 2 | Fecha | Día del pago | Obligatoria |
| 3 | Concepto | Para qué fue el pago | Obligatorio |
| 4 | Guardar | Botón | Registra el pago a tu nombre |

### 3.5 Consultar y exportar

![Consulta de registros con filtros](img/manual/05-consulta.png)

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

### 3.6 Modificar un pago

![Formulario de modificación](img/manual/06-editar.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Monto | El valor corregido |
| 2 | Fecha | La fecha corregida |
| 3 | Concepto | El concepto corregido |
| 4 | Guardar cambios | Aplica la corrección |

### 3.7 Recuperar la contraseña

![Formulario de recuperación de contraseña](img/manual/07-recuperar.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Correo (@EAN.com) | Tu usuario |
| 2 | Nombre registrado | El nombre completo con el que te registraste (no distingue mayúsculas) |
| 3 | Nueva contraseña | La contraseña nueva |
| 4 | Actualizar contraseña | Aplica el cambio |

## 4. Qué información ingresas y dónde queda

| Dato | Para qué se usa | Dónde se guarda | Quién lo ve |
|---|---|---|---|
| Usuario y nombre | Identificar tu cuenta y recuperar la contraseña | Oracle Autonomous Database (tabla `TBPLogin`) | Tú y el administrador |
| Contraseña | Iniciar sesión | Oracle, solo su huella SHA-256 | Nadie la ve en texto plano |
| Pagos (monto, fecha, concepto) | Tu registro de pagos | Oracle, marcados con tu cuenta | Tú y el administrador |
| Sesión | Mantenerte conectado | Cookie en tu navegador | Firmada, invisible para JavaScript, vence a las 8 horas |

## 5. Seguridad: qué se garantiza

Comprobado en el código del proyecto (`web/app/seguridad.py`, `routers/auth.py`,
`routers/registros.py`, `repositorio.py`):

- **Cada quien ve solo sus pagos:** la consulta, la exportación y la edición filtran por la cuenta
  que inició sesión. Cambiar el número del pago en la URL no abre pagos ajenos.
- **Eliminar pagos y administrar cuentas** (suspender, reactivar, eliminar) es exclusivo del rol
  administrador, que no se puede obtener desde el registro.
- **Sesión con JWT en cookie `HttpOnly` y `SameSite=Lax`**, válida por 8 horas.
- **Reglas de negocio en la base:** el inicio de sesión y las operaciones pasan por
  procedimientos almacenados de Oracle con auditoría, y las consultas usan parámetros (sin
  concatenar texto del usuario en el SQL).
- **Conexión cifrada a la base:** Oracle Autonomous Database exige TLS con Wallet.
- **Plantillas con escape automático** (Jinja2).
- **Secretos fuera del repositorio:** credenciales y Wallet de Oracle en la configuración de Cloud
  Run.

## 6. Límites y recomendaciones

PRPagos reproduce a propósito una aplicación académica original, y conserva algunas de sus
decisiones para que web y escritorio compartan las mismas cuentas. Por eso:

- **La recuperación de contraseña solo pide el usuario y el nombre registrado.** Quien conozca
  ambos datos puede cambiar tu contraseña. No uses tu nombre real ni una contraseña que uses en
  otros sitios.
- **La contraseña se guarda con SHA-256 sin sal**, compatible con la versión de escritorio en Java.
  En un sistema real lo correcto sería bcrypt o PBKDF2 (el propio código lo indica).
- No hay reglas de longitud mínima ni bloqueo por intentos fallidos.
- La recuperación indica si un usuario existe o no.
- **Registra solo datos de ejemplo**: es un simulador, no un sistema de pagos real.

## 7. Preguntas frecuentes

**¿Tengo que tener un correo de la EAN?** No. Basta con que el usuario termine en `@EAN.com`; no se
envía ningún correo.

**¿Lo que registro en la web aparece en la app de escritorio?** Sí. Ambas usan la misma base de
datos y las mismas cuentas.

**¿Puedo eliminar un pago?** No desde una cuenta normal; corrígelo con **Modificar**. Eliminar está
reservado al administrador.
