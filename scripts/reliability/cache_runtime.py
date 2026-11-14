"""인증 Redis를 보존하고 소유한 상태 cache 서비스만 제어한다."""
import argparse
import local_runtime as runtime
import re
import json,subprocess,time

def evict_keys(keys):
    if not keys or any(not re.fullmatch(r'custom-report:status:v1:[1-9][0-9]*:[0-9a-f-]{36}',key) for key in keys):raise ValueError('대상 cache 키 확인 필요')
    result=runtime.compose(['exec','-T','status-cache-redis','redis-cli','DEL',*keys],True)
    return int(result.stdout.strip())

def wait_caught_up():
    import sys
    from pathlib import Path
    repo=Path(__file__).resolve().parents[2];sys.path.insert(0,str(repo/'scripts/perf'))
    from portfolio_run import mysql
    fixture=json.loads((runtime.STATE/'r07-m04/browser-fixture.json').read_text());owners=','.join(str(int(user['id'])) for user in fixture['users'])
    payload=mysql("SELECT o.payload FROM outbox_events o JOIN report_requests r ON r.id=o.aggregateid WHERE r.user_id IN ("+owners+") AND r.status='SUCCEEDED' AND o.type='STATUS_CHANGED' ORDER BY o.created_at,o.id LIMIT 1").strip()
    if not payload:raise RuntimeError('소유 상태 barrier 입력 부재')
    cp=(runtime.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip()
    result=subprocess.run(['java','-Xms32m','-Xmx128m','--class-path',cp,str(repo/'scripts/perf/StatusCacheReady.java')],input=payload,text=True,cwd=runtime.WORKSPACE,env=runtime.environment(),capture_output=True,timeout=220)
    log=repo/'perf/results/20261112-r07-m03-m04'/('status-cache-ready-'+str(time.time_ns())+'.log');log.parent.mkdir(parents=True,exist_ok=True)
    log.write_text(result.stdout+result.stderr)
    if result.returncode:raise RuntimeError('상태 cache catch-up barrier 실패; 전용 로그 확인')
    line=next((line for line in result.stdout.splitlines() if line.startswith('STATUS_CACHE_READY ')),None)
    if line is None:raise RuntimeError('상태 cache catch-up 증거 부재')
    return json.loads(line.removeprefix('STATUS_CACHE_READY '))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['up','stop','verify']);args=parser.parse_args()
    runtime.sync_workspace()
    if args.action=='up':runtime.compose(['--profile','status-cache','up','-d','--wait','--wait-timeout','60','status-cache-redis'])
    elif args.action=='stop':runtime.compose(['stop','status-cache-redis'])
    else:
        result=runtime.compose(['exec','-T','status-cache-redis','redis-cli','ping'],True)
        if result.stdout.strip()!='PONG':raise RuntimeError('상태 cache Redis 확인 실패')
    print('상태 cache 서비스 '+args.action+' 완료; 인증/DB/자료 보존')

if __name__=='__main__':main()
