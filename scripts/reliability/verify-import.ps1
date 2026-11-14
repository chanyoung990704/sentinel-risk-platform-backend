param(
    [Parameter(Mandatory=$true)][guid]$SnapshotId,
    [Parameter(Mandatory=$true)][string]$OutputDir,
    [string]$SourceDir
)
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted = & wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$linuxRepo = ([string]$converted).Trim()
$outputFull = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
$outputConverted = & wsl -d Ubuntu --exec wslpath -u $outputFull.Replace('\','/')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$cliArgs = @('verify',$SnapshotId.ToString(),([string]$outputConverted).Trim())
if ($SourceDir) {
    $sourceFull = (Resolve-Path -LiteralPath $SourceDir -ErrorAction Stop).Path
    $sourceConverted = & wsl -d Ubuntu --exec wslpath -u $sourceFull.Replace('\','/')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $cliArgs += ([string]$sourceConverted).Trim()
}
& wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/reliability/data_cli.py" @cliArgs
exit $LASTEXITCODE
