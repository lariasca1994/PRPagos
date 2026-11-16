# Simulador de pagos · versión de escritorio

Aplicación de Windows en Java Swing (FlatLaf y JFreeChart) del [Simulador de pagos](../README.md).
Se publica como **PRPagos.exe** con Java incluido, en instalador o versión portable, y se actualiza
sola. No guarda datos ni claves: todo lo pide al servidor de la versión web.

> 📘 **[Manual de usuario](../docs/MANUAL-ESCRITORIO.md)**: cómo usar cada pantalla, paso a paso y con imágenes.

**Descargar:** [última versión](https://github.com/lariasca1994/PRPagos/releases/latest).

## Estructura

```
src/main/java/com/prpagos/
├── ui/                Ventanas y componentes
├── cliente/           Acceso a la API del servidor
└── actualizaciones/   Actualización automática
lib/                   Bibliotecas (FlatLaf, JFreeChart, Gson)
resources/             Íconos e imágenes
empaquetar.ps1         Arma PRPagos.exe (y el instalador)
```

## Compilar y ejecutar

Con JDK 17 o superior, desde `escritorio/` (PowerShell):

```powershell
javac --release 17 -encoding UTF-8 -d bin -cp "lib\*" (Get-ChildItem -Recurse -Path src -Filter *.java | % FullName)
java --enable-native-access=ALL-UNNAMED -cp "bin;lib\*" com.prpagos.ui.VentanaLogin
```

Por defecto usa la web de producción; para probarla con la web local, ejecútala con
`PRPAGOS_URL=http://localhost:8000`.

Para armar el ejecutable: `.\empaquetar.ps1` (portable) o `.\empaquetar.ps1 -Instalador`
(requiere Inno Setup 6).

## Autor

**Luis F. A.** · [GitHub](https://github.com/lariasca1994) · [LinkedIn](https://linkedin.com/in/lfac1) ·
Licencia [MIT](../LICENSE)
