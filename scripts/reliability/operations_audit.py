"""최종 시험 상태와 원본 보존을 읽기 전용으로 대조한다. 비밀 값은 출력하지 않는다."""
import argparse,hashlib,json,os,subprocess,time,zipfile
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops
from capture_baseline import fingerprint
from checkpoint import digest

def containers():
    result=[]
    for identity in runtime.compose(['--profile','cdc','--profile','status-cache','ps','-a','-q'],True).stdout.splitlines():
        raw=json.loads(subprocess.run(['docker','inspect',identity],env=runtime.environment(),capture_output=True,text=True,check=True).stdout)[0]
        result.append(dict(id=raw['Id'],service=raw['Config']['Labels']['com.docker.compose.service'],project=raw['Config']['Labels']['com.docker.compose.project'],image=raw['Config']['Image'],imageId=raw['Image'],state=raw['State']['Status']))
    return result

def owned_apps():
    found=[];signatures=(b'PortfolioOperationsProcess.java',b'ReportDeliveryApplicationProcess.java',b'PortfolioBrowserProcess.java')
    for directory in Path('/proc').iterdir():
        if not directory.name.isdigit():continue
        try:
            command=(directory/'cmdline').read_bytes()
            if (directory/'cwd').resolve()==runtime.WORKSPACE and any(signature in command for signature in signatures):found.append(int(directory.name))
        except (OSError,PermissionError):pass
    return found

def restore_audit(label):
    ops.require_experiment()
    if runtime.NAMESPACE!='restore' or not label.isalnum():raise ValueError('최종 시험 감사 환경 확인 필요')
    apps=owned_apps()
    if apps:raise RuntimeError('소유 앱 종료가 먼저 필요합니다')
    topology=ops.verify_topology();financial=ops.financial();source=fingerprint(runtime.SOURCE);native=fingerprint(runtime.WORKSPACE)
    mismatch=sorted(key for key in source.keys()|native.keys() if source.get(key)!=native.get(key))
    if mismatch:raise RuntimeError('최종 실행 소스 불일치; 원자료를 보존합니다')
    admission=int(ops.sql("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'").strip())
    nonterminal=int(ops.sql("SELECT COUNT(*) FROM report_requests WHERE status NOT IN ('SUCCEEDED','FAILED')").strip())
    if admission or nonterminal:raise RuntimeError('최종 미종결 요청/슬롯을 조사해야 합니다')
    mock=runtime.compose(['exec','-T','mock-ai','python','-c',"import hashlib,pathlib; print(hashlib.sha256(pathlib.Path('/app/server.py').read_bytes()).hexdigest())"],True).stdout.strip()
    if mock!=digest(runtime.SOURCE/'infra/mock-ai/server.py')['sha256']:raise RuntimeError('실행 모의 서버 소스 불일치')
    root=(runtime.STATE/'uploads').resolve();files=[]
    for line in ops.sql('SELECT request_id,storage_key,file_size,sha256 FROM report_artifacts ORDER BY request_id').splitlines():
        request,key,size,expected=line.split('\t');path=(root/key).resolve()
        if not path.is_relative_to(root) or path.is_symlink() or not path.is_file() or digest(path)!={'bytes':int(size),'sha256':expected}:raise RuntimeError('최종 실제 PDF 불일치')
        files.append(dict(requestId=request,key=key,bytes=int(size),sha256=expected))
    private=ops.PRIVATE/('final-restore-'+label);private.mkdir(mode=0o700,exist_ok=False)
    schema=ops.dump(private/'schema.sql',schema=True);data=ops.dump(private/'data.sql')
    archive=private/'uploads.zip'
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as zipped:
        for path in sorted(root.rglob('*')):
            if path.is_symlink():raise ValueError('업로드 링크 거부')
            if path.is_file():zipped.write(path,path.relative_to(root).as_posix())
    os.chmod(archive,0o600)
    report=dict(epoch=time.time(),project=runtime.PROJECT,financial=financial,sourceSha256=source,sourceMismatch=mismatch,ownedApps=apps,admission=admission,nonterminal=nonterminal,topology=topology,containers=containers(),runningMockSourceSha256=mock,files=files,retainedSchema=schema,retainedData=data,retainedUploads=digest(archive),quarantineCount=int(ops.sql('SELECT COUNT(*) FROM quarantined_messages').strip()))
    ops.write_json(ops.RESULTS/('restore-final-audit-'+label+'.json'),report)
    print(json.dumps(dict(audit=label,rows=financial['rows'],pdfs=len(files),admission=admission,nonterminal=nonterminal,sourceMismatch=0)))

def original_audit(label):
    if runtime.NAMESPACE!='portfolio' or not label.isalnum():raise ValueError('원본 보존 감사 환경 확인 필요')
    baseline=json.loads((ops.RESULTS/'origin-baseline.json').read_text());financial=ops.financial()
    tables=ops.sql('SHOW TABLES').splitlines()
    if any(not __import__('re').fullmatch('[A-Za-z0-9_]+',table) for table in tables):raise ValueError('테이블 이름 확인 필요')
    counts={line.split('\t')[0]:int(line.split('\t')[1]) for line in ops.sql(' UNION ALL '.join("SELECT '"+table+"',COUNT(*) FROM `"+table+'`' for table in tables)).splitlines()}
    quarantines=ops.sql('SELECT id FROM quarantined_messages ORDER BY id').splitlines()
    if financial!=baseline['financial'] or counts!=baseline['counts'] or quarantines!=baseline['quarantineIds']:raise RuntimeError('원본 재무/행수/격리ID 보존 불일치')
    linked=int(ops.sql("SELECT COUNT(*) FROM quarantined_messages q JOIN outbox_events o ON o.id=q.dlt_event_id AND o.aggregateid=q.id AND o.type='MESSAGE_QUARANTINED'").strip())
    if linked!=828:raise RuntimeError('원본 격리-Outbox 연결 불일치')
    current_containers=containers();old=json.loads((runtime.SOURCE/'perf/results/20261112-r07-m03-m04/cleanup-verified-audit.json').read_text())['containers'];old_images={row['service']:row['imageId'] for row in old}
    image_comparison={row['service']:row['imageId']==old_images[row['service']] for row in current_containers if row['service'] in old_images}
    source_apps=owned_apps()
    report=dict(epoch=time.time(),project=runtime.PROJECT,financial=financial,counts=counts,quarantineIds=quarantines,linkedQuarantineOutboxes=linked,containers=current_containers,historicalImageComparison=image_comparison,ownedApps=source_apps,allOriginalTablesSameCounts=True,originalVolumesReset=False,originalOffsetsReset=False,limit='격리 ID/관련 Outbox/전체 테이블 행수와 활성 재무 전체 지문 대조이며 모든 비재무 행 본문의 별도 이전 지문은 수집하지 않았다')
    ops.write_json(ops.RESULTS/('origin-final-audit-'+label+'.json'),report)
    print(json.dumps(dict(audit=label,originalRows=financial['rows'],quarantines=len(quarantines),linkedOutboxes=linked,allTableCountsEqual=True)))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=('restore','original'));parser.add_argument('label');args=parser.parse_args();(restore_audit if args.action=='restore' else original_audit)(args.label)
