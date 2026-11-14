"""Ubuntu 전용 실제 조회 측정기. 인증은 비밀 디렉터리, 원자료는 새 run 디렉터리에만 기록한다."""
import argparse,csv,hashlib,http.client,importlib.util,json,math,os,platform,re,shutil,socket,subprocess,sys,threading,time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from portfolio_summary import write_summary

REPO=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(REPO/'scripts/reliability'))
import local_runtime as runtime
from capture_baseline import fingerprint
NATIVE=runtime.STATE/'measurements'
FIELDS=['label','phase','sequence','worker','request_key','start_ms','elapsed_ms','status','expected_status','success','sql_count','sql_nanos','sql_errors','response_hash','response_bytes','error']
_READY_READERS=set()

def canonical_hash(body):
    # 응답 봉투의 생성 시각 하나만 제외하며 업무 데이터는 모두 비교한다.
    clean={key:value for key,value in body.items() if key!='timestamp'}
    return hashlib.sha256(json.dumps(clean,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def validate_run_id(value):
    if not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,100}',value):raise ValueError('Invalid run ID')
    return value

def prometheus_value(text,name,tag=None):
    values=[]
    for line in text.splitlines():
        match=re.fullmatch(re.escape(name)+r'(\{[^}]*\})?\s+([-+0-9.eE]+)',line)
        if match and (tag is None or tag in (match[1] or '')):
            number=float(match[2])
            if math.isfinite(number):values.append(number)
    return sum(values) if values else None

def guard_reasons(label,rows,errors):
    reasons=[]
    if label['httpErrorPercent']>0:reasons.append('http-errors')
    if label['responseHashMismatches']:reasons.append('response-drift')
    if label['sqlCoveragePercent']<100:reasons.append('sql-coverage')
    if not rows or errors:reasons.append('runtime-unavailable')
    consecutive=0
    for row in rows:
        pending=row.get('pool_pending')
        consecutive=consecutive+1 if pending is not None and pending>0 else 0
        if consecutive>=5:reasons.append('pool-pending-5-samples');break
    if any(row.get('mem_available_bytes') is not None and row['mem_available_bytes']<512*1024**2 for row in rows):reasons.append('host-memory-low')
    if any(row.get('app_rss_bytes') is not None and row['app_rss_bytes']>1536*1024**2 for row in rows):reasons.append('app-rss-high')
    return reasons

def exclusive_json(path,value):
    with Path(path).open('x',encoding='utf-8') as file:json.dump(value,file,ensure_ascii=False,indent=2);file.write('\n')

def git_head():
    metadata=REPO/'.git'
    if metadata.is_file():
        pointer=metadata.read_text().strip().removeprefix('gitdir: ').replace('\\','/')
        if re.match(r'^[A-Za-z]:/',pointer):pointer='/mnt/'+pointer[0].lower()+pointer[2:]
        metadata=(REPO/pointer).resolve()
    return subprocess.run(['git','--git-dir='+str(metadata),'--work-tree='+str(REPO),'-c','safe.directory='+str(REPO),'rev-parse','HEAD'],capture_output=True,text=True,check=True).stdout.strip()

def validate_read_sql(sql):
    # 일반 SQL 파서가 아니라 공개 시험 SELECT/SHOW/EXPLAIN SELECT만 받는 보수적인 경계다.
    sql=sql.strip()
    if sql.endswith(';'):sql=sql[:-1].rstrip()
    if not sql or any(token in sql for token in (';','/*','*/','--','#','\\')) or any(ord(c)<32 and c not in '\t\r\n' for c in sql):
        raise ValueError('Only one uncommented read statement is supported')
    if not re.match(r'^(SELECT\s|SHOW\s|EXPLAIN\s+(?:ANALYZE\s+)?SELECT\s)',sql,re.I):raise ValueError('Read-only SQL required')
    if re.search(r'\b(?:INTO|OUTFILE|DUMPFILE|DELIMITER|CHARSET)\b',sql,re.I):raise ValueError('SQL output and client commands are forbidden')
    return sql

def ensure_mysql_reader():
    import secrets
    path=runtime.STATE/'secrets/perf-mysql-reader.json'
    if not path.exists():
        value=dict(username='sentinel_perf_'+secrets.token_hex(6),password=secrets.token_hex(32))
        descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(descriptor,'w') as file:json.dump(value,file)
    value=json.loads(path.read_text())
    if not re.fullmatch(r'sentinel_perf_[0-9a-f]{12}',value.get('username','')) or not re.fullmatch(r'[0-9a-f]{64}',value.get('password','')):raise ValueError('Invalid local reader credential format')
    key=(str(path),value['username'])
    if key not in _READY_READERS:
        # 이 작업 소유의 고유 계정에 SELECT만 부여한다. 비밀번호는 stdin/비밀 파일에만 존재한다.
        sql="CREATE USER IF NOT EXISTS '"+value['username']+"'@'%' IDENTIFIED BY '"+value['password']+"'; GRANT SELECT ON sentinel_portfolio.* TO '"+value['username']+"'@'%';"
        cmd=['docker','compose','-p',runtime.PROJECT,'-f',str(runtime.WORKSPACE/'docker-compose.portfolio.yml'),'exec','-T','mysql','sh','-c',
            'MYSQL_PWD="$(cat /run/secrets/mysql-root)" mysql -u root --batch --skip-column-names']
        subprocess.run(cmd,input=sql,env=runtime.environment(),text=True,capture_output=True,check=True)
        _READY_READERS.add(key)
    return value

def mysql(sql):
    sql=validate_read_sql(sql)
    credential=ensure_mysql_reader();env=runtime.environment();env['MYSQL_PWD']=credential['password']
    # Docker에는 환경변수 이름만 전달한다. SQL 파일의 후속 쓰기는 권한으로도 차단한다.
    cmd=['docker','compose','-p',runtime.PROJECT,'-f',str(runtime.WORKSPACE/'docker-compose.portfolio.yml'),'exec','-T','-e','MYSQL_PWD','mysql','mysql',
        '-u',credential['username'],'-D','sentinel_portfolio','--default-character-set=utf8mb4','--binary-mode','--batch','--raw','--skip-column-names']
    return subprocess.run(cmd,input=sql,env=env,text=True,capture_output=True,check=True).stdout

def dataset():
    row=mysql("SELECT a.snapshot_id,s.canonical_checksum,s.committed_cells,s.state,s.source_fingerprint FROM active_data_snapshots a JOIN data_snapshots s ON s.id=a.snapshot_id WHERE a.family='FINANCIAL'").strip().split('\t')
    if len(row)!=5 or row[0]!='2ba59132-4743-446b-a307-f81e9dd563f2' or row[1]!='bdde302162d4a00adc458851666ed63a1755a8b9c064ae3ed65e53ee1d32892f' or row[2]!='149976' or row[3]!='VERIFIED':raise RuntimeError('D04 active dataset differs')
    counts=mysql("SELECT COUNT(*),SUM(v.metric_value IS NULL),SUM(v.metric_value=0),COUNT(DISTINCT rv.id) FROM company_report_metric_values v JOIN company_report_versions rv ON rv.id=v.report_version_id JOIN active_data_snapshots a ON rv.data_snapshot_id=a.snapshot_id WHERE a.family='FINANCIAL'").strip().split('\t')
    if counts!=['149976','8656','1383','8332']:raise RuntimeError('Actual materialized dataset counts differ')
    return dict(snapshot=row[0],canonicalChecksum=row[1],cells=int(row[2]),state=row[3],sourceFingerprint=row[4],materializedCounts=counts)

def json_request(method,path,body=None,token=None):
    connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=10)
    try:
        headers={'Content-Type':'application/json'}
        if token:headers['Authorization']='Bearer '+token
        connection.request(method,path,json.dumps(body) if body is not None else None,headers)
        response=connection.getresponse();payload=json.loads(response.read());return response.status,payload
    finally:connection.close()

def login():
    secret=runtime.STATE/'secrets/perf-user.json'
    if not secret.exists():
        import secrets
        value=dict(email='portfolio-measurement@sentinel.invalid',password='M9!'+secrets.token_hex(20),name='로컬 측정 사용자',turnstileToken='local-perf-fixture')
        descriptor=os.open(secret,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(descriptor,'w') as file:json.dump(value,file)
    value=json.loads(secret.read_text())
    request={key:value[key] for key in ('email','password')}
    status,body=json_request('POST','/api/auth/login',request)
    if status==401:
        try:
            signup_status,_=json_request('POST','/api/auth/signup',value)
        except (OSError,ValueError,http.client.HTTPException):
            signup_status=None  # commit 여부가 모호하므로 자격은 보존하고 로그인으로 확인한다.
        if signup_status not in (None,201,409):raise RuntimeError('Local fixture signup failed: '+str(signup_status))
        status,body=json_request('POST','/api/auth/login',request)
    if status!=200:raise RuntimeError('Local fixture login failed: '+str(status))
    return body['data']['accessToken']

def inputs():
    ids=[int(row) for row in mysql("SELECT DISTINCT b.company_id FROM snapshot_report_versions b JOIN active_data_snapshots a ON b.snapshot_id=a.snapshot_id WHERE a.family='FINANCIAL' ORDER BY b.company_id").splitlines()]
    if len(ids)!=2083:raise RuntimeError('Actual company distribution differs')
    # 전체 기업에 균등한 고정 간격으로 20개를 선택한다. 빈 응답 기업으로 대체하지 않는다.
    return [f'/api/companies/{ids[round(i*(len(ids)-1)/19)]}/overview?quarterKey=20253' for i in range(20)]

def start_app():
    if runtime.owned_process() is not None:raise RuntimeError('Stop the owned application before rebuilding')
    runtime.sync_workspace();runtime.setup();runtime.verify()
    with socket.socket() as probe:probe.bind(('127.0.0.1',18080))
    env=runtime.environment();env['SPRING_PROFILES_ACTIVE']='local,portfolio-perf'
    with (runtime.STATE/'perf-build.log').open('w') as output:
        subprocess.run(['bash','./gradlew','bootJar','--no-daemon','--console=plain'],cwd=runtime.WORKSPACE,env=env,stdout=output,stderr=subprocess.STDOUT,check=True)
    jars=[p for p in (runtime.WORKSPACE/'build/libs').glob('*.jar') if not p.name.endswith('-plain.jar')]
    if len(jars)!=1:raise RuntimeError('Expected one bootJar')
    source_hashes=fingerprint(REPO)
    if source_hashes!=fingerprint(runtime.WORKSPACE):raise RuntimeError('Native source copy differs')
    (runtime.STATE/'perf-app-source.json').write_text(json.dumps(dict(sourceSha256=source_hashes,jarSha256=hashlib.sha256(jars[0].read_bytes()).hexdigest(),profile='local,portfolio-perf')))
    with (runtime.STATE/'perf-app.log').open('a') as log:
        process=subprocess.Popen(['java','-Xms512m','-Xmx512m','-jar',str(jars[0])],cwd=runtime.WORKSPACE,env=env,stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.DEVNULL,start_new_session=True)
    (runtime.STATE/'app.pid').write_text(str(process.pid))
    for _ in range(120):
        if process.poll() is not None:raise RuntimeError('Application exited; inspect private perf-app.log')
        try:
            if json_request('GET','/actuator/health/liveness')[0]==200:return process.pid
        except (OSError,ValueError):pass
        time.sleep(1)
    raise RuntimeError('Readiness timeout')

def proc_snapshot(pid):
    stat=Path(f'/proc/{pid}/stat').read_text().split();status=Path(f'/proc/{pid}/status').read_text()
    available=re.search(r'^MemAvailable:\s+(\d+)',Path('/proc/meminfo').read_text(),re.M)
    rss=re.search(r'^VmRSS:\s+(\d+)',status,re.M)
    return dict(app_cpu_seconds=(int(stat[13])+int(stat[14]))/os.sysconf('SC_CLK_TCK'),app_rss_bytes=int(rss[1])*1024 if rss else None,
        mem_available_bytes=int(available[1])*1024 if available else None)

def run_read(run_id,concurrency,warmup,duration,mode='baseline'):
    validate_run_id(run_id)
    if concurrency<1 or concurrency>100 or warmup<0 or duration<=0:raise ValueError('Invalid load settings')
    destination=REPO/'perf/results'/run_id
    if destination.exists():raise FileExistsError('Existing artifact directory preserved')
    root=NATIVE/run_id;root.mkdir(parents=True,exist_ok=False)
    pid=runtime.owned_process()
    if pid is None:raise RuntimeError('Owned application is not running')
    built=json.loads((runtime.STATE/'perf-app-source.json').read_text())
    if built['sourceSha256']!=fingerprint(REPO) or built['sourceSha256']!=fingerprint(runtime.WORKSPACE):raise RuntimeError('Rebuild required: instrumented source differs')
    paths=inputs();data=dataset();token=login();reference={}
    for path in paths:
        status,body=json_request('GET',path,token=token)
        if status!=200 or body.get('success') is not True:raise RuntimeError('Read preparation failed: '+str(status))
        if not body['data'].get('forecast',{}).get('metricSeries'):raise RuntimeError('Empty actual financial response')
        reference[path]=canonical_hash(body)
    hashes=fingerprint(REPO)
    cpuinfo=Path('/proc/cpuinfo').read_text();model=re.search(r'model name\s*:\s*(.+)',cpuinfo)
    manifest=dict(schemaVersion=1,runId=run_id,mode='read',purpose=mode,fixture=False,startedUtc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
        codeSha=git_head(),sourceSha256=hashes,
        sourceFingerprint=hashlib.sha256(json.dumps(hashes,sort_keys=True).encode()).hexdigest(),jarSha256=built['jarSha256'],dataset=data,
        generator='python-stdlib-http.client-threaded-closed-loop',generatorVersion=sys.version,os=platform.platform(),cpu=model[1] if model else None,cpuCount=os.cpu_count(),
        memory=Path('/proc/meminfo').read_text().splitlines()[0],disk=shutil.disk_usage(NATIVE)._asdict(),
        java=subprocess.run(['java','-version'],capture_output=True,text=True).stderr,
        load=dict(concurrency=concurrency,warmupSeconds=warmup,measureSeconds=duration,timeoutSeconds=10,distribution=paths,seed='equidistant-20-v1',arrivalMode='closed-loop'),
        config=dict(profile='local,portfolio-perf',heapMiB=512,poolMax=10,poolMin=10,cache='off',p6spyLogging=False,apiInfoLogging=False,instrumentation='P6Spy callback + synchronous request filter + 1Hz Prometheus',serverPort=18080,authentication='one actual ROLE_USER JWT per run; signup/login outside samples; Turnstile local mock',imageLocks={str(p.relative_to(REPO)):json.loads(p.read_text()) for p in (REPO/'infra/portfolio-images.lock.json',REPO/'infra/cdc/images.lock.json')}),
        guard=dict(httpErrors='any',poolPending='positive for 5 consecutive 1Hz samples',hostMemAvailableMinimumMiB=512,appRssMaximumMiB=1536),
        unavailable=['durable report lifecycle (M03)','Redis cache hit/miss (cache off)','Kafka lag/outbox/backlog (read workload)'],clock='monotonic_ns client elapsed and schedule; start_ms UTC anchored once',sqlCount='JDBC execute callbacks; batch=1; synchronous request thread only; includes authentication SQL if present',canonicalHash='all response fields except envelope timestamp')
    exclusive_json(root/'manifest.json',manifest)
    exclusive_json(root/'responses-before.json',reference)
    stop=threading.Event();sample_errors=[];runtime_rows=[];lock=threading.Lock();rows_written=0;worker_errors=[]
    origin_ns=time.monotonic_ns();origin_ms=time.time_ns()/1e6
    start=time.monotonic();measure_start=start+warmup;end=measure_start+duration
    runtime_fields=['time_ms','phase','app_cpu_seconds','app_cpu_percent','app_rss_bytes','mem_available_bytes','client_cpu_seconds','heap_used_bytes','gc_pause_count','gc_pause_seconds','jvm_threads','pool_active','pool_pending','pool_max','pool_timeout_count','sample_error']
    def sample():
        last=None
        with (root/'runtime.csv').open('w',newline='') as output:
            writer=csv.DictWriter(output,fieldnames=runtime_fields);writer.writeheader()
            while not stop.is_set():
                now=time.monotonic();row=dict(time_ms=origin_ms+(time.monotonic_ns()-origin_ns)/1e6,phase='warmup' if now<measure_start else 'measure',client_cpu_seconds=time.process_time())
                try:
                    row.update(proc_snapshot(pid))
                    connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=3)
                    try:
                        connection.request('GET','/actuator/prometheus',headers={'Authorization':'Bearer '+token});response=connection.getresponse();text=response.read().decode()
                        if response.status!=200:raise RuntimeError('collector-http-'+str(response.status))
                    finally:connection.close()
                    for key,name,tag in [('heap_used_bytes','jvm_memory_used_bytes','area="heap"'),('gc_pause_count','jvm_gc_pause_seconds_count',None),('gc_pause_seconds','jvm_gc_pause_seconds_sum',None),('jvm_threads','jvm_threads_live_threads',None),('pool_active','hikaricp_connections_active',None),('pool_pending','hikaricp_connections_pending',None),('pool_max','hikaricp_connections_max',None),('pool_timeout_count','hikaricp_connections_timeout_total',None)]:
                        row[key]=prometheus_value(text,name,tag)
                    if any(row[key] is None for key in ('heap_used_bytes','pool_active','pool_pending','pool_max')):raise RuntimeError('collector-missing-required-metric')
                    if last:row['app_cpu_percent']=100*(row['app_cpu_seconds']-last[1])/(now-last[0])
                    last=(now,row['app_cpu_seconds'])
                except Exception as error:
                    row['sample_error']=type(error).__name__;sample_errors.append(row['sample_error'])
                runtime_rows.append(row);writer.writerow(row);output.flush();stop.wait(max(0,1-(time.monotonic()-now)))
    def worker(number,writer):
        nonlocal rows_written
        connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=10);sequence=0;batch=[]
        try:
            while time.monotonic()<end:
                path=paths[(number+sequence)%len(paths)];sequence+=1;begin=time.monotonic_ns();phase='warmup' if begin/1e9<measure_start else 'measure'
                row=dict(label='company_overview',phase=phase,sequence=sequence,worker=number,request_key=path,start_ms=origin_ms+(begin-origin_ns)/1e6,status=0,expected_status=200,success='false',sql_count='',sql_nanos='',sql_errors='',response_hash='',response_bytes=0,error='')
                try:
                    connection.request('GET',path,headers={'Authorization':'Bearer '+token,'Accept':'application/json'})
                    response=connection.getresponse();body=response.read();row.update(status=response.status,response_bytes=len(body),sql_count=response.getheader('X-Portfolio-Sql-Count',''),sql_nanos=response.getheader('X-Portfolio-Sql-Nanos',''),sql_errors=response.getheader('X-Portfolio-Sql-Errors',''))
                    row['elapsed_ms']=(time.monotonic_ns()-begin)/1e6
                    parsed=json.loads(body);digest=canonical_hash(parsed);row['response_hash']=digest
                    row['success']=str(response.status==200 and parsed.get('success') is True and digest==reference[path]).lower()
                    if digest!=reference[path]:row['error']='canonical-mismatch'
                except (OSError,ValueError,http.client.HTTPException) as error:
                    row['elapsed_ms']=(time.monotonic_ns()-begin)/1e6;row['error']=type(error).__name__;connection.close();connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=10)
                batch.append(row)
                if len(batch)>=50:
                    with lock:writer.writerows(batch);rows_written+=len(batch)
                    batch.clear()
        except BaseException as error:worker_errors.append(type(error).__name__)
        finally:
            connection.close()
            if batch:
                with lock:writer.writerows(batch);rows_written+=len(batch)
    sampler=threading.Thread(target=sample,name='runtime-sampler');sampler.start()
    try:
        with (root/'requests.csv').open('w',newline='') as output:
            writer=csv.DictWriter(output,fieldnames=FIELDS);writer.writeheader()
            with ThreadPoolExecutor(max_workers=concurrency) as executor:list(executor.map(lambda n:worker(n,writer),range(concurrency)))
    finally:stop.set();sampler.join()
    after=dataset();exclusive_json(root/'dataset-after.json',after)
    exclusive_json(root/'runner-finished.json',dict(completed=not worker_errors and after==data,requestRows=rows_written,workerErrors=worker_errors))
    result=write_summary(root);label=result['labels'].get('company_overview')
    measured=[row for row in runtime_rows if row['phase']=='measure']
    reasons=guard_reasons(label,measured,len(sample_errors)) if label else ['no-measurement-samples']
    if label and label['unexpectedResponses']:reasons.append('unexpected-response-or-parity')
    if not result['measurementComplete']:reasons.append('incomplete')
    exclusive_json(root/'guard.json',dict(stopEscalation=bool(reasons),reasons=reasons,runtimeSamples=len(measured),sampleErrors=len(sample_errors)))
    shutil.copytree(root,destination)
    print(json.dumps(dict(runId=run_id,complete=result['measurementComplete'],summary=label,stopEscalation=bool(reasons),guardReasons=reasons),ensure_ascii=False))
    return reasons

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['start','stop','read','report','summarize','explain'])
    parser.add_argument('--run-id');parser.add_argument('--concurrency',type=int,default=1);parser.add_argument('--warmup-seconds',type=float,default=30);parser.add_argument('--duration-seconds',type=float,default=120);parser.add_argument('--purpose',default='baseline');parser.add_argument('--run-dir',type=Path);parser.add_argument('--sql-file',type=Path)
    args=parser.parse_args()
    if os.name!='posix' or not Path('/proc/version').exists():raise SystemExit('Execute inside WSL Ubuntu')
    if args.action=='start':print(json.dumps(dict(pid=start_app())))
    elif args.action=='stop':runtime.stop_app()
    elif args.action=='read':return 2 if run_read(args.run_id,args.concurrency,args.warmup_seconds,args.duration_seconds,args.purpose) else 0
    elif args.action=='report':raise SystemExit('M03 durable report workflow is not implemented; no artificial lifecycle measurements emitted')
    elif args.action=='summarize':write_summary(args.run_dir)
    elif args.action=='explain':
        root=REPO/'perf/results'/validate_run_id(args.run_id);root.mkdir(parents=True,exist_ok=False)
        sql=args.sql_file.read_text();(root/'query.sql').write_text(sql)
        (root/'explain.txt').write_text(mysql('EXPLAIN ANALYZE '+sql))
    return 0

if __name__=='__main__':raise SystemExit(main())
