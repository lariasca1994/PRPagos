# 📘 Manual de usuario · PRPagos Escritorio

Guía para instalar y usar la **aplicación de escritorio** para Windows paso a paso: qué significa
cada campo, qué datos se ingresan y qué protege la aplicación. La versión web tiene su propio
manual ([MANUAL-WEB.md](MANUAL-WEB.md)); las dos usan las mismas cuentas y los mismos datos. Para
la parte técnica vuelve al [README del escritorio](../escritorio/README.md).

**Descarga:** [última versión en GitHub Releases](https://github.com/lariasca1994/PRPagos/releases/latest)

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

Gestor de presupuesto personal: registras tus **ingresos y gastos** por categoría, los comparas
con el **plan 50/30/20** (50 % necesidades, 30 % deseos, 20 % ahorro y deudas) en un dashboard con
gráficas, y recibes un **resumen por correo** cada viernes (la semana) y cada día 29 (el mes; en
febrero, el último día). Funciona y se ve igual que la versión web: los datos, las reglas y los
mensajes vienen del mismo servidor, así que lo que ves aquí es exactamente lo que ves en la web.

### Alcance: qué necesitas y qué esperar

- **Windows 10 u 11.** No hace falta instalar Java ni compilar nada: la aplicación lo trae incluido.
- **Nada que configurar:** se instala y se usa. La aplicación no guarda claves ni se conecta a la
  base: pide todo a PRPagos por internet, como el navegador.
- Una **cuenta** (la misma de la web) y una **app autenticadora** en el celular (Google
  Authenticator, Microsoft Authenticator, Authy…).

### Límites

- **Registra solo datos de ejemplo**: es un proyecto de portafolio, no un servicio financiero.
- Necesita internet: los datos viven en el servidor, no en el equipo.

## 2. Glosario

| Término | Qué significa |
|---|---|
| **Plan 50/30/20** | Regla para repartir los ingresos del mes: 50 % a necesidades, 30 % a deseos y 20 % a ahorro y pago de deudas. |
| **Categoría** | Clasificación de un ingreso o gasto (Mercado, Vivienda, Salario…). Cada categoría de gasto pertenece a un grupo del plan. |
| **Balance** | Ingresos menos gastos del periodo. |
| **Dashboard** | Pantalla de resumen con cifras y gráficas del mes. |
| **Release** | Versión publicada de la aplicación en GitHub, lista para descargar. |
| **Instalador / portable** | El instalador crea accesos directos; la versión portable se descomprime y se abre sin instalar. |
| **API** | Puerta de entrada del servidor: la aplicación le pide los datos y el servidor responde con los mismos que muestra la web. |
| **Huella SHA-256** | "Firma" de un archivo: si cambia un solo byte, cambia la huella. Se usa para comprobar que una actualización es la publicada. |
| **Base de datos** | Lugar donde la aplicación guarda la información de forma ordenada y permanente. |
| **Cifrado / TLS** | Protección que codifica la información mientras viaja, para que nadie en el camino pueda leerla. |
| **Llave de acceso (passkey)** | Forma de ingresar sin contraseña ni código: Windows Hello la desbloquea con tu PIN, huella o rostro. La huella nunca sale del equipo. |
| **Código de respaldo** | Código de un solo uso (formato `ABCD-EFGH`) para entrar si no tienes el teléfono. |
| **CSV** | Archivo de texto con datos en columnas, que se abre en Excel o Google Sheets. |
| **TOTP (código de verificación)** | Código de 6 dígitos que cambia cada 30 segundos en tu app autenticadora; se pide además de la contraseña. |
| **Sesión** | El tiempo en que la aplicación te reconoce después de iniciar sesión, sin pedirte la contraseña de nuevo. |

## 3. Plataformas, base de datos y servicios

| Plataforma | Tipo | Qué hace en el proyecto |
|---|---|---|
| ![Java](https://img.shields.io/badge/Java_Swing-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white) | Aplicación de escritorio | La aplicación (Java Swing, FlatLaf y JFreeChart) corre en tu equipo con su propio Java incluido. |
| ![Google Cloud Run](https://img.shields.io/badge/Google_Cloud_Run-4285F4?style=for-the-badge&logo=googlecloud&logoColor=white) | Servidor | La web de PRPagos y su API: la aplicación le pide todos los datos, las cifras del dashboard y los mensajes. |
| ![Oracle](https://img.shields.io/badge/Oracle_DB-F80000?style=for-the-badge&logo=oracle&logoColor=white) | Base de datos | Oracle Autonomous Database guarda cuentas, registros, categorías y planes. Solo el servidor se conecta a ella. |
| ![Brevo](https://img.shields.io/badge/Brevo-0B996E?style=for-the-badge&logo=brevo&logoColor=white) | Correo | El servidor envía con Brevo los resúmenes que pides y los códigos de recuperación. |
| ![GitHub](https://img.shields.io/badge/GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white) | Distribución | Publica cada versión (instalador y portable) y de ahí se descargan las actualizaciones automáticas. |

## 4. Paso a paso

En las tablas, `#` es el orden en que aparece cada campo en la pantalla, de arriba hacia abajo.

### 4.1 Instalar

1. Entra a la [última versión](https://github.com/lariasca1994/PRPagos/releases/latest) y descarga
   una de las dos opciones:
   - **`PRPagos-<versión>.exe`** (instalador): ábrelo y se instala solo, sin preguntas ni permisos de
     administrador. Crea el acceso directo **PRPagos** en el escritorio y en el menú Inicio y abre la
     aplicación al terminar. Si Windows muestra "Windows protegió su PC", pulsa **Más información →
     Ejecutar de todas formas** (el instalador es gratuito y no lleva firma de pago).
   - **`PRPagos-Escritorio-<versión>.zip`** (portable): descomprímelo y abre `PRPagos.exe`.
2. Abre **PRPagos** e inicia sesión con tu cuenta (la misma de la web) o crea una.

No hay nada más que copiar ni configurar, en este equipo ni en otro (por ejemplo, una tablet con
Windows): basta instalarla. Las versiones nuevas se instalan solas (ver 4.16).

**Desinstalar:** en **Configuración → Aplicaciones → Aplicaciones instaladas → PRPagos →
Desinstalar**, o con **Desinstalar PRPagos** en el menú Inicio. Se borran la aplicación, sus
registros de uso y sus preferencias; tu cuenta y tus datos siguen en la web.

### 4.2 Portada

![Portada de PRPagos Escritorio](img/manual-escritorio/01-portada.png)

Al abrir PRPagos aparece primero una **animación de entrada** de 1,4 segundos, la misma de la app
web instalada: sube el recibo, se escriben sus líneas, aparece el escudo con el check y luego el
nombre. Después se abre la portada.

La portada es el inicio de sesión (paso 1: correo y contraseña), con la misma animación de la web.

**Tablet o ventana angosta:** la aplicación se acomoda sola al tamaño de la ventana, igual que la
web. Con la tablet de pie (o una ventana de menos de 1000 px de ancho), la portada pone la marca
arriba y el formulario debajo; en las demás pantallas el menú baja a una segunda fila y las tarjetas
y gráficas pasan a dos columnas o a una sola, sin cortarse ni necesitar barra horizontal.
Desde ahí eliges **Regístrate** u **Olvidé mi contraseña**. El botón ◐ cambia entre tema claro y
oscuro.

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Correo | Tu correo |
| 2 | Contraseña | Tu contraseña (el ojo la muestra u oculta) |
| 3 | Continuar | Pasa al código de verificación |
| 4 | Ingresar con llave de acceso | Entra directo con Windows Hello (PIN, huella o rostro), sin contraseña ni código (ver 4.17) |

### 4.3 Crear una cuenta

![Formulario de registro](img/manual-escritorio/02-registro.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Correo | Tu correo real; es tu usuario | Forma `nombre@dominio.com` y único |
| 2 | Nombre completo | Tu nombre | Se usa también para recuperar la contraseña |
| 3 | Contraseña | Tu contraseña | Mínimo 8 caracteres |
| 4 | Repite la contraseña | La misma | Debe coincidir |
| 5 | Crear cuenta | Botón | Crea la cuenta; sirve en el escritorio y en la web |

### 4.4 Configurar la verificación en dos pasos (solo la primera vez)

La primera vez eliges cómo recibir el código de la verificación en dos pasos (es obligatoria):

| Método | Cómo funciona |
|---|---|
| **App autenticadora** (recomendado) | Escaneas el código QR con Google Authenticator, Microsoft Authenticator u otra app; la app muestra un código nuevo cada 30 segundos, incluso sin internet. Los pasos están abajo. |
| **Código por correo** | Te llega un código de 6 dígitos que vence en pocos minutos y sirve una sola vez. Escríbelo y pulsa **Activar e ingresar**; recibes tus **códigos de respaldo**. |

En **Perfil → Verificación en dos pasos** agregas el método que no elegiste y escoges cuál se pide
por defecto; con los dos activos, la pantalla del código ofrece **Usar un código por correo** o
**Usar la app autenticadora**. Es la misma cuenta en la web y en el escritorio.


![Configuración del autenticador con código QR](img/manual-escritorio/03-configurar.png)

| # | Campo | Qué hacer |
|:-:|---|---|
| 1 | Código QR | Escanéalo con tu app autenticadora |
| 2 | Clave | La misma información, para escribirla a mano si no puedes escanear |
| 3 | Código de 6 dígitos | El que muestra la app para **PRPagos** |
| 4 | Activar e ingresar | Guarda el autenticador y muestra tus **códigos de respaldo** (ver 4.13) |

Si ya lo configuraste en la web, este paso no aparece.

### 4.5 Iniciar sesión (paso 2: código)

![Código de verificación](img/manual-escritorio/04-codigo.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Código de 6 dígitos | El código actual de tu app (cambia cada 30 segundos) o un código de respaldo |
| 2 | Ingresar | Abre el **Dashboard** |
| 3 | Perdí mi teléfono | Te envía un código al correo (ver 4.14) |

La sesión se cierra sola tras un tiempo **sin uso** o al cumplir su **duración máxima**, con un mensaje que
explica el motivo, igual que en la web.

### 4.6 Dashboard

![Dashboard con cifras y gráficas del mes](img/manual-escritorio/05-dashboard.png)

| # | Parte | Qué muestra |
|:-:|---|---|
| 1 | Selector de mes | El mes que quieres revisar (por defecto, el actual) |
| 2 | Ingresos · Gastos · Balance | Totales del mes; el balance sale en verde si es positivo y en rojo si es negativo |
| 3 | Ahorro del mes | Qué porcentaje de los ingresos no se gastó, y cuántos movimientos hubo |
| 4 | Ingresos vs. gastos | Barras de los últimos 6 meses, para ver la tendencia; **toca un mes** para abrirlo |
| 5 | Gastos por categoría | Anillo con el peso de cada categoría en el mes; **toca una** para ver sus registros |
| 6 | Plan 50/30/20 | Presupuesto de cada grupo (calculado sobre los ingresos del mes) frente a lo real. **Ver el presupuesto detallado por categoría** abre la pantalla Presupuesto en ese mes |
| 7 | Tabla del plan | Presupuesto, real, % usado y estado: **Dentro del plan**, **Excedido**, **Meta cumplida** o **Por debajo de la meta** (ahorro) |
| 8 | Resúmenes por correo | **Enviarme el resumen semanal** o **del mes** para recibirlo al momento |

Las cifras, los colores y las gráficas son los mismos de la web. En tema oscuro:

![Dashboard en tema oscuro](img/manual-escritorio/06-dashboard-oscuro.png)

### 4.7 Presupuesto

Planea cuánto vas a recibir y gastar **en cada categoría, mes a mes**, y compáralo con lo que
registras. Es el mismo esquema de una hoja de presupuesto anual (ingresos, gastos fijos y
variables, ahorro y lo que **queda**), pero conectado a tus registros: no tienes que pasar nada a
mano.

| # | Parte | Qué muestra o qué haces |
|:-:|---|---|
| 1 | Mes y año | El periodo que planeas (año anterior, actual o siguiente) |
| 2 | Ingresos · Gastos | Lo **registrado** en el mes y, debajo, lo **presupuestado** |
| 3 | Quedan | Ingresos menos gastos (el ahorro cuenta como gasto: ya tiene destino), real y presupuestado |
| 4 | Presupuesto de gastos usado | Qué porcentaje del presupuesto de gastos ya se usó y si vas **Dentro del presupuesto** o **Excedido** |
| 5 | Gastos presupuestados vs. reales | Barras de los 12 meses; **toca un mes** para abrirlo |
| 6 | Distribución del presupuesto | Cómo se reparten los ingresos presupuestados entre Necesidades, Deseos, Ahorro y deudas y lo **sin asignar** |
| 7 | Mayores gastos | Los 5 gastos más grandes del mes y su peso; **toca uno** para abrirlo, o **Ver todos los registros del mes** |
| 8 | Tabla del mes | Una fila por categoría, agrupadas como el plan 50/30/20. Escribe el monto en **Presupuesto** (con o sin puntos de miles; vacío = sin presupuesto). **Real**, **Usado** (con barra) y **Estado** salen de tus registros. **Toca el nombre** de una categoría para ver sus movimientos del mes |
| 9 | Meta del plan | En cada grupo: qué parte de los ingresos presupuestados le diste frente a la meta de tu plan 50/30/20 |
| 10 | Aplicar a · Guardar presupuesto | Guarda **solo este mes**, **de este mes a diciembre** o **todo el año** |
| 11 | Copiar el presupuesto del mes anterior | Deja el mes igual al anterior, para ajustar solo lo que cambia |
| 12 | Resumen del año | Presupuestado y real de cada mes (ingresos, gastos y quedan) con el total; **toca un mes** para abrirlo |

![Presupuesto completo: tabla por categoría y resumen del año](img/manual-escritorio/16-presupuesto.png)

Estados: en ingresos, **Cumplido** o **Por debajo de lo previsto**; en gastos, **Dentro del
presupuesto**, **Excedido** o **Sin presupuesto**; en ahorro, **Meta cumplida** o **Por debajo de la
meta**. Al guardar un registro con categoría, el aviso te dice cómo va su presupuesto, por
ejemplo: *Registro guardado · Mercado en septiembre 2026: $ 657.000,00 de $ 900.000,00 (73 %) ·
Dentro del presupuesto*.

### 4.8 Registrar un ingreso o un gasto

![Formulario de nuevo registro](img/manual-escritorio/07-nuevo.png)

| # | Campo | Qué escribir | Reglas |
|:-:|---|---|---|
| 1 | Tipo | **Ingreso** o **Gasto** | Obligatorio |
| 2 | Monto | Valor, sin puntos de miles ni signo `$` (los decimales pueden ir con coma) | Número válido |
| 3 | Fecha | Día del movimiento (con el calendario) | Obligatoria |
| 4 | Categoría | Se filtra según el tipo; las de gasto muestran su grupo del plan | Opcional (**Sin categoría**) |
| 5 | Concepto | Descripción breve | Obligatorio, hasta 100 caracteres |
| 6 | Guardar | Botón | Registra el movimiento a tu nombre |

### 4.9 Consultar y exportar

![Consulta de registros con filtros](img/manual-escritorio/08-consulta.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Tipo | Todos, solo ingresos o solo gastos |
| 2 | Concepto contiene | Parte del concepto que buscas |
| 3 | Monto mínimo / máximo | Rango de valores |
| 4 | Desde / Hasta | Rango de fechas |
| 5 | Categoría | Una categoría, **Sin categoría** o todas |
| 6 | Buscar | Aplica los filtros (**Limpiar** los quita). Un campo vacío no filtra |
| 7 | Totales | Ingresos, gastos y balance de lo que cumple los filtros |
| 8 | Modificar | Abre el registro para corregirlo |
| 9 | Exportar a CSV | Guarda la consulta en un archivo (las mismas columnas que la web) |

Solo ves **tus** registros.

### 4.10 Modificar un registro

![Formulario de modificación](img/manual-escritorio/09-editar.png)

Los mismos campos de 4.8 con los valores actuales. **Guardar cambios** aplica la corrección y queda
en la auditoría.

### 4.11 Perfil

![Pantalla Perfil](img/manual-escritorio/10-perfil.png)

| # | Sección | Qué puedes hacer |
|:-:|---|---|
| 1 | Tus datos | Cambiar tu nombre y activar o desactivar los **resúmenes por correo** |
| 2 | Plan 50/30/20 | Ajustar los porcentajes de Necesidades, Deseos y Ahorro y deudas (deben sumar 100) |
| 3 | Categorías | Ver las sugeridas y **agregar** las tuyas (nombre, tipo y grupo del plan); las tuyas se pueden eliminar si no tienen registros |
| 4 | Verificación en dos pasos | Muestra tus métodos; **Agregar código por correo** (escribe el código que llega y pulsa **Activar código por correo**), **Agregar app autenticadora** o **Cambiar autenticador** (con un código vigente: de la app, del correo o de respaldo), **Pedir … por defecto** y **Enviarme un código por correo** para confirmar cambios |
| 5 | Códigos de respaldo | Cuántos te quedan y **Generar códigos nuevos** (anula los anteriores) |
| 6 | Llaves de acceso | Ver tus dispositivos con llave, **agregar este dispositivo** o eliminar uno (ver 4.17) |
| 7 | Cambiar contraseña | Contraseña actual, la nueva dos veces y un código de la app o de respaldo |

### 4.12 Resúmenes por correo

- **Semanal:** cada viernes, con los 7 días que terminan ese viernes (de sábado a viernes).
- **Mensual:** cada día 29 (en febrero, el último día), con lo que va del mes: totales,
  categorías y el plan 50/30/20.
- Los envía el servidor a las 08:00 (hora de Colombia) aunque la aplicación esté cerrada, a las
  cuentas con los resúmenes activados en **Perfil**.
- Los botones del dashboard te los envían al momento; el correo es idéntico al de la web.

### 4.13 Códigos de respaldo

![Códigos de respaldo](img/manual-escritorio/11-codigos-respaldo.png)

Aparecen al activar el autenticador y cada vez que generas nuevos en **Perfil**. Son 10, cada uno
sirve **una sola vez** y no se vuelven a mostrar: guárdalos impresos o en tu gestor de contraseñas.
Sirven en el escritorio y en la web para ingresar, recuperar la contraseña o cambiar de
autenticador.

### 4.14 Perdí mi teléfono

![Código enviado al correo](img/manual-escritorio/12-perdi-telefono.png)

1. Inicia sesión con tu correo y contraseña.
2. En el paso del código, si tienes un **código de respaldo**, escríbelo y entra.
3. Si no, pulsa **Perdí mi teléfono**: llega a tu correo un código de 6 dígitos que vence en 15
   minutos.
4. Escríbelo en **Código del correo** y pulsa **Verificar y configurar autenticador**: configuras
   la app en el teléfono nuevo y recibes códigos de respaldo nuevos.

### 4.15 Recuperar la contraseña

![Formulario de recuperación de contraseña](img/manual-escritorio/13-recuperar.png)

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Correo | Tu correo |
| 2 | Nombre registrado | El nombre completo de tu cuenta (no distingue mayúsculas) |
| 3 | Código del autenticador o de respaldo | El código actual de tu app o un código de respaldo |
| 4 | Nueva contraseña | Mínimo 8 caracteres |
| 5 | Repite la nueva contraseña | La misma |
| 6 | Actualizar contraseña | Aplica el cambio |

### 4.16 Actualizaciones automáticas

![Aviso de actualización disponible](img/manual-escritorio/14-actualizacion.png)

No tienes que volver a descargar nada: al abrir la aplicación, y luego cada hora, PRPagos revisa si
hay una versión nueva. Si la hay, la descarga en segundo plano, comprueba que sea la publicada y te
avisa:

- **Reiniciar ahora:** instala la versión nueva y vuelve a abrir PRPagos (se cierra la sesión).
- **Al cerrar:** la instala cuando cierres la aplicación; la próxima vez ya abre actualizada.

Si algo falla (sin internet, descarga incompleta, archivo que no coincide), sigues con la versión
que tenías y se reintenta más tarde.

### 4.17 Llaves de acceso (Windows Hello)

![Sección Llaves de acceso en Perfil](img/manual-escritorio/15-llaves.png)

Con una llave de acceso entras sin escribir la contraseña ni el código: Windows Hello te pide tu
PIN, huella o rostro. Requiere Windows 10 u 11 con Windows Hello configurado (**Configuración →
Cuentas → Opciones de inicio de sesión**); si no lo está, el botón aparece desactivado con un aviso.

**Agregar este equipo** (en **Perfil → Llaves de acceso**):

| # | Campo | Qué escribir |
|:-:|---|---|
| 1 | Nombre del dispositivo | Un nombre para reconocerlo (por ejemplo, "Portátil") |
| 2 | Código de la app o de respaldo | Un código vigente, para confirmar que eres tú |
| 3 | Agregar este dispositivo | Windows pide tu PIN, huella o rostro y guarda la llave |

**Ingresar:** en la portada pulsa **Ingresar con llave de acceso** y confirma en la ventana de
Windows Hello. Abre el **Dashboard** directamente.

- La llave es la misma de la web: si la creaste en Edge o Chrome en este Windows, también sirve
  aquí (y al revés). En otro equipo necesitas agregar otra.
- Si cambias de equipo, elimina su llave en **Perfil**. La contraseña + código y los códigos de
  respaldo siguen funcionando.

### 4.18 Preguntas frecuentes

**¿Tengo que configurar el autenticador otra vez si ya lo uso en la web?** No. Es uno solo por
cuenta, igual que los códigos de respaldo.

**¿Lo que registro aquí aparece en la web?** Sí, al instante: las dos piden los datos al mismo
servidor.

**¿Puedo instalarla en otro computador o en una tablet con Windows?** Sí: descárgala e instálala;
no necesita ninguna configuración.

**Windows muestra "Windows protegió su PC" al abrir el instalador.** Pasa con aplicaciones sin
firma comercial: pulsa **Más información → Ejecutar de todas formas**. Descárgalo solo desde la
página de Releases del proyecto.

## 5. Seguridad: tu información protegida

### Qué datos se guardan y dónde

| Dato | Para qué se usa | Dónde se guarda | Quién lo ve |
|---|---|---|---|
| Correo y nombre | Identificar tu cuenta, enviarte los resúmenes y recuperar el acceso | Oracle Autonomous Database | Solo tú |
| Contraseña | Iniciar sesión | Oracle, solo su huella con sal | Nadie la ve en texto plano |
| Clave del autenticador | Verificar tus códigos | Oracle, cifrada | Nadie |
| Códigos de respaldo y código por correo | Recuperar el acceso | Oracle, solo su huella | Nadie los ve después de mostrarlos |
| Ingresos, gastos, categorías y plan | Tu presupuesto | Oracle, marcados con tu cuenta | Solo tú |
| Llaves de acceso | Ingresar con Windows Hello | Oracle, solo la clave pública y un contador | Nadie: el PIN, la huella y la clave privada nunca salen del equipo |
| Sesión | Mantenerte conectado | En memoria, mientras la aplicación está abierta | Nadie; se borra al cerrar |

### Cómo se protege tu información

- **Sin claves en tu equipo:** la aplicación no guarda la contraseña de la base ni ninguna otra
  clave; solo conoce la dirección pública de PRPagos. Copiarla no da acceso a ningún dato.
- **Llaves de acceso con Windows Hello:** exigen la verificación (PIN, huella o rostro) y el
  servidor comprueba cada ingreso.
- **Actualizaciones verificadas:** solo se descargan desde GitHub y se descartan si no son
  exactamente la versión publicada.
- **Cada quien ve solo lo suyo:** dashboard, consulta, exportación, edición y correos filtran por la
  cuenta que inició sesión.
- **Verificación en dos pasos obligatoria**, a elección: app autenticadora (TOTP) o código por correo (vence en pocos minutos y sirve una vez), compartida con la web: un código no se puede
  usar dos veces.
- **Contraseñas protegidas:** solo se guarda su huella con sal, nunca la contraseña.
- **Bloqueo temporal** ante intentos fallidos repetidos, en el escritorio y en la web a la vez.
- **Registro de eventos de seguridad:** ingresos, intentos fallidos, bloqueos y cambios de
  contraseña quedan registrados (nunca contraseñas ni códigos) y se depuran solos.
- **Reglas de negocio en la base:** las operaciones pasan por procedimientos almacenados de Oracle
  con auditoría, y las consultas usan parámetros.
- **Conexión cifrada:** la aplicación habla con el servidor por HTTPS, y el servidor con la base
  por una conexión cifrada.
- **Java incluido al día:** el instalador trae Java con los últimos parches de seguridad y la
  aplicación instalada se actualiza sola.

## 6. Agradecimientos

A mi familia, por su apoyo incondicional, su paciencia y por creer en cada proyecto incluso cuando
apenas era una idea. Este trabajo también es de ustedes.

Y a ti, que usas esta aplicación: cada prueba, comentario y sugerencia ayuda a que siga mejorando.
