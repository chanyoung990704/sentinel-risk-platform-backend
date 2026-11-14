[CmdletBinding()]
param([ValidateSet('configure','status','recover')][string]$Action='configure')
# 비밀 값은 전달하지 않고 Ubuntu private state의 provider를 사용한다.
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/configure_cdc.py" $Action
exit $LASTEXITCODE
