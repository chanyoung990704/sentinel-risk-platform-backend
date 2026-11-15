"""Ubuntu 네이티브 실행과 XML/소스/명령을 한 번의 원장에 보존한다."""
import argparse,json,re,shutil,subprocess,time
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops
from capture_baseline import fingerprint
from fault_matrix import xml_cases
from verify_test_results import verify

TEST_SCOPES=('modulithTest','fastTest','realInfraTest','cdcTest','reliabilityTest')

def test_environment():
    # Gradle의 test 프로필이 H2를 선택하고 실제 인프라는 각 시험의 명시 설정을 사용한다.
    return {key:value for key,value in runtime.environment().items()
            if key!='SPRING_PROFILES_ACTIVE' and not key.startswith('SPRING_DATASOURCE_')}

def run(label,tasks,filters=()):
    ops.require_experiment()
    if not re.fullmatch('[a-z0-9-]+',label) or not set(tasks)<=set(('compileTestJava','modulithTest','fastTest','realInfraTest','cdcTest','reliabilityTest','bootJar','verifiedCheck','prepareImportCli')):raise ValueError('검증 범위 확인 필요')
    if any(task in tasks for task in ('realInfraTest','cdcTest','reliabilityTest','verifiedCheck')):
        ops.verify_topology();ops.require_idle()
    out=ops.RESULTS/label;out.mkdir(exist_ok=False);runtime.sync_workspace();runtime.setup();env=test_environment()
    native=runtime.STATE/'verification'/label;native.mkdir(parents=True,mode=0o700,exist_ok=False)
    env.update(PORTFOLIO_K_RUN_LABEL=label,PORTFOLIO_K_RUN_DIR=str(native/'raw'),PORTFOLIO_CDC_RUN_ID=label,PORTFOLIO_CDC_RUN_DIR=str(native/'cdc'))
    source=fingerprint(runtime.SOURCE);command=['bash','./gradlew',*tasks,'--no-daemon','--console=plain']
    if 'verifiedCheck' in tasks:command+=['-PoutboxMode=cdc']
    for selector in filters:command+=['--tests',selector]
    started=time.time()
    with (out/'console.log').open('x') as log:result=subprocess.run(command,cwd=runtime.WORKSPACE,env=env,stdout=log,stderr=subprocess.STDOUT)
    # aggregate의 의존 task가 만든 XML을 수집하며 누락된 범위도 필수 검증에 남긴다.
    required=list(TEST_SCOPES) if 'verifiedCheck' in tasks else [name for name in TEST_SCOPES if name in tasks]
    rows=[]
    for task in required:
        source_xml=runtime.WORKSPACE/'build/test-results'/task
        if source_xml.exists():
            shutil.copytree(source_xml,out/task,ignore=shutil.ignore_patterns('binary'))
            if any(source_xml.glob('TEST-*.xml')):rows+=xml_cases(out/task)
    for name in ('raw','cdc'):
        if (native/name).exists():shutil.copytree(native/name,out/name)
    current=fingerprint(runtime.SOURCE);execution=fingerprint(runtime.WORKSPACE)
    summary=verify(out,required,since=started) if required else None
    strict_source=runtime.WORKSPACE/'build/reports/portfolio-verification.json';strict=None
    if 'verifiedCheck' in tasks and strict_source.exists() and strict_source.stat().st_mtime>=started:
        shutil.copy2(strict_source,out/'strict-gate.json');strict=json.loads(strict_source.read_text())
    import xml.etree.ElementTree as ET
    historic=runtime.SOURCE/'perf/results/20261112-r07-m03-m04/precompare-full-fastTest-xml'
    old_skips={(case.get('classname').rsplit('.',1)[-1],case.get('name')) for path in historic.glob('TEST-*.xml') for case in ET.parse(path).getroot().findall('testcase') if case.find('skipped') is not None}
    current_skips={(row['className'].rsplit('.',1)[-1],row['name']) for row in rows if row['state']=='skipped'}
    accepted_skips=bool(strict and strict.get('status')=='INCOMPLETE' and strict['totals']['failures']==strict['totals']['errors']==0 and strict['totals']['skipped']==15 and old_skips==current_skips and summary and summary['status']=='PASS_WITH_SKIPS' and all(problem=='skipped tests forbidden by verification gate' for task in strict['tasks'] for problem in task['problems']) and ':verifiedCheck FAILED' in (out/'console.log').read_text())
    result_json=dict(label=label,namespace=runtime.NAMESPACE,project=runtime.PROJECT,command=command,started=started,finished=time.time(),exitCode=result.returncode,sourceSha256=source,sourceChanged=sorted(k for k in source.keys()|current.keys() if source.get(k)!=current.get(k)),nativeMismatch=sorted(k for k in execution.keys()|current.keys() if execution.get(k)!=current.get(k)),tests=rows,summary=summary,strictGatePassed=bool(strict and strict['status']=='PASS'),acceptedWithKnownSkips=accepted_skips)
    ops.write_json(out/'command.json',result_json)
    print(json.dumps(dict(label=label,exitCode=result.returncode,tests=len(rows),failed=sum(x['state']=='failed' for x in rows),skipped=sum(x['state']=='skipped' for x in rows))))
    return result.returncode

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('label');parser.add_argument('tasks',nargs='+');parser.add_argument('--filter',action='append',default=[]);args=parser.parse_args();raise SystemExit(run(args.label,args.tasks,args.filter))
