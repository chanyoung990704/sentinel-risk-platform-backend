param([Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9]+$')][string]$Name)
# 비어 있는 restore DB만 대상으로 하며 hash 검증이 적용보다 먼저 수행된다.
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$linuxRepo=(& wsl -d Ubuntu -- wslpath -u $repo.Replace('\','/')).Trim()
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& wsl -d Ubuntu -- env SENTINEL_RUNTIME_NAMESPACE=restore python3 "$linuxRepo/scripts/reliability/checkpoint.py" restore $Name
exit $LASTEXITCODE
