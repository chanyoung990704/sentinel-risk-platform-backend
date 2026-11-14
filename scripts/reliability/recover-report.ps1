[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][guid]$RequestId,
    [Parameter(Mandatory=$true)][guid]$CommandId,
    [Parameter(Mandatory=$true)][long]$ActorUserId,
    [Parameter(Mandatory=$true)][ValidateLength(1,1000)][string]$Reason
)
# 값은 shell 명령으로 합치지 않고 Ubuntu 프로그램 인수로 전달한다.
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
$cliArgs = @($RequestId.ToString(),$CommandId.ToString(),[string]$ActorUserId,$Reason)
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/report_cli.py" @cliArgs
exit $LASTEXITCODE
