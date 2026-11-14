"""고정 매트릭스를 실제 Gradle 시험에 연결하며 미실행/skip을 성공으로 만들지 않는다."""
import argparse,hashlib,json,os,shutil,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops
from capture_baseline import fingerprint

SCOPES={
 'realInfraTest':['ReportAcceptanceIntegrationTest','ReportWorkerLeaseIntegrationTest','ReportGenerationContractIntegrationTest','ReportArtifactIntegrationTest','ReportFinalReviewIntegrationTest','ReportStatusCacheIntegrationTest','CustomReportApiIntegrationTest','ImportResumeIntegrationTest','SnapshotVisibilityIntegrationTest','ReportConsumerAckIntegrationTest'],
 'cdcTest':['OutboxCdcIntegrationTest'],
 'reliabilityTest':['ReportAcceptanceCrashTest','ReportDeliveryRecoveryTest','PortfolioFaultMatrixTest','StatusCacheFailureTest']}

def xml_cases(directory):
    result=[]
    for path in sorted(directory.glob('TEST-*.xml')):
        root=ET.parse(path).getroot()
        for case in root.findall('testcase'):
            state='failed' if case.find('failure') is not None or case.find('error') is not None else 'skipped' if case.find('skipped') is not None else 'passed'
            result.append(dict(className=case.get('classname'),name=case.get('name'),state=state,seconds=float(case.get('time','0')),xml=directory.name+'/'+path.name))
    if not result:raise RuntimeError('실행 XML이 없습니다')
    return result

def execute(seed,attempt=1):
    ops.require_experiment()
    if runtime.NAMESPACE!='restore' or seed not in (1,2,3):raise ValueError('행렬은 복원 시험 환경만 사용합니다')
    import operations_app
    if operations_app.owned():raise RuntimeError('행렬 시작 전에 소유 앱 정지 필요')
    topology=ops.verify_topology()
    if not 1<=attempt<=20:raise ValueError('시도 번호 확인 필요')
    label=f'm06-seed{seed}'+('' if attempt==1 else f'-attempt{attempt}')
    runtime.sync_workspace();runtime.setup();out=ops.RESULTS/label;out.mkdir(exist_ok=False)
    if int(ops.sql("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'").strip())!=0 or int(ops.sql("SELECT COUNT(*) FROM report_jobs WHERE state='RUNNING'").strip()):raise RuntimeError('이전 미종결 작업을 확인해야 합니다')
    native=runtime.STATE/'matrix'/label;native.mkdir(parents=True,mode=0o700,exist_ok=False)
    env=runtime.environment();env.update(PORTFOLIO_K_RUN_LABEL=label,PORTFOLIO_K_RUN_DIR=str(native/'raw'),PORTFOLIO_CDC_RUN_ID=label,PORTFOLIO_CDC_RUN_DIR=str(native/'cdc'))
    manifest=dict(seed=seed,repeatMeaning='independent UUID fixtures; zero retry jitter in delivery process; not a seeded traffic benchmark',namespace=runtime.NAMESPACE,project=runtime.PROJECT,sourceSha256=fingerprint(runtime.SOURCE),financialBefore=ops.financial(),matrix=json.loads((runtime.SOURCE/'perf/fixtures/fault-matrix.json').read_text()),started=time.time())
    ids=runtime.compose(['--profile','cdc','--profile','status-cache','ps','-q'],True).stdout.splitlines();containers=[]
    for identity in ids:
        container=json.loads(subprocess.run(['docker','inspect',identity],env=env,text=True,capture_output=True,check=True).stdout)[0]
        containers.append(dict(name=container['Name'],id=container['Id'],imageReference=container['Config']['Image'],imageId=container['Image'],memoryLimit=container['HostConfig']['Memory'],running=container['State']['Running']))
    manifest.update(attempt=attempt,topology=topology,containers=containers,memory=Path('/proc/meminfo').read_text().splitlines()[0],jdkTestHeapMiB=512,deliveryChildHeapMiB=384,imageLocks={name:json.loads((runtime.WORKSPACE/name).read_text()) for name in ('infra/portfolio-images.lock.json','infra/cdc/images.lock.json')})
    ops.write_json(out/'manifest.json',manifest);all_cases=[];commands=[]
    for task,classes in SCOPES.items():
        command=['bash','./gradlew',task,'--no-daemon','--console=plain']
        for name in classes:command+=['--tests','*'+name]
        start=time.time()
        with (out/(task+'.log')).open('x') as log:result=subprocess.run(command,cwd=runtime.WORKSPACE,env=env,stdout=log,stderr=subprocess.STDOUT)
        target=out/task;source_xml=runtime.WORKSPACE/'build/test-results'/task
        fresh=source_xml.exists() and any(path.stat().st_mtime>=start for path in source_xml.glob('TEST-*.xml'))
        cases=[]
        if fresh:
            target.mkdir()
            for path in source_xml.glob('TEST-*.xml'):
                if path.stat().st_mtime>=start:shutil.copy2(path,target/path.name)
            cases=xml_cases(target)
        all_cases+=cases;commands.append(dict(task=task,command=command,started=start,finished=time.time(),exitCode=result.returncode,cases=cases))
        ops.write_json(out/(task+'-command.json'),commands[-1]);print(json.dumps(dict(seed=seed,task=task,exitCode=result.returncode,tests=len(cases))),flush=True)
        if result.returncode:break
    for name in ('raw','cdc'):
        if (native/name).exists():shutil.copytree(native/name,out/name)
    coverage=[]
    for case in manifest['matrix']['cases']:
        if case['id']=='F15':coverage.append(dict(id=case['id'],state='N/A',reason=case['reason']));continue
        matched={method:[row for row in all_cases if row['name'].startswith(method+'(') or row['name'].startswith(method+'[') or row['name']==method] for method in case['methods']}
        passed=all(rows and all(row['state']=='passed' for row in rows) for rows in matched.values())
        coverage.append(dict(id=case['id'],state='passed' if passed else 'incomplete-or-failed',level=case['level'],requests=case['requests'],tests=matched))
    summary=dict(seed=seed,coverage=coverage,tests=len(all_cases),passed=sum(row['state']=='passed' for row in all_cases),failed=sum(row['state']=='failed' for row in all_cases),skipped=sum(row['state']=='skipped' for row in all_cases),financialAfter=ops.financial(),finished=time.time())
    summary['complete']=len(commands)==len(SCOPES) and all(row['exitCode']==0 for row in commands) and all(row['state'] in ('passed','N/A') for row in coverage) and summary['financialAfter']==manifest['financialBefore']
    ops.write_json(out/'summary.json',summary)
    if not summary['complete']:raise RuntimeError('행렬 미완료/실패; 기존 원자료를 보존합니다')
    return summary

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--seed',type=int,required=True,choices=(1,2,3));parser.add_argument('--attempt',type=int,default=1);args=parser.parse_args();result=execute(args.seed,args.attempt);print(json.dumps(dict(seed=args.seed,complete=result['complete'],tests=result['tests'],passed=result['passed'])))
