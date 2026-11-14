[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][guid]$QuarantineId,
    [Parameter(Mandatory=$true)][guid]$CommandId,
    [Parameter(Mandatory=$true)][long]$ActorUserId,
    [Parameter(Mandatory=$true)][guid]$TargetRequestId,
    [Parameter(Mandatory=$true)][ValidateLength(1,1000)][string]$Reason
)
# 값은 Ubuntu 프로그램 인수로 전달한다.
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
$cliArgs = @($QuarantineId.ToString(),$CommandId.ToString(),[string]$ActorUserId,$TargetRequestId.ToString(),$Reason)
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/quarantine_cli.py" @cliArgs
exit $LASTEXITCODE
