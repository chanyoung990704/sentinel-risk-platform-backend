"""소유 앱만 기동/정지하며 자격 정보는 Ubuntu private state에 둔다."""
import json
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
import local_runtime as r
from capture_baseline import fingerprint

STATE = r.STATE / 'r07-m04'

def stop():
    ready = STATE / 'browser-ready.json'
    if not ready.exists(): return
    pid = json.loads(ready.read_text())['pid']
    cmd = Path(f'/proc/{pid}/cmdline')
    if cmd.exists() and b'PortfolioBrowserProcess.java' in cmd.read_bytes():
        os.kill(pid, signal.SIGTERM)
        for _ in range(300):
            stat = Path(f'/proc/{pid}/stat')
            if not stat.exists() or stat.read_text().split()[2] == 'Z': break
            time.sleep(.1)
        else: raise RuntimeError('소유 앱 종료 한도 초과')
    elif cmd.exists(): raise RuntimeError('PID 소유권 불일치')
    ready.unlink()

def start(mode):
    if mode not in ('off', 'on'): raise ValueError('cache mode')
    if (STATE / 'browser-ready.json').exists(): raise RuntimeError('먼저 stop 필요')
    r.sync_workspace(); r.setup(); r.verify()
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    env = r.application_environment()
    # 성능 fixture도 실제 서버의 짧은 Redis timeout을 사용한다.
    env['SPRING_DATA_REDIS_TIMEOUT']='200ms'
    env['SPRING_DATA_REDIS_CONNECT_TIMEOUT']='200ms'
    subprocess.run(['bash','./gradlew','prepareCustomReportCli','--no-daemon','--console=plain'],cwd=r.WORKSPACE,env=r.environment(),check=True)
    (STATE/'app-source.json').write_text(json.dumps({'sourceSha256':fingerprint(r.SOURCE),'mainSha256':{k:v for k,v in fingerprint(r.SOURCE).items() if k.startswith('src/main/')}}))
    cp=(r.WORKSPACE/'build/portfolio-report-classpath.txt').read_text().strip()
    with (STATE/f'app-{time.time_ns()}.log').open('w') as log:
        process=subprocess.Popen(['java','-Xms128m','-Xmx512m','-Dspring.devtools.restart.enabled=false','--class-path',cp,'src/test/java/com/aivle/project/reliability/PortfolioBrowserProcess.java',str(STATE),mode],cwd=r.WORKSPACE,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    for _ in range(120):
        if process.poll() is not None: raise RuntimeError('앱 시작 실패; private log 확인')
        if (STATE/'browser-ready.json').exists(): return {'application':'UP','cache':mode,'pid':process.pid}
        time.sleep(1)
    raise RuntimeError('기동 한도 초과; 프로세스는 보존됨')

if __name__=='__main__':
    if sys.argv[1]=='stop': stop(); print('소유 시험 앱 종료')
    elif sys.argv[1]=='start': print(json.dumps(start(sys.argv[2])))
    else: raise ValueError('start/stop')
