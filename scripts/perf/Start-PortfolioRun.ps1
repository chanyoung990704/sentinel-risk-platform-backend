[CmdletBinding()]
param([ValidateSet('Start','Stop')][string]$Action='Start')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted=& wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$linuxRepo=([string]$converted).Trim()
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/perf/portfolio_run.py" $Action.ToLowerInvariant()
exit $LASTEXITCODE
