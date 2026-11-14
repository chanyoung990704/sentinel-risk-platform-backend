"""소유 시험 JVM만 제어하고 비밀 자격을 출력하지 않는다."""
import argparse,http.client,json,os,secrets,signal,subprocess,time
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops
from capture_baseline import fingerprint

STATE=runtime.STATE/'m05'
PROC=Path('/proc')

def owned():
    ready=STATE/'app-ready.json'
    record=ready if ready.exists() else STATE/'app-launch.json'
    if not record.exists():return None
    value=json.loads(record.read_text());pid=value['pid'];cmd=PROC/str(pid)/'cmdline';status=PROC/str(pid)/'stat'
    if not cmd.exists() or status.read_text().split()[2]=='Z':return None
    arguments=cmd.read_bytes().split(b'\0')
    if not any(arg.endswith(b'PortfolioOperationsProcess.java') for arg in arguments) or str(STATE).encode() not in arguments or value['nonce'].encode() not in arguments:raise RuntimeError('소유 앱 PID/nonce 불일치')
    return value

def stop(force=False):
    ops.require_experiment();value=owned()
    if value:
        os.kill(value['pid'],signal.SIGKILL if force else signal.SIGTERM)
        for _ in range(400):
            if owned() is None:break
            time.sleep(.1)
        else:raise RuntimeError('소유 앱 종료 한도 초과')
    (STATE/'app-ready.json').unlink(missing_ok=True)
    (STATE/'app-launch.json').unlink(missing_ok=True)

def start(paused=False,build=False):
    ops.require_experiment()
    if owned():raise RuntimeError('소유 앱이 이미 실행 중입니다')
    (STATE/'app-ready.json').unlink(missing_ok=True);(STATE/'app-launch.json').unlink(missing_ok=True)
    runtime.sync_workspace();runtime.setup();STATE.mkdir(parents=True,mode=0o700,exist_ok=True)
    env=runtime.application_environment();env['SPRING_DATA_REDIS_TIMEOUT']='200ms';env['SPRING_DATA_REDIS_CONNECT_TIMEOUT']='200ms'
    if build or not (runtime.WORKSPACE/'build/portfolio-report-classpath.txt').exists():
        with (STATE/'build.log').open('a') as log:subprocess.run(['bash','./gradlew','prepareCustomReportCli','--no-daemon','--console=plain'],cwd=runtime.WORKSPACE,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
    runtime.sync_workspace();hashes=fingerprint(runtime.SOURCE)
    if hashes!=fingerprint(runtime.WORKSPACE):raise RuntimeError('소유 앱 소스 복사 불일치')
    (STATE/'source.json').write_text(json.dumps(hashes));cp=(runtime.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip()
    nonce=secrets.token_hex(24)
    with (STATE/f'app-{time.time_ns()}.log').open('w') as log:
        child=subprocess.Popen(['java','-Xms128m','-Xmx384m','-Dspring.devtools.restart.enabled=false','--class-path',cp,'src/test/java/com/aivle/project/reliability/PortfolioOperationsProcess.java',str(STATE),str(paused).lower(),nonce],cwd=runtime.WORKSPACE,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    ops.write_json(STATE/'app-launch.json',dict(pid=child.pid,nonce=nonce));os.chmod(STATE/'app-launch.json',0o600)
    for _ in range(120):
        if child.poll() is not None:raise RuntimeError('소유 앱 기동 실패; private log 확인')
        if (STATE/'app-ready.json').exists() and owned():return dict(up=True,pid=child.pid,paused=paused)
        time.sleep(1)
    raise RuntimeError('소유 앱 기동 한도 초과')

def control(action):
    ops.require_experiment();value=owned()
    if not value:raise RuntimeError('소유 앱이 정지 상태입니다')
    connection=http.client.HTTPConnection('127.0.0.1',value['controlPort'],timeout=5)
    try:
        connection.request('POST','/worker',action,{'X-Owned-Nonce':value['nonce']});response=connection.getresponse();body=response.read()
        if response.status!=200:raise RuntimeError('소유 worker 제어 실패')
        return json.loads(body)
    finally:connection.close()

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('start','pause-start','pause','resume','stop','kill'));parser.add_argument('--build',action='store_true');args=parser.parse_args()
    if args.action in ('start','pause-start'):print(json.dumps(start(args.action=='pause-start',args.build)))
    elif args.action in ('pause','resume'):print(json.dumps(control(args.action)))
    else:stop(args.action=='kill');print('소유 시험 앱 종료')
