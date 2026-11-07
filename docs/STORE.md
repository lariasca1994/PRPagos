# Publicar el escritorio en Microsoft Store

Procedimiento para quien publica la aplicación de escritorio (**Simulador de pagos**) en Microsoft
Store con una cuenta individual de desarrollador. Microsoft firma el paquete al publicarlo, así que
el repositorio no necesita certificados ni secretos para esto.

Mientras no existan las variables de abajo, nada cambia: el workflow publica el escritorio en
GitHub Releases como siempre y omite el paquete de la Store.

## 1. Datos de Partner Center

1. En Partner Center, reserva el nombre **Simulador de pagos** (Aplicaciones y juegos → Nueva app).
2. En la app, abre **Administración de productos → Identidad del producto** y copia:
   - `Package/Identity/Name`
   - `Package/Identity/Publisher` (empieza por `CN=`; cópialo exacto)
   - `Package/Properties/PublisherDisplayName`
   - El **Id. de la Store** (12 caracteres, por ejemplo `9N...`).

## 2. Variables del repositorio

En GitHub: **Settings → Secrets and variables → Actions → Variables** (son variables, no secretos):

| Variable | Valor | Efecto |
|---|---|---|
| `STORE_IDENTITY_NAME` | `Package/Identity/Name` | Activa el paquete MSIX (las tres juntas) |
| `STORE_PUBLISHER` | `Package/Identity/Publisher` (`CN=...` exacto) | Identidad del paquete |
| `STORE_PUBLISHER_DISPLAY_NAME` | `PublisherDisplayName` | Nombre del editor en el paquete |
| `STORE_PRODUCT_ID` | Id. de la Store | Enlace del aviso de actualización obligatoria |
| `STORE_VERSION_MINIMA` | `x.y.z` (por ejemplo `1.0.30`) | Versión mínima obligatoria para las instalaciones de la Store |

`STORE_PRODUCT_ID` y `STORE_VERSION_MINIMA` llegan al servidor en el siguiente despliegue de la web
(workflow **Desplegar en Cloud Run**, en un push que toque `web/` o al ejecutarlo desde Actions). Si
faltan o no tienen la forma esperada, la web no las envía.

## 3. Obtener el paquete

1. Con las tres primeras variables creadas, cada push que publique el escritorio arma además
   `Simulador-de-pagos-<versión>.msix` **sin firmar**.
2. Ábrelo en **Actions → Publicar escritorio → (la ejecución) → Artifacts** y descarga
   `microsoft-store-<versión>`. Se guarda 15 días.
3. No va al Release de GitHub: sin firma no se puede instalar con doble clic.

El paquete es la misma aplicación del Release, con el nombre visible "Simulador de pagos", para
Windows 10 (1809) o superior, y un marcador que le indica que se instaló desde la Store.

## 4. Subirlo en Partner Center

1. En la app, crea un **envío** (Submission).
2. **Paquetes:** arrastra el `.msix` descargado (descomprime antes el `.zip` del artefacto).
3. **Ficha de la Store:** descripción, capturas (puedes usar las de `docs/img/manual-escritorio`) y
   la **política de privacidad**:
   `https://github.com/lariasca1994/PRPagos/blob/main/docs/PRIVACIDAD.md`.
4. **Clasificación por edades** y **precio** (gratis), y envía a certificación.

La aplicación pide la capacidad `runFullTrust` (es una aplicación de escritorio completa) e
`internetClient` (habla con la API de la web). Si Partner Center pide justificar `runFullTrust`,
indica que es una aplicación de escritorio Win32/Java empaquetada en MSIX.

## 5. Actualizaciones en el canal Store

- La aplicación instalada desde la Store **no** descarga ni instala versiones por su cuenta: lo
  hace Microsoft Store.
- La actualización sigue siendo **obligatoria**: si la versión instalada es menor que
  `STORE_VERSION_MINIMA`, la aplicación muestra un aviso con una sola opción, **Actualizar en
  Microsoft Store**, que abre la ficha del producto y cierra la aplicación.
- **Cuándo fijar `STORE_VERSION_MINIMA`:** solo **después** de que Microsoft apruebe y publique esa
  versión en la Store. Si se fija antes, las instalaciones verían un aviso que la Store todavía no
  puede cumplir. Para exigir la versión nueva: sube el paquete, espera la publicación, actualiza la
  variable y vuelve a desplegar la web.
- Las instalaciones desde GitHub (instalador o portable) siguen actualizándose solas como siempre.
