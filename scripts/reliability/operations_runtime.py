"""M05~M07 전용 실행 환경. 원본은 읽기/정지/재개만 하며 시험 DB와 volume은 분리한다."""
import argparse,hashlib,json,os,re,subprocess,time
from pathlib import Path
import local_runtime as runtime

RUN='20261113-m05-m07'
PRIVATE=Path.home()/'.local/share/sentinel/operations-evidence'/RUN
RESULTS=runtime.SOURCE/'perf/results'/RUN
SEED_TABLES=('roles','categories','industry_codes','companies','quarters','metrics','files','company_reports',
 'company_report_versions','company_report_metric_values','metric_averages','metric_descriptions',
 'key_metric_descriptions','risk_score_summaries','data_snapshots','data_snapshot_sources','import_runs',
 'import_chunks','import_errors','import_stage_values','snapshot_report_versions','active_data_snapshots','flyway_schema_history')
FINANCIAL_SQL="""SELECT c.stock_code,m.metric_code,q.quarter_key,v.value_type,v.metric_value,v.signal_color,v.benchmark_value,v.color_rationale
FROM company_report_metric_values v JOIN company_report_versions rv ON rv.id=v.report_version_id
JOIN active_data_snapshots a ON a.snapshot_id=rv.data_snapshot_id AND a.family='FINANCIAL'
JOIN company_reports cr ON cr.id=rv.company_report_id JOIN companies c ON c.id=cr.company_id
JOIN metrics m ON m.id=v.metric_id JOIN quarters q ON q.id=v.quarter_id
ORDER BY c.stock_code,m.metric_code,q.quarter_key,v.value_type"""

def require_experiment():
    if runtime.NAMESPACE not in ('operations','restore','modulith'):raise ValueError('원본 환경 변경 거부')
    expected=runtime.namespace_config(runtime.NAMESPACE)
    if runtime.PROJECT!=expected['project'] or runtime.STATE.resolve()!=expected['state'].resolve() or runtime.WORKSPACE.resolve()!=expected['workspace'].resolve():
        raise ValueError('시험 환경 소유 경로 불일치')

def require_idle():
    """전체 회귀가 이전 실행의 대기 작업을 claim하지 않도록 시작 전에 거절한다."""
    require_experiment()
    queries={
        'heldSlots': "SELECT COALESCE(MAX(used_count),0) FROM report_admission WHERE scope_key='GLOBAL'",
        'nonterminalRequests': "SELECT COUNT(*) FROM report_requests WHERE status NOT IN ('SUCCEEDED','FAILED')",
        'unfinishedJobs': "SELECT COUNT(*) FROM report_jobs WHERE state IN ('READY','RUNNING','RETRY_WAIT')"}
    counts={key:int(sql(statement).strip()) for key,statement in queries.items()}
    if any(counts.values()):
        raise RuntimeError('기존 미종결/실행 중 작업이 있어 회귀 시작을 거부합니다: '+json.dumps(counts,sort_keys=True))
    return counts

def validate_topology(rows,project):
    expected={'mysql':('3306/tcp',13306),'redis':('6379/tcp',16379),'status-cache-redis':('6379/tcp',16380),'kafka':('9094/tcp',19092),'mock-ai':('8080/tcp',18081),'connect':('8083/tcp',18083)}
    by_service={row['service']:row for row in rows}
    for service,(inside,outside) in expected.items():
        row=by_service.get(service)
        if row is None or row['project']!=project or row['running'] is not True or row['ports'].get(inside)!=[{'HostIp':'127.0.0.1','HostPort':str(outside)}]:raise ValueError('시험 서비스/localhost 포트 소유권 불일치: '+service)

def verify_topology():
    require_experiment();ids=runtime.compose(['--profile','cdc','--profile','status-cache','ps','-q'],True).stdout.splitlines();rows=[]
    for identity in ids:
        value=json.loads(subprocess.run(['docker','inspect',identity],env=runtime.environment(),text=True,capture_output=True,check=True).stdout)[0];labels=value['Config']['Labels']
        rows.append(dict(service=labels.get('com.docker.compose.service'),project=labels.get('com.docker.compose.project'),id=value['Id'],running=value['State']['Running'],ports=value['NetworkSettings']['Ports']))
    validate_topology(rows,runtime.PROJECT);return rows

def root_command(client='mysql',extra=()):
    if client not in ('mysql','mysqldump'):raise ValueError('DB 도구 확인 필요')
    # 자격 정보는 컨테이너의 비밀 파일에서만 읽는다.
    suffix=' '.join(extra)
    return ['docker','compose','-p',runtime.PROJECT,'-f',str(runtime.WORKSPACE/'docker-compose.portfolio.yml'),
        'exec','-T','mysql','sh','-c','MYSQL_PWD="$(cat /run/secrets/mysql-root)" '+client+' -u root '+suffix]

def sql(statement,write=False):
    if write:require_experiment()
    elif not re.match(r'^\s*(SELECT|SHOW)\b',statement,re.I) or any(mark in statement for mark in (';','--','/*','*/','#','\x00')) or re.search(r'\b(INSERT|UPDATE|DELETE|REPLACE|ALTER|DROP|TRUNCATE|CREATE|GRANT|REVOKE|CALL|INTO|LOAD_FILE|SLEEP|BENCHMARK|GET_LOCK|RELEASE_LOCK)\b',statement,re.I):
        # 내부 고정 진단문만 받는다. 외부 입력을 받는 SQL API가 아니다.
        raise ValueError('읽기 SQL 확인 필요')
    result=subprocess.run(root_command(extra=('-D sentinel_portfolio --batch --raw --skip-column-names',)),
        input=statement,env=runtime.environment(),text=True,capture_output=True)
    if result.returncode:
        code=re.search(r'ERROR\s+[0-9]+\s*\([^)]*\)',result.stderr or '')
        raise RuntimeError('DB 명령 실패: '+(code.group(0) if code else 'client-error'))
    return result.stdout

def financial():
    text=sql(FINANCIAL_SQL);rows=len(text.splitlines());sha=hashlib.sha256(text.encode()).hexdigest()
    if rows!=149976 or sha!='282ba71516abf75b3bd414a6f2db753a0181147aad4115923de8b48f6cfb4f09':
        raise RuntimeError('실제 재무 자료 지문 불일치')
    return dict(rows=rows,sha256=sha,active=sql("SELECT snapshot_id FROM active_data_snapshots WHERE family='FINANCIAL'").strip())

def write_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('x') as file:json.dump(value,file,indent=2)

def dump(path,tables=None,schema=False):
    options=['--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--default-character-set=utf8mb4','--hex-blob']
    options+=['--no-data'] if schema else ['--no-create-info','--skip-add-locks','--order-by-primary']
    options+=['sentinel_portfolio']
    if tables:
        if any(not re.fullmatch(r'[A-Za-z0-9_]+',name) for name in tables):raise ValueError('dump 테이블 확인 필요')
        options+=list(tables)
    descriptor=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(descriptor,'wb') as file:result=subprocess.run(root_command('mysqldump',options),env=runtime.environment(),stdout=file,stderr=subprocess.PIPE)
    if result.returncode:raise RuntimeError('시험 dump 실패; 비공개 부분파일 보존')
    return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())

def import_sql(path):
    require_experiment()
    with path.open('rb') as file:result=subprocess.run(root_command(extra=('-D sentinel_portfolio --binary-mode',)),env=runtime.environment(),stdin=file,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:raise RuntimeError('시험 DB 복원 실패; 원본은 변경하지 않음')

def capture_origin():
    if runtime.NAMESPACE!='portfolio':raise ValueError('원본 읽기 환경 확인 필요')
    if runtime.owned_process() is not None:raise RuntimeError('기존 사용자 앱이 실행 중입니다')
    runtime.sync_workspace();runtime.setup();PRIVATE.mkdir(parents=True,exist_ok=True,mode=0o700)
    baseline=financial();q=sql('SELECT id FROM quarantined_messages ORDER BY id').splitlines()
    if len(q)!=828:raise RuntimeError('원본 격리 기록 변경 감지')
    existing=sql('SHOW TABLES').splitlines()
    counts={line.split('\t')[0]:int(line.split('\t')[1]) for line in sql(' UNION ALL '.join("SELECT '"+table+"',COUNT(*) FROM `"+table+'`' for table in existing)).splitlines()}
    if counts['report_requests'] or counts['report_artifacts']:raise RuntimeError('원본에 새 작업이 있어 보존 정지할 수 없음')
    proof=dict(financial=baseline,counts=counts,quarantineIds=q,project=runtime.PROJECT,seedTables=SEED_TABLES)
    seed=PRIVATE/'seed';seed.mkdir(mode=0o700,exist_ok=True)
    proof['schema']=dump(seed/'schema.sql',schema=True);proof['data']=dump(seed/'data.sql',SEED_TABLES)
    write_json(seed/'manifest.json',proof);write_json(RESULTS/'origin-baseline.json',proof)
    # 원본 volume/history는 보존하고 새 시험 환경과 같은 포트를 순차 사용한다.
    runtime.compose(['--profile','cdc','--profile','status-cache','stop'])
    print(json.dumps(dict(origin='stopped-preserved',financial=baseline,quarantines=len(q),seedBytes=proof['data']['bytes'])),flush=True)

def prepare(seed=True):
    require_experiment();runtime.sync_workspace();runtime.setup()
    marker=runtime.STATE/'operations-owned.json'
    if marker.exists():
        if json.loads(marker.read_text()).get('project')!=runtime.PROJECT:raise RuntimeError('시험 소유 marker 불일치')
    else:write_json(marker,dict(project=runtime.PROJECT,run=RUN,namespace=runtime.NAMESPACE))
    runtime.compose(['up','-d','--build','--wait','--wait-timeout','180','mysql','redis','kafka','mock-ai'])
    if seed:
        tables=sql('SHOW TABLES').splitlines()
        if not tables:
            directory=PRIVATE/'seed';manifest=json.loads((directory/'manifest.json').read_text())
            for name,key in [('schema.sql','schema'),('data.sql','data')]:
                file=directory/name
                if hashlib.sha256(file.read_bytes()).hexdigest()!=manifest[key]['sha256']:raise ValueError('seed 지문 불일치')
                import_sql(file)
            sql("INSERT INTO report_admission(scope_key,used_count) VALUES('GLOBAL',0)",write=True)
        financial()
    runtime.compose(['--profile','status-cache','up','-d','--wait','--wait-timeout','60','status-cache-redis'])
    if seed:configure_delivery()
    print(json.dumps(dict(namespace=runtime.NAMESPACE,prepared=True,financial=financial() if seed else None)),flush=True)

def configure_delivery():
    require_experiment()
    write=runtime.STATE/'relay-mode.json'
    if not write.exists():write_json(write,dict(relayMode='cdc',basis='isolated validation of selected CDC; business delivery still verified by each run'))
    import cdc_runtime as cdc
    cdc.prepare();cdc.ensure_mysql_configuration()
    runtime.compose(['--profile','cdc','up','-d','--wait','--wait-timeout','180','connect']);cdc.wait_worker()
    import configure_cdc
    configure_cdc.configure()

def original_resume():
    if runtime.NAMESPACE!='portfolio':raise ValueError('원본 재개 환경 확인 필요')
    # 공유 mock 이미지 tag가 바뀌어도 원래 컨테이너의 image ID를 그대로 재개한다.
    runtime.compose(['--profile','cdc','--profile','status-cache','start'])
    deadline=time.monotonic()+180
    while True:
        try:runtime.verify();break
        except (OSError,ValueError,subprocess.CalledProcessError):
            if time.monotonic()>deadline:raise RuntimeError('원본 컨테이너 재개 관찰 한도 초과')
            time.sleep(1)
    baseline=json.loads((RESULTS/'origin-baseline.json').read_text())
    if financial()!=baseline['financial'] or sql('SELECT id FROM quarantined_messages ORDER BY id').splitlines()!=baseline['quarantineIds']:
        raise RuntimeError('원본 보존 대조 실패')
    print('원본 재개/재무·격리 동일성 확인',flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['origin','prepare','prepare-empty','stop','original-resume','financial']);args=parser.parse_args()
    if args.action=='origin':capture_origin()
    elif args.action=='prepare':prepare()
    elif args.action=='prepare-empty':prepare(False)
    elif args.action=='stop':require_experiment();runtime.stop_app();runtime.compose(['--profile','cdc','--profile','status-cache','stop'])
    elif args.action=='original-resume':original_resume()
    else:print(json.dumps(financial()))

if __name__=='__main__':main()
