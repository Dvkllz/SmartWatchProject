$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $root 'backend')
try {
    if (-not (Test-Path 'node_modules')) {
        & npm.cmd ci
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron instalar las dependencias' }
    }
    & npm.cmd start
} finally { Pop-Location }
