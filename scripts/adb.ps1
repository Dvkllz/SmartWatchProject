param([Parameter(Mandatory=$true)][string[]]$Arguments)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:ANDROID_USER_HOME = Join-Path $root '.tools\android-user'
$env:ANDROID_SDK_HOME = Join-Path $root '.tools'
$adb = Join-Path $root '.tools\android-sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'No hay SDK local. Usa el adb de tu instalación Android SDK.' }
& $adb @Arguments
if ($LASTEXITCODE -ne 0) { throw "ADB terminó con código $LASTEXITCODE" }
