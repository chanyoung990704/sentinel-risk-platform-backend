[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RunId,[Parameter(Mandatory=$true)][string]$SqlFile)
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted=& wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$linuxRepo=([string]$converted).Trim()
$sqlFull=(Resolve-Path -LiteralPath $SqlFile).Path
$converted=& wsl -d Ubuntu --exec wslpath -u $sqlFull.Replace('\','/')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/perf/portfolio_run.py" explain --run-id $RunId --sql-file ([string]$converted).Trim()
exit $LASTEXITCODE
