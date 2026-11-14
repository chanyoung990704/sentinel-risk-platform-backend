[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,60}$')][string]$RunId,
    [ValidateRange(1,20)][int[]]$ArrivalRates=@(1,5,10,20),
    [ValidateRange(0,10000)][int]$MockLatencyMs=200,
    [ValidateSet('off','on')][string]$CacheMode='off',
    [ValidateRange(1,10)][int]$Repeat=3,
    [ValidateRange(0,3600)][int]$WarmupSeconds=30,
    [ValidateRange(1,3600)][int]$DurationSeconds=120
)
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted=& wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$linuxRepo=([string]$converted).Trim()
foreach($rate in $ArrivalRates){
    for($iteration=1;$iteration -le $Repeat;$iteration++){
        & wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/perf/report_benchmark.py" --run-id "$RunId-a$rate-r$iteration" --rate $rate --latency $MockLatencyMs --cache $CacheMode --warmup $WarmupSeconds --duration $DurationSeconds
        if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
    }
}
