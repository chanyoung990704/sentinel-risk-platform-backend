param([Parameter(Mandatory=$true)][ValidateSet('setup','up','verify','start-app','stop-app','down')][string]$Action)
# 실행과 비밀 파일 생성은 Ubuntu 안에서만 수행한다.
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu -- wslpath -u $repo.Replace('\', '/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
& wsl -d Ubuntu -- python3 "$linuxRepo/scripts/reliability/local_runtime.py" $Action
exit $LASTEXITCODE
