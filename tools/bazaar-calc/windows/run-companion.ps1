param([string]$NodePath = "node")
$ErrorActionPreference = 'Stop'
$CompanionRoot = Split-Path $PSScriptRoot -Parent
Set-Location $CompanionRoot
New-Item -ItemType Directory -Force -Path 'data' | Out-Null
foreach ($LogName in @('companion.log', 'companion-error.log')) {
    $LogPath = Join-Path $CompanionRoot "data\$LogName"
    if ((Test-Path $LogPath) -and (Get-Item $LogPath).Length -gt 5MB) {
        Move-Item -Force $LogPath "$LogPath.previous"
    }
}
& $NodePath (Join-Path $CompanionRoot 'server.mjs') 8789 >> 'data\companion.log' 2>> 'data\companion-error.log'
exit $LASTEXITCODE
