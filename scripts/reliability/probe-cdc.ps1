param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9_-]{1,64}$')][string]$RunId)
# Ubuntu에서 실행하며 기존 증거 파일은 덮어쓰지 않는다.
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu -- wslpath -u $repo.Replace('\', '/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
& wsl -d Ubuntu -- python3 "$linuxRepo/scripts/reliability/cdc_probe.py" --run-id $RunId
exit $LASTEXITCODE
