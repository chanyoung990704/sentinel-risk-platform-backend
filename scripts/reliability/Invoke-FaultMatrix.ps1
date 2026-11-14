param([ValidateRange(1,3)][int[]]$Seeds=@(1,2,3),[ValidateRange(1,20)][int]$Attempt=1)
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$linuxRepo=(& wsl -d Ubuntu -- wslpath -u $repo.Replace('\','/')).Trim()
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
foreach($seed in $Seeds){
    & wsl -d Ubuntu -- env SENTINEL_RUNTIME_NAMESPACE=restore python3 "$linuxRepo/scripts/reliability/fault_matrix.py" --seed $seed --attempt $Attempt
    if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
}
