param([string]$NodePath = "node")
$ErrorActionPreference = 'Stop'
$CompanionRoot = Split-Path $PSScriptRoot -Parent
Set-Location $CompanionRoot
# The Node helper migrates old history before the server or log writers start.
$PreviousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue' # Migration notices on native stderr are not failures.
try {
    $DataDirectory = & $NodePath (Join-Path $CompanionRoot 'data-paths.mjs')
    $NodeExitCode = $LASTEXITCODE
} finally { $ErrorActionPreference = $PreviousPreference }
if ($NodeExitCode -ne 0) { throw 'Cannot prepare persistent data; see the error above.' }
$DataDirectory = $DataDirectory.Trim()
foreach ($LogName in @('companion.log', 'companion-error.log')) {
    $LogPath = Join-Path $DataDirectory $LogName
    if ((Test-Path $LogPath) -and (Get-Item $LogPath).Length -gt 5MB) {
        Move-Item -Force $LogPath "$LogPath.previous"
    }
}
& $NodePath (Join-Path $CompanionRoot 'server.mjs') 8789 >> (Join-Path $DataDirectory 'companion.log') 2>> (Join-Path $DataDirectory 'companion-error.log')
exit $LASTEXITCODE
