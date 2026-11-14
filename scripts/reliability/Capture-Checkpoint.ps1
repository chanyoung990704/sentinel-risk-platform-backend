param([Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9]+$')][string]$Name)
# 원본이 아닌 operations 정지 checkpoint만 Ubuntu에서 생성한다.
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$linuxRepo=(& wsl -d Ubuntu -- wslpath -u $repo.Replace('\','/')).Trim()
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& wsl -d Ubuntu -- env SENTINEL_RUNTIME_NAMESPACE=operations python3 "$linuxRepo/scripts/reliability/checkpoint.py" capture $Name
exit $LASTEXITCODE
