# Empaqueta PRPagos Escritorio como aplicación de Windows con su propio
# PRPagos.exe y Java incluido (no hace falta instalar Java ni compilar nada).
# Lo usa el workflow .github/workflows/escritorio.yml y sirve igual en local.
#
# Uso (PowerShell, parado en escritorio/):
#   .\empaquetar.ps1                         # carpeta portable: build\dist\PRPagos\PRPagos.exe
#   .\empaquetar.ps1 -Instalador             # además, instalador .exe con acceso directo
#   .\empaquetar.ps1 -Version 1.2.0
#
# Requiere un JDK 17 o superior con jpackage (incluido en el JDK). El
# instalador necesita además WiX Toolset (lo tienen los runners de Windows de
# GitHub Actions).
param(
    [string]$Version = "1.0.0",
    [switch]$Instalador
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$build = Join-Path $PSScriptRoot "build"
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
$clases = Join-Path $build "clases"
$entrada = Join-Path $build "jar"
New-Item -ItemType Directory -Force $clases, $entrada | Out-Null

# 1. Compilar (Java 17 como mínimo, igual que el README).
$fuentes = Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d $clases -cp "lib/*" $fuentes
if ($LASTEXITCODE -ne 0) { throw "Falló la compilación" }

# 2. Recursos dentro del .jar: consultas y plantillas compartidas con la web, e ícono.
Copy-Item -Recurse (Join-Path $PSScriptRoot "..\compartido") (Join-Path $clases "compartido")
New-Item -ItemType Directory -Force (Join-Path $clases "recursos") | Out-Null
Copy-Item "resources\icono.png" (Join-Path $clases "recursos\icono.png")

# 3. El .jar de la aplicación y sus librerías.
& jar --create --file (Join-Path $entrada "prpagos-escritorio.jar") --main-class com.prpagos.ui.VentanaLogin -C $clases .
if ($LASTEXITCODE -ne 0) { throw "Falló la creación del jar" }
Copy-Item "lib\*.jar" $entrada

# 4. jpackage: PRPagos.exe con el ícono de la app y un Java propio.
$comunes = @(
    "--name", "PRPagos",
    "--app-version", $Version,
    "--description", "PRPagos: control de ingresos y gastos con plan 50/30/20",
    "--vendor", "PRPagos",
    "--input", $entrada,
    "--main-jar", "prpagos-escritorio.jar",
    "--main-class", "com.prpagos.ui.VentanaLogin",
    "--icon", "resources\icono.ico",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "-Dfile.encoding=UTF-8",
    "--dest", (Join-Path $build "dist")
)
& jpackage --type app-image @comunes
if ($LASTEXITCODE -ne 0) { throw "Falló jpackage (app-image)" }
Write-Host "Listo: $(Join-Path $build 'dist\PRPagos\PRPagos.exe')"

if ($Instalador) {
    # Instalador por usuario (sin permisos de administrador), con acceso directo en
    # el escritorio y en el menú Inicio.
    & jpackage --type exe @comunes --win-per-user-install --win-shortcut --win-menu --win-menu-group "PRPagos" `
        --win-upgrade-uuid "6f1d1f0e-6a3b-4c43-9a6c-2b6a7d3f9c11"
    if ($LASTEXITCODE -ne 0) { throw "Falló jpackage (instalador)" }
    Write-Host "Instalador: $(Get-ChildItem (Join-Path $build 'dist') -Filter *.exe | Select-Object -First 1 -ExpandProperty FullName)"
}
