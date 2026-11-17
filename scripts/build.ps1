param([string[]]$Tasks = @(':wear-app:assembleDemoDebug'))
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

$localJdk = Join-Path $root '.tools\jdk-21'
if (Test-Path (Join-Path $localJdk 'bin\java.exe')) {
    $env:JAVA_HOME = $localJdk
} elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaVersion = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1 | Select-Object -First 1
    if ($javaVersion -notmatch 'version "(17|18|19|20|21|22|23|24)(\.|\")') {
        throw "JAVA_HOME usa una version no compatible ($javaVersion). Configura JDK 17 o 21."
    }
} else {
    throw 'No se encontro un JDK compatible. Instala JDK 17/21 o coloca JDK 21 en .tools\jdk-21.'
}
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-user'
$env:ANDROID_USER_HOME = Join-Path $root '.tools\android-user'
New-Item -ItemType Directory -Force $env:ANDROID_USER_HOME | Out-Null
Push-Location $root
try {
    & "$root\gradlew.bat" @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle termino con codigo $LASTEXITCODE" }
} finally { Pop-Location }
