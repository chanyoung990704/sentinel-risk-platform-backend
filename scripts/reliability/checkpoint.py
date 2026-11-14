"""정지한 시험 DB/파일만 담고 새 restore broker에서 재전달하는 복원 절차."""
import argparse,hashlib,json,os,shutil,stat,subprocess,time,zipfile
from pathlib import Path,PurePosixPath
import local_runtime as runtime
import operations_runtime as ops

FILES=('schema.sql','data.sql','uploads.zip')

def digest(path):
    value=hashlib.sha256()
    with path.open('rb') as file:
        for block in iter(lambda:file.read(1024*1024),b''):value.update(block)
    return dict(bytes=path.stat().st_size,sha256=value.hexdigest())

def validate(root):
    root=Path(root);manifest=json.loads((root/'manifest.json').read_text())
    if manifest.get('version')!=1 or manifest.get('quiescent') is not True or manifest.get('running')!=0 or manifest.get('sourceProject')!='sentinel-operations':
        raise ValueError('정지/소유 checkpoint 조건 불일치')
    if set(manifest.get('files',{}))!=set(FILES):raise ValueError('checkpoint 필수 파일 불일치')
    for name in FILES:
        path=root/name
        if path.is_symlink() or not path.is_file() or digest(path)!=manifest['files'][name]:raise ValueError('checkpoint 파일 지문 불일치')
    return manifest

def validate_archive(path):
    with zipfile.ZipFile(path) as archive:
        seen=set()
        for info in archive.infolist():
            name=info.filename;parts=PurePosixPath(name).parts
            if name.startswith('/') or '\\' in name or ':' in name or '..' in parts or name in seen or stat.S_ISLNK(info.external_attr>>16):
                raise ValueError('checkpoint 압축 경로 거부')
            seen.add(name)
        if archive.testzip() is not None:raise ValueError('checkpoint 압축 손상')

def app_stopped():
    import operations_app
    if operations_app.owned() is not None:raise RuntimeError('checkpoint 전에 소유 앱 정지 필요')
    # 소유 실행기는 ready와 실제 명령을 함께 대조한다. 다른 프로세스에는 신호를 보내지 않는다.
    for ready in (runtime.STATE/'m05/app-ready.json',runtime.STATE/'r07-m04/browser-ready.json'):
        if ready.exists():
            pid=json.loads(ready.read_text())['pid'];proc=Path(f'/proc/{pid}/stat')
            if proc.exists() and proc.read_text().split()[2]!='Z':raise RuntimeError('checkpoint 전에 앱 정지 필요')
    if runtime.owned_process() is not None:raise RuntimeError('checkpoint 전에 앱 정지 필요')

def require_fresh_broker():
    ops.require_experiment()
    if runtime.NAMESPACE!='restore':raise ValueError('복원 broker 환경 확인 필요')
    # DB만 비워진 부분 복구에서도 과거 Kafka/Connect 위치를 재사용하지 않는다.
    volume=runtime.PROJECT+'_kafka-data'
    volumes=subprocess.run(['docker','volume','ls','--filter','name=^'+volume+'$','--format','{{.Name}}'],env=runtime.environment(),capture_output=True,text=True,check=True).stdout.splitlines()
    services=subprocess.run(['docker','ps','-a','--filter','label=com.docker.compose.project='+runtime.PROJECT,'--format','{{.Label "com.docker.compose.service"}}'],env=runtime.environment(),capture_output=True,text=True,check=True).stdout.splitlines()
    delivery=[name for name in services if name in ('kafka','connect')]
    if volumes or delivery:raise ValueError('기존 broker/Connect 상태 재사용 거부; 소유 자료 보존 후 별도 빈 환경 필요')
    return dict(project=runtime.PROJECT,preExistingKafkaVolumes=volumes,preExistingDeliveryContainers=delivery)

def verify_artifact_files(upload):
    if any(path.is_symlink() for path in (Path(upload),*Path(upload).parents)):raise ValueError('업로드 링크 경로 거부')
    root=Path(upload).resolve();verified=[]
    statement='SELECT a.request_id,a.storage_key,a.file_size,a.sha256,f.id,f.storage_key,f.file_size,f.content_type,f.storage_url FROM report_artifacts a LEFT JOIN files f ON f.id=a.file_id ORDER BY a.request_id'
    for line in ops.sql(statement).splitlines():
        request,key,size,expected,file_id,file_key,metadata_size,content_type,storage_url=line.split('\t');relative=PurePosixPath(key)
        if relative.is_absolute() or '..' in relative.parts or '\\' in key:raise ValueError('DB artifact 경로 거부')
        path=root/key
        if file_id=='NULL' or file_key!=key or metadata_size!=size or content_type!='application/pdf' or storage_url!=str(path):raise ValueError('DB artifact 파일 참조 불일치')
        if not path.resolve().is_relative_to(root) or any(part.is_symlink() for part in (path,*path.parents)) or not path.is_file():raise ValueError('DB artifact 파일 누락/경로 불일치')
        actual=digest(path)
        if actual!={'bytes':int(size),'sha256':expected}:raise ValueError('DB artifact 파일 크기/SHA 불일치')
        verified.append(dict(requestId=request,key=key,**actual))
    return verified

def capture(name):
    ops.require_experiment()
    if runtime.NAMESPACE!='operations' or not name.isalnum():raise ValueError('checkpoint 소유 이름 확인 필요')
    ops.verify_topology();app_stopped()
    running=int(ops.sql("SELECT COUNT(*) FROM report_jobs WHERE state='RUNNING'").strip())
    if running:raise RuntimeError('RUNNING 작업은 정지 checkpoint 대상이 아닙니다')
    artifacts=verify_artifact_files(runtime.STATE/'uploads')
    import cdc_runtime as cdc
    import configure_cdc
    source_before=cdc.request('/connectors/'+configure_cdc.NAME+'/offsets')
    cdc.request('/connectors/'+configure_cdc.NAME+'/stop','PUT')
    configure_cdc.wait_transition('STOPPED',lambda state:state['connector']['state']=='STOPPED' and not state.get('tasks'))
    source_stopped=cdc.request('/connectors/'+configure_cdc.NAME+'/offsets')
    runtime.compose(['--profile','cdc','stop','connect'])
    root=ops.PRIVATE/'checkpoints'/name;root.mkdir(parents=True,mode=0o700,exist_ok=False)
    files={'schema.sql':ops.dump(root/'schema.sql',schema=True),'data.sql':ops.dump(root/'data.sql')}
    upload=runtime.STATE/'uploads';members=[]
    with zipfile.ZipFile(root/'uploads.zip','x',zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(upload.rglob('*')):
            if path.is_symlink():raise RuntimeError('업로드 symlink 거부')
            if path.is_file():
                rel=path.relative_to(upload).as_posix();archive.write(path,rel);members.append(dict(path=rel,**digest(path)))
    os.chmod(root/'uploads.zip',0o600);files['uploads.zip']=digest(root/'uploads.zip')
    # fixture는 private에만 복사한다. 로그인 토큰/비밀 키와 기존 broker offset은 복원하지 않는다.
    shutil.copy2(runtime.STATE/'m05/fixture.json',root/'fixture.json');os.chmod(root/'fixture.json',0o600)
    offsets=runtime.compose(['exec','-T','kafka','/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','kafka:9092','--all-groups','--describe'],True)
    (root/'source-offsets.txt').write_text(offsets.stdout);os.chmod(root/'source-offsets.txt',0o600)
    manifest=dict(version=1,quiescent=True,running=running,sourceProject=runtime.PROJECT,files=files,
        fixture=digest(root/'fixture.json'),uploads=members,artifacts=artifacts,financial=ops.financial(),
        counts={table:int(ops.sql('SELECT COUNT(*) FROM '+table).strip()) for table in ('report_requests','report_jobs','report_attempts','report_artifacts','inbox_events','outbox_events')},
        sourceConnectorBefore=source_before,sourceConnectorStopped=source_stopped,kafkaPositionFile=digest(root/'source-offsets.txt'),
        connectorConfigSha256=digest(runtime.WORKSPACE/'infra/cdc/portfolio-outbox.json')['sha256'],
        offsetPolicy='fresh broker and connector; replay Outbox snapshot; preserve restored Inbox/jobs; never attach source offsets',createdEpoch=time.time())
    ops.write_json(root/'manifest.json',manifest);validate(root);validate_archive(root/'uploads.zip')
    ops.write_json(ops.RESULTS/(name+'-checkpoint.json'),{key:value for key,value in manifest.items() if key not in ('fixture',)})
    print(json.dumps(dict(checkpoint=name,counts=manifest['counts'],verified=True)))

def restore(name):
    ops.require_experiment()
    if runtime.NAMESPACE!='restore' or not name.isalnum():raise ValueError('복원 namespace/name 확인 필요')
    root=ops.PRIVATE/'checkpoints'/name;manifest=validate(root);validate_archive(root/'uploads.zip')
    if digest(root/'fixture.json')!=manifest['fixture']:raise ValueError('private fixture 지문 불일치')
    app_stopped();freshness=require_fresh_broker();ops.prepare(False)
    if ops.sql('SHOW TABLES').strip():raise RuntimeError('비어 있지 않은 복원 DB 덮어쓰기 거부')
    upload=runtime.STATE/'uploads'
    if any(upload.iterdir()):raise RuntimeError('비어 있지 않은 업로드 덮어쓰기 거부')
    ops.import_sql(root/'schema.sql');ops.import_sql(root/'data.sql')
    with zipfile.ZipFile(root/'uploads.zip') as archive:archive.extractall(upload)
    # 절대 파일 경로는 격리된 복원 디렉터리로만 치환한다.
    source=str(runtime.namespace_config('operations')['state']/'uploads')
    target=str(upload)
    ops.sql("UPDATE files SET storage_url=REPLACE(storage_url,'"+source+"','"+target+"') WHERE storage_url LIKE '"+source+"/%'",write=True)
    private=runtime.STATE/'m05';private.mkdir(mode=0o700,exist_ok=True)
    shutil.copy2(root/'fixture.json',private/'fixture.json');os.chmod(private/'fixture.json',0o600)
    for member in manifest['uploads']:
        if digest(upload/member['path'])!={key:member[key] for key in ('bytes','sha256')}:raise RuntimeError('복원된 파일 불일치')
    if ops.financial()!=manifest['financial']:raise RuntimeError('복원 재무 지문 불일치')
    artifacts=verify_artifact_files(upload)
    ops.configure_delivery()
    ops.verify_topology()
    ops.write_json(ops.RESULTS/(name+'-restored.json'),dict(checkpoint=name,project=runtime.PROJECT,files=len(manifest['uploads']),artifactsVerified=len(artifacts),financial=ops.financial(),freshBroker=True,freshBrokerPrecondition=freshness,sourceOffsetsReused=False))
    print(json.dumps(dict(restored=name,files=len(manifest['uploads']),financialRows=manifest['financial']['rows'])))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('capture','restore'));parser.add_argument('name');args=parser.parse_args()
    (capture if args.action=='capture' else restore)(args.name)
