# Instala una versión nueva de PRPagos Escritorio ya descargada y verificada
# (lo lanza Actualizador.java). Espera a que la aplicación se cierre, deja la
# carpeta instalada como respaldo (.anterior), pone la nueva en su lugar y,
# si algo falla, restaura la anterior: la versión instalada nunca se pierde.
# Si se instaló con el instalador (instalador.iss), conserva su desinstalador y
# deja la versión nueva en Configuración > Aplicaciones instaladas.
param(
    [int]$Proceso,
    [string]$Instalada,
    [string]$Nueva,
    [string]$Log,
    [string]$Abrir = "0",
    [string]$Version = ""
)
# El mismo AppId de instalador.iss (Inno Setup agrega "_is1").
$ClaveDesinstalar = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{B6F0B0C2-5C1E-4D4B-9E1F-7A2C3D4E5F60}_is1"
$ErrorActionPreference = "Stop"
# Nunca dentro de la carpeta que se va a reemplazar.
Set-Location $env:TEMP

function Registrar([string]$mensaje) {
    try {
        New-Item -ItemType Directory -Force (Split-Path $Log) | Out-Null
        Add-Content -Path $Log -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $mensaje" -Encoding utf8
    } catch { }
}

try { Wait-Process -Id $Proceso -Timeout 120 -ErrorAction SilentlyContinue } catch { }
# PRPagos.exe (el lanzador) y la máquina virtual de Java son dos procesos: se
# espera a que no quede ninguno corriendo desde la carpeta instalada.
$carpeta = (Resolve-Path $Instalada).Path.TrimEnd('\') + '\'
for ($i = 0; $i -lt 120; $i++) {
    $vivos = Get-CimInstance Win32_Process -Filter "Name='PRPagos.exe'" |
        Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($carpeta, [StringComparison]::OrdinalIgnoreCase) }
    if (-not $vivos) { break }
    Start-Sleep -Seconds 1
}
Start-Sleep -Seconds 1

$anterior = "$Instalada.anterior"
try {
    if (Test-Path $anterior) { Remove-Item -Recurse -Force $anterior }
    # Renombrar falla si algún archivo sigue en uso (p. ej. el antivirus revisándolo):
    # se reintenta unos segundos y, si no se puede, no se toca nada.
    for ($i = 1; ; $i++) {
        try { Rename-Item -Path $Instalada -NewName (Split-Path $anterior -Leaf); break }
        catch { if ($i -ge 15) { throw }; Start-Sleep -Seconds 2 }
    }
    try {
        Copy-Item -Recurse -Path $Nueva -Destination $Instalada
        if (-not (Test-Path (Join-Path $Instalada "PRPagos.exe"))) { throw "La copia quedó incompleta." }
        # El desinstalador (unins000.exe / .dat) vive en la carpeta: pasa a la versión nueva.
        Get-ChildItem -Path $anterior -Filter "unins*" -File -ErrorAction SilentlyContinue |
            Copy-Item -Destination $Instalada -Force
    } catch {
        if (Test-Path $Instalada) { Remove-Item -Recurse -Force $Instalada }
        Rename-Item -Path $anterior -NewName (Split-Path $Instalada -Leaf)
        throw
    }
    Remove-Item -Recurse -Force $anterior -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Split-Path $Nueva) -ErrorAction SilentlyContinue
    if ($Version -and (Test-Path $ClaveDesinstalar)) {
        try {
            Set-ItemProperty -Path $ClaveDesinstalar -Name DisplayVersion -Value $Version
            Set-ItemProperty -Path $ClaveDesinstalar -Name "Inno Setup: App Version" -Value $Version -ErrorAction SilentlyContinue
        } catch { Registrar "No se pudo actualizar la versión en Aplicaciones instaladas: $($_.Exception.Message)" }
    }
    Registrar "Actualización $Version instalada en $Instalada."
} catch {
    Registrar "La instalación falló: $($_.Exception.Message). Sigue la versión anterior; se reintenta al próximo inicio."
}

if ($Abrir -eq "1") { Start-Process (Join-Path $Instalada "PRPagos.exe") }
