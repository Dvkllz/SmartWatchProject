param([string[]]$Tasks = @(':wear-app:assembleDemoDebug'))
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $env:JAVA_HOME -and (Test-Path 'C:\AppAndroid\.tools\java\jdk-21.0.12.1+1')) {
    $env:JAVA_HOME = 'C:\AppAndroid\.tools\java\jdk-21.0.12.1+1'
}
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-user'
$env:ANDROID_USER_HOME = Join-Path $root '.tools\android-user'
New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME | Out-Null
Push-Location $root
try {
    & "$root\gradlew.bat" @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }
} finally { Pop-Location }
