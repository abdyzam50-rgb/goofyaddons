$ErrorActionPreference = 'Stop'
$NodeCommand = Get-Command node -ErrorAction Stop
$NodeVersion = [version]((& $NodeCommand.Source --version).TrimStart('v'))
if ($NodeVersion -lt [version]'22.15.0') { throw 'Install Node.js 24 LTS first.' }
$Runner = Join-Path $PSScriptRoot 'run-companion.ps1'
$UserName = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$Action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -File `"$Runner`" -NodePath `"$($NodeCommand.Source)`"" -WorkingDirectory (Split-Path $PSScriptRoot -Parent)
$Trigger = New-ScheduledTaskTrigger -AtLogOn -User $UserName
$Principal = New-ScheduledTaskPrincipal -UserId $UserName -LogonType Interactive -RunLevel Limited
$Settings = New-ScheduledTaskSettingsSet -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName 'GoofyAddons Bazaar Companion' -Action $Action -Trigger $Trigger -Principal $Principal -Settings $Settings -Force | Out-Null
Start-ScheduledTask -TaskName 'GoofyAddons Bazaar Companion'
Write-Host 'Installed and started. Dashboard: http://127.0.0.1:8789. Logs and history: data folder.'
