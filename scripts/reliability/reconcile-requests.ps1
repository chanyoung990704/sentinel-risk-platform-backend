[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateCount(1,1000)][guid[]]$RequestIds)
# 원문 대신 사용자가 지정한 요청 ID만 전달한다.
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
$cliArgs = @($RequestIds | ForEach-Object { $_.ToString() })
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/reconcile_requests.py" @cliArgs
exit $LASTEXITCODE
