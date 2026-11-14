"""M05 정지 checkpoint/새 broker 복원을 실제 HTTP와 파일 hash로 검증한다."""
import argparse,concurrent.futures,hashlib,http.client,json,os,shutil,subprocess,time,uuid,zipfile
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops
import operations_app as app
import checkpoint
from reconcile import reconcile
from capture_baseline import fingerprint

def request(method,path,body=None,token=None,key=None):
    connection=http.client.HTTPConnection('127.0.0.1',18080,timeout=20);headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if key:headers['Idempotency-Key']=key
    try:
        connection.request(method,path,json.dumps(body) if body is not None else None,headers);response=connection.getresponse();raw=response.read()
        return response.status,json.loads(raw) if 'json' in response.getheader('Content-Type','') else raw
    finally:connection.close()

def login_all():
    fixture=json.loads((app.STATE/'fixture.json').read_text());tokens=[]
    for user in fixture['users']:
        code,body=request('POST','/api/auth/login',dict(email=user['email'],password=user['password']))
        if code!=200:raise RuntimeError('실제 로그인 실패 HTTP '+str(code))
        tokens.append(body['data']['accessToken'])
    return fixture,tokens

def post(company,token,key):
    return request('POST',f'/api/companies/{company}/custom-report-requests',dict(additionalInstructions='운영 복원 실제 자료 검증'),token,key)

def rows(ids):
    if not ids:return []
    quoted=','.join("'"+str(uuid.UUID(value))+"'" for value in ids)
    result=ops.sql("SELECT r.id,r.status,(SELECT COUNT(*) FROM report_jobs j WHERE j.request_id=r.id),(SELECT COUNT(*) FROM report_artifacts a WHERE a.request_id=r.id),(SELECT COUNT(*) FROM report_artifacts a JOIN files f ON f.id=a.file_id WHERE a.request_id=r.id),r.admission_slot_held FROM report_requests r WHERE r.id IN ("+quoted+") ORDER BY r.id")
    return [dict(id=p[0],status=p[1],jobs=int(p[2]),artifacts=int(p[3]),files=int(p[4]),slot=int(p[5])) for p in (line.split('\t') for line in result.splitlines())]

def wait_for(predicate,seconds=180):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        value=predicate()
        if value:return value
        time.sleep(.2)
    raise RuntimeError('정해진 '+str(seconds)+'초 관찰 한도 초과')

def file_audit(ids):
    quoted=','.join("'"+str(uuid.UUID(value))+"'" for value in ids)
    raw=ops.sql("SELECT a.request_id,a.storage_key,a.file_size,a.sha256 FROM report_artifacts a WHERE a.request_id IN ("+quoted+") ORDER BY a.request_id")
    proofs=[];root=(runtime.STATE/'uploads').resolve()
    for line in raw.splitlines():
        request_id,key,size,sha=line.split('\t');file=(root/key).resolve()
        if not file.is_relative_to(root) or file.is_symlink() or not file.is_file() or file.stat().st_size!=int(size) or checkpoint.digest(file)['sha256']!=sha:raise RuntimeError('실제 PDF 보존 대조 실패')
        proofs.append(dict(requestId=request_id,storageKey=key,bytes=int(size),sha256=sha))
    if len(proofs)!=len(ids):raise RuntimeError('실제 PDF 개수 불일치')
    return proofs

def enqueue(seed):
    ops.require_experiment()
    if runtime.NAMESPACE!='operations':raise ValueError('operations 필요')
    if seed>1 and int(ops.sql('SELECT COUNT(*) FROM report_requests').strip()):
        reset_restore(seed);ops.prepare()
    app.start(True,build=seed==1);fixture,tokens=login_all();start=time.monotonic()
    prefix=f'm05-{seed}-'+uuid.uuid4().hex
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        result=list(pool.map(lambda n:post(fixture['company'],tokens[n%20],prefix+'-'+str(n)),range(100)))
    if any(code!=202 for code,_ in result):raise RuntimeError('100건 접수 실패')
    ids=[body['data']['requestId'] for _,body in result]
    if len(set(ids))!=100:raise RuntimeError('고유 수락 100건 불일치')
    replay=post(fixture['company'],tokens[0],prefix+'-0');overflow=post(fixture['company'],tokens[0],prefix+'-overflow')
    if replay[0]!=202 or replay[1]['data']['requestId']!=ids[0] or overflow[0] not in (429,503):raise RuntimeError('한도/멱등 재전송 계약 불일치')
    full=request('GET','/actuator/health/apiReady')[0]
    if full!=503 or int(ops.sql("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'").strip())!=100:raise RuntimeError('접수 한도 readiness 불일치')
    wait_for(lambda:int(ops.sql("SELECT COUNT(*) FROM report_jobs WHERE state='READY'").strip())==100)
    app.control('resume');wait_for(lambda:sum(row['status']=='SUCCEEDED' for row in rows(ids))>=15)
    app.control('pause');wait_for(lambda:int(ops.sql("SELECT COUNT(*) FROM report_jobs WHERE state='RUNNING'").strip())==0)
    app.stop();before=rows(ids);complete=[row['id'] for row in before if row['status']=='SUCCEEDED']
    if not complete or len(complete)==100:raise RuntimeError('완료/대기 혼합 checkpoint 조건 미충족')
    proof=dict(seed=seed,acceptedIds=ids,completedBefore=complete,before=before,filesBefore=file_audit(complete),
        replaySameRequest=True,overflowHttp=overflow[0],fullReadinessHttp=full,globalCap=100,userCap=100,users=20,elapsedSeconds=time.monotonic()-start,sourceSha256=json.loads((app.STATE/'source.json').read_text()))
    ops.write_json(ops.RESULTS/f'm05-seed{seed}-before.json',proof);checkpoint.capture('seed'+str(seed))
    runtime.compose(['--profile','cdc','--profile','status-cache','stop'])

def verify_restore(seed,resume=False):
    restore_started=time.monotonic()
    ops.require_experiment()
    if runtime.NAMESPACE!='restore':raise ValueError('restore 필요')
    before=json.loads((ops.RESULTS/f'm05-seed{seed}-before.json').read_text())
    if not resume:checkpoint.restore('seed'+str(seed))
    else:
        checkpoint.validate(ops.PRIVATE/'checkpoints'/('seed'+str(seed)))
        if not (ops.RESULTS/f'seed{seed}-restored.json').exists():raise ValueError('실제 복원 기록이 있어야 재대조 가능')
    if not app.owned():app.start(False,build=True)
    fixture,tokens=login_all();start=time.monotonic();plan=ops.RESULTS/f'm05-seed{seed}-new-request.json'
    if plan.exists():new=json.loads(plan.read_text())['requestId']
    elif resume:
        old=set(before['acceptedIds'])
        if seed==1:old.update(json.loads((ops.RESULTS/'m05-1-harness-failure-input.json').read_text())['acceptedIds'])
        delta=set(ops.sql('SELECT id FROM report_requests').splitlines())-old
        if len(delta)!=1:raise ValueError('복원 이후 신규 요청을 하나로 식별할 수 없음')
        new=delta.pop();ops.write_json(plan,dict(requestId=new,recoveredFromCommittedLedger=True))
    else:
        result=post(fixture['company'],tokens[0],f'restored-{seed}-'+uuid.uuid4().hex)
        if result[0]!=202:raise RuntimeError('복원 후 실제 신규 요청 접수 실패')
        new=result[1]['data']['requestId'];ops.write_json(plan,dict(requestId=new,recoveredFromCommittedLedger=False))
    ids=before['acceptedIds']+[new]
    wait_for(lambda:all(row['status']=='SUCCEEDED' for row in rows(ids)))
    final=rows(ids);result=reconcile(ids,final);files=file_audit(ids);original={r['requestId']:r for r in before['filesBefore']}
    if not result['passed'] or any(original.get(row['requestId'],row)!=row for row in files):raise RuntimeError('복원 후 최종 대조 실패')
    for request_id in before['completedBefore']:
        if int(ops.sql("SELECT COUNT(*) FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id='"+request_id+"'").strip())!=1:raise RuntimeError('기존 완료 재실행 감지')
    code,body=request('GET','/api/report-requests/'+new+'/file',token=tokens[0])
    if code!=200 or not body.startswith(b'%PDF-'):raise RuntimeError('복원 후 본인 실제 파일 다운로드 실패')
    ops.write_json(ops.RESULTS/f'm05-seed{seed}-after.json',dict(seed=seed,reconciliation=result,rows=final,files=files,newRequest=new,oldCompletedRetained=len(original),drainSeconds=time.monotonic()-start,restoreInvocationSeconds=time.monotonic()-restore_started,resumedAudit=resume,financial=ops.financial(),downloadHttp=code,sourceOffsetsReused=False))
    app.stop();runtime.compose(['--profile','cdc','--profile','status-cache','stop'])

def reset_restore(seed):
    ops.require_experiment()
    if runtime.NAMESPACE not in ('operations','restore') or seed not in (2,3):raise ValueError('새 시험 namespace만 정리 가능')
    marker=json.loads((runtime.STATE/'operations-owned.json').read_text())
    if marker!={'project':runtime.PROJECT,'run':ops.RUN,'namespace':runtime.NAMESPACE}:raise ValueError('시험 소유 marker 불일치')
    previous=json.loads((ops.RESULTS/f'm05-seed{seed-1}-after.json').read_text())
    if not previous['reconciliation']['passed']:raise ValueError('이전 복원 완료 증거 없이는 초기화 불가')
    checkpoint.validate(ops.PRIVATE/'checkpoints'/('seed'+str(seed-1)))
    checkpoint.app_stopped()
    volumes=subprocess.run(['docker','volume','ls','--filter','label=com.docker.compose.project='+runtime.PROJECT,'--format','{{.Name}}'],env=runtime.environment(),capture_output=True,text=True,check=True).stdout.splitlines()
    if not volumes:raise RuntimeError('복원 시험 소유 volume 확인 필요')
    for volume in volumes:
        labels=json.loads(subprocess.run(['docker','volume','inspect',volume],env=runtime.environment(),capture_output=True,text=True,check=True).stdout)[0]['Labels']
        if labels.get('com.docker.compose.project')!=runtime.PROJECT:raise RuntimeError('원본/다른 volume 정리 거부')
    backup=ops.PRIVATE/f'{runtime.NAMESPACE}-finished-seed{seed-1}';backup.mkdir(mode=0o700,exist_ok=False)
    runtime.compose(['up','-d','--no-deps','--wait','--wait-timeout','120','mysql'])
    retained=ops.dump(backup/'data.sql');archive=backup/'uploads.zip'
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as zipped:
        for file in sorted((runtime.STATE/'uploads').rglob('*')):
            if file.is_symlink():raise ValueError('소유 파일 symlink 거부')
            if file.is_file():zipped.write(file,file.relative_to(runtime.STATE/'uploads').as_posix())
    os.chmod(archive,0o600)
    ops.write_json(ops.RESULTS/f'm05-reset-{runtime.NAMESPACE}-before-seed{seed}.json',dict(project=runtime.PROJECT,ownedVolumes=volumes,originalTouched=False,retainedData=retained,retainedUploads=checkpoint.digest(archive)))
    runtime.compose(['--profile','cdc','--profile','status-cache','down','-v'])
    upload=runtime.STATE/'uploads'
    if upload.resolve()!=runtime.namespace_config(runtime.NAMESPACE)['state']/'uploads':raise ValueError('정리 경로 확인 실패')
    shutil.rmtree(upload);upload.mkdir()
    prior=runtime.STATE/f'm05-previous-{seed-1}'
    app.STATE.rename(prior);(runtime.STATE/'mock-journal').rename(runtime.STATE/f'mock-journal-previous-{seed-1}')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('enqueue','restore','restore-resume','reset-restore'));parser.add_argument('seed',type=int,choices=(1,2,3));args=parser.parse_args()
    {'enqueue':enqueue,'restore':verify_restore,'restore-resume':lambda seed:verify_restore(seed,True),'reset-restore':reset_restore}[args.action](args.seed)
    print(json.dumps(dict(action=args.action,seed=args.seed,passed=True)),flush=True)
