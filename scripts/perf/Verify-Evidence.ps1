param([ValidatePattern('^[a-z0-9-]+$')][string]$RunId='20261113-m05-m07')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$linuxRepo=(& wsl -d Ubuntu -- wslpath -u $repo.Replace('\','/')).Trim()
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& wsl -d Ubuntu -- python3 "$linuxRepo/scripts/perf/evidence.py" --root $linuxRepo --package "$linuxRepo/perf/results/$RunId/evidence-package.json" --output "$linuxRepo/perf/results/$RunId/evidence-verification-latest.json"
exit $LASTEXITCODE
