[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,70}$')][string]$RunId,
    [ValidateRange(1,100)][int[]]$Concurrency=@(1,10,50,100),
    [ValidateRange(0,3600)][int]$WarmupSeconds=30,
    [ValidateRange(1,3600)][int]$DurationSeconds=120,
    [ValidateRange(1,20)][int]$Repeat=3,
    [ValidateSet('off')][string]$CacheMode='off',
    [ValidateSet('baseline','candidate','smoke')][string]$Purpose='baseline'
)
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$converted=& wsl -d Ubuntu --exec wslpath -u $repo.Replace('\','/')
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$linuxRepo=([string]$converted).Trim()
foreach($level in $Concurrency){
    for($iteration=1;$iteration -le $Repeat;$iteration++){
        $id="$RunId-c$level-r$iteration"
        & wsl -d Ubuntu --exec python3 "$linuxRepo/scripts/perf/portfolio_run.py" read --run-id $id --concurrency $level --warmup-seconds $WarmupSeconds --duration-seconds $DurationSeconds --purpose $Purpose
        if($LASTEXITCODE -ne 0){Write-Warning "측정 중단: $id 원자료/guard.json을 확인하세요. 높은 부하로 올리지 않습니다.";exit $LASTEXITCODE}
    }
}
exit 0
