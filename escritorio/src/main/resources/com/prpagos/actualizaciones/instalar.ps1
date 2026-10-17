# Instala una versión nueva de PRPagos Escritorio ya descargada y verificada
# (lo lanza Actualizador.java). Espera a que la aplicación se cierre, deja la
# carpeta instalada como respaldo (.anterior), pone la nueva en su lugar y,
# si algo falla, restaura la anterior: la versión instalada nunca se pierde.
param(
    [int]$Proceso,
    [string]$Instalada,
    [string]$Nueva,
    [string]$Log,
    [string]$Abrir = "0"
)
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
    } catch {
        if (Test-Path $Instalada) { Remove-Item -Recurse -Force $Instalada }
        Rename-Item -Path $anterior -NewName (Split-Path $Instalada -Leaf)
        throw
    }
    Remove-Item -Recurse -Force $anterior -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force (Split-Path $Nueva) -ErrorAction SilentlyContinue
    Registrar "Actualización instalada en $Instalada."
} catch {
    Registrar "La instalación falló: $($_.Exception.Message). Sigue la versión anterior; se reintenta al próximo inicio."
}

if ($Abrir -eq "1") { Start-Process (Join-Path $Instalada "PRPagos.exe") }
