# Empaqueta PRPagos Escritorio como aplicación de Windows con su propio
# PRPagos.exe y Java incluido (no hace falta instalar Java ni compilar nada).
# Lo usa el workflow .github/workflows/escritorio.yml y sirve igual en local.
#
# Uso (PowerShell, parado en escritorio/):
#   .\empaquetar.ps1                         # carpeta portable: build\dist\PRPagos\PRPagos.exe
#   .\empaquetar.ps1 -Instalador             # además, instalador automático con desinstalador
#   .\empaquetar.ps1 -Version 1.2.0
#
# Requiere un JDK 17 o superior con jpackage (incluido en el JDK). El
# instalador necesita además Inno Setup 6 (gratuito; lo instala el workflow en
# el runner de Windows de GitHub Actions). Ver instalador.iss.
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

# 2. Recursos dentro del .jar: ícono y script del actualizador (src/main/resources).
#    No lleva datos, consultas ni claves: todo lo pide a la API de PRPagos.
New-Item -ItemType Directory -Force (Join-Path $clases "recursos") | Out-Null
Copy-Item "resources\icono.png" (Join-Path $clases "recursos\icono.png")
Copy-Item -Recurse "src\main\resources\*" $clases -Force

# 3. El .jar de la aplicación (con su versión en el manifiesto: el actualizador
#    la compara con la publicada) y sus librerías.
$manifiesto = Join-Path $build "MANIFEST.MF"
[IO.File]::WriteAllText($manifiesto, "Implementation-Title: PRPagos Escritorio`nImplementation-Version: $Version`n")
& jar --create --file (Join-Path $entrada "prpagos-escritorio.jar") --manifest $manifiesto --main-class com.prpagos.ui.VentanaLogin -C $clases .
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
    # Instalador automático (sin preguntas, por usuario, sin administrador) con
    # desinstalador en Aplicaciones instaladas y en el menú Inicio: instalador.iss.
    $iscc = (Get-Command iscc.exe -ErrorAction SilentlyContinue).Source
    if (-not $iscc) {
        $iscc = @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe",
            "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
    }
    if (-not $iscc) { throw "Falta Inno Setup 6 (ISCC.exe) para armar el instalador" }
    & $iscc /Q "/DVersion=$Version" (Join-Path $PSScriptRoot "instalador.iss")
    if ($LASTEXITCODE -ne 0) { throw "Falló Inno Setup (instalador)" }
    Write-Host "Instalador: $(Join-Path $build "dist\PRPagos-$Version.exe")"
}
