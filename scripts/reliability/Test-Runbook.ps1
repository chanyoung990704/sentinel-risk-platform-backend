param(
    [Parameter(Mandatory=$true)][ValidateSet('enqueue','restore','restore-resume','reset-restore')][string]$Action,
    [Parameter(Mandatory=$true)][ValidateSet('operations','restore')][string]$Namespace,
    [ValidateRange(1,3)][int]$Seed=1
)
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$linuxRepo=(& wsl -d Ubuntu -- wslpath -u $repo.Replace('\','/')).Trim()
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& wsl -d Ubuntu -- env "SENTINEL_RUNTIME_NAMESPACE=$Namespace" python3 "$linuxRepo/scripts/reliability/operations_runbook.py" $Action $Seed
exit $LASTEXITCODE
