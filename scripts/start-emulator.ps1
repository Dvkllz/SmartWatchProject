param([switch]$ShowWindow, [switch]$EnableMicrophone)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_HOME = Join-Path $root '.tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $root '.tools\android-user'
$env:ANDROID_SDK_HOME = Join-Path $root '.tools'
$env:ANDROID_AVD_HOME = Join-Path $root '.tools\avd'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
if (-not (Test-Path $emulator) -or -not (Test-Path (Join-Path $env:ANDROID_AVD_HOME 'WatchSosDemo.ini'))) {
    throw 'El AVD local no está instalado. Crea un dispositivo Wear OS desde Android Studio; consulta README.md.'
}
$emulatorArgs = @('-avd','WatchSosDemo','-port','5556','-no-snapshot','-gpu','swiftshader_indirect','-no-boot-anim','-memory','1536','-cores','2')
if ($EnableMicrophone) {
    $configPath = Join-Path $env:ANDROID_AVD_HOME 'WatchSosDemo.avd/config.ini'
    $config = Get-Content -LiteralPath $configPath -Raw
    if ($config -match '(?m)^hw\.audioInput\s*=.*$') {
        $config = $config -replace '(?m)^hw\.audioInput\s*=.*$', 'hw.audioInput = yes'
    } else { $config += "`nhw.audioInput = yes`n" }
    Set-Content -LiteralPath $configPath -Value $config -Encoding UTF8
    $emulatorArgs += '-allow-host-audio'
} else { $emulatorArgs += '-no-audio' }
if (-not $ShowWindow) { $emulatorArgs += '-no-window' }
$style = if ($ShowWindow) { 'Normal' } else { 'Hidden' }
Start-Process -FilePath $emulator -ArgumentList $emulatorArgs -WindowStyle $style -RedirectStandardOutput (Join-Path $root '.tools\emulator.stdout.log') -RedirectStandardError (Join-Path $root '.tools\emulator.stderr.log')
