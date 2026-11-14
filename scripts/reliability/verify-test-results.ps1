# Ubuntu에서는 Python 진입점을 직접 사용한다. 인수는 그대로 전달한다.
$verifier = Join-Path $PSScriptRoot 'verify_test_results.py'
python $verifier @args
exit $LASTEXITCODE
