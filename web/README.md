# PRPagos Web

Versión web de PRPagos, construida con FastAPI. Habla con la **misma** base
de datos, el **mismo** Wallet de Oracle y los **mismos** procedimientos
almacenados que la versión de escritorio en `escritorio/` — no es una
reescritura de las reglas de negocio, es la misma aplicación expuesta como
páginas web en vez de ventanas Swing, pensada para desplegarse en la nube
(OCI).

Ver `../CHANGELOG-migracion.md` para el detalle completo de la migración.

## Pantallas

- Ingresar / Crear usuario / Recuperar contraseña
- Inicio (menú)
- Consultar pagos (filtros por concepto, monto y rango de fechas, exportar a CSV)
- Ingresar pago
- Modificar pago (solo si el pago pertenece al usuario logueado)

## Requisitos

- Python 3.11 o superior
- El Wallet de Oracle en `../wallet` (ya está en el repo, compartido con la
  versión de escritorio)
- Credenciales de la Autonomous Database

## Configuración

```bash
cd web
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/Mac: source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Editar `.env` con la contraseña de la base y un `JWT_SECRET` propio
(se puede generar con `python -c "import secrets; print(secrets.token_urlsafe(48))"`).

## Ejecución local

```bash
uvicorn app.main:app --reload
```

Abrir `http://localhost:8000`.

## Despliegue en OCI

La app es un servicio FastAPI/uvicorn sin estado (la sesión vive en una
cookie JWT, no en memoria del proceso), así que corre igual detrás de
cualquiera de estas opciones:

- **OCI Container Instances**: empaquetar con un `Dockerfile` simple
  (`python:3.11-slim` + `pip install -r requirements.txt` +
  `uvicorn app.main:app --host 0.0.0.0 --port 8080`) y montar `wallet/` como
  parte de la imagen o como volumen.
- **OCI Compute (VM.Standard.E2.1.Micro, capa gratuita)**: mismo patrón que
  el resto del portafolio (Docker + reverse proxy con Caddy/nip.io para
  TLS automático).

En ambos casos las variables de `.env` se pasan como variables de entorno
del contenedor/servicio, nunca como archivo commiteado.

## Seguridad

- Las contraseñas se validan con el mismo hash SHA-256 que ya usa la tabla
  `TBPLogin` desde la versión de escritorio, para que un usuario pueda
  iniciar sesión desde cualquiera de las dos interfaces sin migrar datos.
- La sesión web usa un JWT en una cookie `httponly`.
- Un usuario solo puede ver y modificar sus propios pagos; esa regla vive
  en los procedimientos almacenados (`buscar_tbpagos`, `actualizar_tbpago`),
  no se reimplementa en la capa web.
