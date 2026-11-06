# Firma de código de la aplicación de escritorio

Política de firma del ejecutable de Windows (instalador y aplicación).

## Política

Free code signing provided by [SignPath.io](https://about.signpath.io/), certificate by
[SignPath Foundation](https://signpath.org/).

- **Qué se firma:** solo lo que compila GitHub Actions desde este repositorio público
  (`.github/workflows/escritorio.yml`): `PRPagos.exe` de la aplicación y el instalador
  `Simulador-de-pagos-<versión>.exe`. Nada se firma desde un equipo personal.
- **Roles:**
  - Autor y revisor de los cambios: [lariasca1994](https://github.com/lariasca1994).
  - Aprobación de cada firma: [lariasca1994](https://github.com/lariasca1994).
- **Privacidad:** la aplicación solo se comunica con su propio servidor (la API de la web,
  para la cuenta y los datos que el usuario registra) y con GitHub (para consultar la
  dirección vigente del servidor y descargar actualizaciones verificadas). No envía
  datos a terceros. El detalle está en el
  [manual de escritorio](MANUAL-ESCRITORIO.md#5-seguridad-tu-información-protegida).

## Cómo se activa

El flujo de publicación firma solo si existen estos valores en el repositorio
(**Settings → Secrets and variables → Actions**); sin ellos publica sin firmar.

| Tipo | Nombre | Valor |
|---|---|---|
| Secreto | `SIGNPATH_API_TOKEN` | Token de la cuenta de servicio de CI en SignPath |
| Variable | `SIGNPATH_ORGANIZATION_ID` | Id de la organización en SignPath |
| Variable | `SIGNPATH_PROJECT_SLUG` | Slug del proyecto en SignPath |
| Variable | `SIGNPATH_POLICY_SLUG` | `release-signing` (o el que asigne SignPath) |

En el proyecto de SignPath van dos configuraciones de artefacto, con estos slugs:

**`aplicacion`** (carpeta de la aplicación que arma jpackage):

```xml
<?xml version="1.0" encoding="utf-8"?>
<artifact-configuration xmlns="http://signpath.io/artifact-configuration/v1">
  <zip-file>
    <pe-file path="PRPagos.exe">
      <authenticode-sign />
    </pe-file>
  </zip-file>
</artifact-configuration>
```

**`instalador`** (instalador de Inno Setup):

```xml
<?xml version="1.0" encoding="utf-8"?>
<artifact-configuration xmlns="http://signpath.io/artifact-configuration/v1">
  <zip-file>
    <pe-file path="PRPagos-*.exe">
      <authenticode-sign />
    </pe-file>
  </zip-file>
</artifact-configuration>
```

En el proyecto se conecta el repositorio de GitHub como origen de confianza, para que
SignPath solo acepte artefactos construidos por GitHub Actions desde `main`.