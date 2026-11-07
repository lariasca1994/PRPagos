# Arma el paquete MSIX para Microsoft Store a partir de la aplicación ya empaquetada
# (build\dist\PRPagos, ver empaquetar.ps1). El paquete sale SIN firmar: Microsoft lo
# firma al publicarlo en la Store. Lo usa .github/workflows/escritorio.yml solo cuando
# existen las variables del repositorio de la Store (ver docs/STORE.md).
#
# Uso (PowerShell, parado en escritorio/, después de empaquetar.ps1):
#   .\msix.ps1 -Version 1.0.30 -IdentityName "<Package/Identity/Name>" `
#              -Publisher "CN=..." -PublisherDisplayName "<nombre del editor>"
#
# Requiere makeappx.exe del Windows SDK (viene en los runners de Windows de GitHub).
param(
    [Parameter(Mandatory)] [string]$Version,
    [Parameter(Mandatory)] [string]$IdentityName,
    [Parameter(Mandatory)] [string]$Publisher,
    [Parameter(Mandatory)] [string]$PublisherDisplayName,
    # Nombre reservado en Partner Center; el manifiesto debe usarlo exacto.
    [string]$DisplayName = "Simulador de pagos",
    [string]$Origen = "build\dist\PRPagos",
    [string]$Destino = "build\dist"
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($Version -notmatch '^\d{1,5}\.\d{1,5}\.\d{1,5}$') { throw "Versión inválida: $Version (se espera x.y.z)" }
if ($Publisher -notmatch '^CN=') { throw "STORE_PUBLISHER debe empezar por CN= (cópialo exacto de Partner Center)" }
if (-not (Test-Path (Join-Path $Origen "PRPagos.exe"))) { throw "No está $Origen\PRPagos.exe: corre antes empaquetar.ps1" }

# makeappx.exe más reciente del Windows SDK (x64).
$makeappx = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin" -Recurse -Filter makeappx.exe |
    Where-Object { $_.Directory.Name -eq 'x64' } | Sort-Object { [version]$_.Directory.Parent.Name } |
    Select-Object -Last 1
if (-not $makeappx) { throw "No se encontró makeappx.exe (Windows SDK)" }

$carpeta = Join-Path $PSScriptRoot "build\msix"
if (Test-Path $carpeta) { Remove-Item -Recurse -Force $carpeta }
New-Item -ItemType Directory -Force $carpeta | Out-Null
Copy-Item -Recurse (Join-Path $Origen "*") $carpeta

# Marcador del canal: la aplicación lo lee para no descargar ni instalar actualizaciones
# por su cuenta (en la Store las instala Microsoft Store). Ver Actualizador.canalStore().
[IO.File]::WriteAllText((Join-Path $carpeta "canal-store.txt"), "microsoft-store`n")

# Logos desde el ícono de la aplicación, en los tamaños que pide el manifiesto.
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $carpeta "Assets"
New-Item -ItemType Directory -Force $assets | Out-Null
$icono = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot "resources\icono.png"))
try {
    foreach ($logo in @(
        @{ Nombre = "StoreLogo.png"; Lado = 50 },
        @{ Nombre = "Square44x44Logo.png"; Lado = 44 },
        @{ Nombre = "Square44x44Logo.targetsize-44_altform-unplated.png"; Lado = 44 },
        @{ Nombre = "Square150x150Logo.png"; Lado = 150 }
    )) {
        $lado = $logo.Lado
        $imagen = New-Object System.Drawing.Bitmap $lado, $lado
        $g = [System.Drawing.Graphics]::FromImage($imagen)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.DrawImage($icono, 0, 0, $lado, $lado)
        $g.Dispose()
        $imagen.Save((Join-Path $assets $logo.Nombre), [System.Drawing.Imaging.ImageFormat]::Png)
        $imagen.Dispose()
    }
} finally {
    $icono.Dispose()
}

function Xml([string]$texto) { [Security.SecurityElement]::Escape($texto) }

$manifiesto = @"
<?xml version="1.0" encoding="utf-8"?>
<Package
  xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
  xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
  xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
  IgnorableNamespaces="uap rescap">
  <Identity Name="$(Xml $IdentityName)" Publisher="$(Xml $Publisher)" Version="$Version.0" ProcessorArchitecture="x64" />
  <Properties>
    <DisplayName>$(Xml $DisplayName)</DisplayName>
    <PublisherDisplayName>$(Xml $PublisherDisplayName)</PublisherDisplayName>
    <Logo>Assets\StoreLogo.png</Logo>
  </Properties>
  <Dependencies>
    <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
  </Dependencies>
  <Resources>
    <Resource Language="es" />
  </Resources>
  <Applications>
    <Application Id="SimuladorDePagos" Executable="PRPagos.exe" EntryPoint="Windows.FullTrustApplication">
      <uap:VisualElements DisplayName="$(Xml $DisplayName)"
        Description="Control de ingresos y gastos con plan 50/30/20, dashboard y res&#250;menes por correo."
        BackgroundColor="transparent" Square150x150Logo="Assets\Square150x150Logo.png"
        Square44x44Logo="Assets\Square44x44Logo.png" />
    </Application>
  </Applications>
  <Capabilities>
    <Capability Name="internetClient" />
    <rescap:Capability Name="runFullTrust" />
  </Capabilities>
</Package>
"@
[IO.File]::WriteAllText((Join-Path $carpeta "AppxManifest.xml"), $manifiesto, (New-Object Text.UTF8Encoding $false))

New-Item -ItemType Directory -Force $Destino | Out-Null
$salida = Join-Path (Resolve-Path $Destino) "Simulador-de-pagos-$Version.msix"
& $makeappx.FullName pack /d $carpeta /p $salida /o
if ($LASTEXITCODE -ne 0) { throw "makeappx no pudo armar el paquete (revisa el manifiesto)" }
Write-Host "Paquete listo (sin firmar): $salida"
