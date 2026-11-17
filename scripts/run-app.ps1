param(
    [ValidateSet('Demo', 'Live')]
    [string]$Variant = 'Demo',
    [switch]$EnableMicrophone
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$serial = 'emulator-5556'
$variantLower = $Variant.ToLowerInvariant()
$package = if ($Variant -eq 'Demo') { 'com.watchsos.demo' } else { 'com.watchsos' }
$apk = Join-Path $root "wear-app\build\outputs\apk\$variantLower\debug\wear-app-$variantLower-debug.apk"
$adb = Join-Path $root '.tools\android-sdk\platform-tools\adb.exe'

& (Join-Path $PSScriptRoot 'build.ps1') -Tasks ":wear-app:assemble${Variant}Debug"

if (-not (Test-Path $apk)) { throw "No se genero el APK: $apk" }
if (-not (Test-Path $adb)) { throw 'No se encontro el ADB local del proyecto.' }

& $adb start-server | Out-Null

function Get-DeviceState {
    $line = & $adb devices 2>$null | Where-Object { $_ -match "^$serial\s+" } | Select-Object -First 1
    if (-not $line) { return 'missing' }
    return (($line -split '\s+')[1]).Trim()
}

$state = Get-DeviceState
if ($state -ne 'device') {
    if ($state -eq 'offline') {
        Write-Host 'El AVD anterior esta offline; se reiniciara.'
        Get-CimInstance Win32_Process -Filter "Name='qemu-system-x86_64.exe'" |
            Where-Object { $_.CommandLine -match 'WatchSosDemo' } |
            ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        Start-Sleep -Seconds 2
    }

    $startArgs = @{ ShowWindow = $true }
    if ($EnableMicrophone) { $startArgs.EnableMicrophone = $true }
    & (Join-Path $PSScriptRoot 'start-emulator.ps1') @startArgs
}

Write-Host 'Esperando a que Wear OS termine de iniciar...'
$ready = $false
for ($attempt = 0; $attempt -lt 180; $attempt++) {
    if ((Get-DeviceState) -eq 'device') {
        $bootCompleted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null).Trim()
        if ($bootCompleted -eq '1') {
            $ready = $true
            break
        }
    }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    throw 'El emulador no termino de arrancar en 6 minutos. Revisa que Windows Hypervisor Platform este habilitado.'
}

& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar $apk" }

& $adb -s $serial shell am force-stop $package
& $adb -s $serial shell am start -n "$package/com.watchsos.MainActivity"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la app.' }

Write-Host "$Variant instalada y abierta en $serial."
