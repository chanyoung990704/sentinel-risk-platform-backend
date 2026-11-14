[CmdletBinding(DefaultParameterSetName='Import')]
param(
    [Parameter(Mandatory=$true,ParameterSetName='Import')][string]$SourceDir,
    [Parameter(Mandatory=$true,ParameterSetName='Resume')][guid]$RunId,
    [Parameter(Mandatory=$true)][string]$OutputDir,
    [int]$BaseQuarter=20253,
    [ValidateSet('STRICT','SENTINEL_ARCHIVE_CODE_GAP_V1')][string]$Policy='STRICT'
)
# 앱·Gradle·파일 파싱은 모두 Ubuntu에서 실행한다.
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
$outputFull = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
$outputConverted = & wsl -d Ubuntu --exec wslpath -u $outputFull.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$outputLinux = ([string]$outputConverted).Trim()
if ($PSCmdlet.ParameterSetName -eq 'Resume') { $cliArgs = @('resume',$RunId.ToString(),$outputLinux) }
else {
    $sourceFull = (Resolve-Path -LiteralPath $SourceDir -ErrorAction Stop).Path
    $sourceConverted = & wsl -d Ubuntu --exec wslpath -u $sourceFull.Replace('\','/')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $cliArgs = @('import',([string]$sourceConverted).Trim(),$outputLinux,[string]$BaseQuarter,$Policy)
}
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/data_cli.py" @cliArgs
exit $LASTEXITCODE
