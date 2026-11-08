# Política de privacidad · Simulador de pagos

Última actualización: 7 de noviembre de 2026.

Esta política explica qué datos guarda el **Simulador de pagos** (aplicación web y aplicación de
escritorio para Windows, también la que se instala desde Microsoft Store), para qué los usa y cómo
puedes borrarlos. Es un proyecto de portafolio sin fines comerciales.

## 1. Qué datos se guardan

| Dato | Para qué se usa |
|---|---|
| Correo y nombre | Identificar tu cuenta, enviarte los códigos de acceso, el enlace para restablecer la contraseña y los resúmenes por correo que actives |
| Contraseña | Iniciar sesión. Solo se guarda su huella (hash con sal); nadie puede leerla |
| Verificación en dos pasos | La clave de tu app autenticadora (cifrada) y la huella de tus códigos de respaldo y de los códigos enviados por correo |
| Llaves de acceso (Windows Hello, huella, PIN) | Solo la clave pública y un contador. Tu huella, tu rostro, tu PIN y la clave privada nunca salen de tu dispositivo |
| Ingresos, gastos, categorías, plan y presupuestos | Mostrarte tu dashboard, tus informes y tus resúmenes |
| Eventos de seguridad | Ingresos, intentos fallidos, bloqueos y cambios de contraseña: solo el tipo de evento, la cuenta y la fecha. Se borran solos al poco tiempo |

No se recogen datos de ubicación, contactos, fotos, micrófono ni cámara, y no hay publicidad ni
herramientas de analítica o seguimiento.

## 2. Dónde se guardan

- Los datos de la cuenta y del presupuesto se guardan en la base de datos del servicio en la nube
  (Oracle Autonomous Database), con conexión cifrada.
- La aplicación web corre en Google Cloud Run. Los correos se envían con Brevo.
- En tu navegador solo queda la cookie de la sesión (firmada e invisible para JavaScript) y tu
  preferencia de tema claro u oscuro.
- En tu equipo con Windows, la aplicación de escritorio guarda solo tu preferencia de tema, la
  dirección del servidor y un registro técnico de las actualizaciones. No guarda tu contraseña ni
  tus datos financieros: los pide al servidor cada vez.

## 3. Con quién se comparten

Con nadie. Los datos no se venden, no se ceden ni se usan para publicidad. Los proveedores de
infraestructura mencionados (base de datos, alojamiento de la web y envío de correos) solo los
procesan para prestar el servicio. Cada persona ve únicamente sus propios registros.

## 4. Cuánto tiempo se guardan

Mientras tengas tu cuenta. Los códigos enviados por correo y los enlaces para restablecer la
contraseña vencen en poco tiempo y sirven una sola vez; los eventos de seguridad se depuran
automáticamente.

## 5. Tus opciones

- **Ver y corregir tus datos:** en **Perfil** cambias tu nombre, tu contraseña, tu plan, tus
  categorías y tus métodos de verificación; tus registros los editas en **Consultar registros**.
- **Dejar de recibir correos:** desactiva los resúmenes en **Perfil**. Los códigos de acceso y los
  enlaces de recuperación solo llegan cuando tú los pides.
- **Borrar tu cuenta y tus datos:** tú mismo, en **Perfil → Eliminar mi cuenta** (en la web o en
  la aplicación de escritorio), con tu contraseña, un código de tu verificación en dos pasos y la
  palabra `ELIMINAR`. Se eliminan **de forma definitiva e inmediata** la cuenta y todos sus datos:
  registros, categorías, plan, presupuestos, envíos de resúmenes, llaves de acceso, códigos de
  respaldo, enlaces de recuperación y eventos de seguridad. No queda ningún dato que te
  identifique. Se cierran las sesiones y recibes un último correo de confirmación.
- **Desinstalar la aplicación de escritorio:** desde **Configuración → Aplicaciones → Aplicaciones
  instaladas**. Se borran la aplicación y sus preferencias del equipo; tu cuenta sigue en la web
  hasta que pidas borrarla.

## 6. Menores de edad

El servicio no está dirigido a menores de 13 años.

## 7. Cambios

Si esta política cambia, la versión vigente es la publicada en esta página, con su fecha de
actualización.
