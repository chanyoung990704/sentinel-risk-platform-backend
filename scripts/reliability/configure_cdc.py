"""선택한 CDC 업무 connector만 구성한다. 비밀/offset/history를 출력·초기화하지 않는다."""
import argparse,json,subprocess,time,urllib.error
from pathlib import Path
import local_runtime as runtime
import cdc_runtime as cdc

NAME='sentinel-custom-reports-v1'
TOPICS=('sentinel.custom-report.request.v1','sentinel.custom-report.status.v1','sentinel.custom-report.dlt.v1')
HISTORY='sentinel-custom-reports-schema-history-v1'

def template():
    value=json.loads((runtime.WORKSPACE/'infra/cdc/portfolio-outbox.json').read_text())
    expected={'database.include.list':'sentinel_portfolio','table.include.list':'sentinel_portfolio.outbox_events','topic.prefix':'sentinel-custom-reports-cdc-v1','schema.history.internal.kafka.topic':HISTORY,'transforms.outbox.route.by.field':'destination','transforms.outbox.route.topic.replacement':'${routedByValue}','key.converter':'org.apache.kafka.connect.storage.StringConverter','value.converter.schemas.enable':'false','transforms.outbox.table.expand.json.payload':'true','database.password':'${file:/run/secrets/cdc-reader.properties:password}'}
    if any(value.get(k)!=v for k,v in expected.items()):raise ValueError('업무 CDC 계약 설정 불일치')
    return value

def configure():
    runtime.sync_workspace();runtime.setup()
    decision=json.loads((runtime.STATE/'relay-mode.json').read_text())
    if decision.get('relayMode')!='cdc':raise ValueError('E03 CDC 선택 기록이 필요합니다')
    cdc.prepare();cdc.wait_worker()
    # 기존 reader의 replication 권한을 유지하고 업무 Outbox 한 테이블의 읽기만 추가한다.
    sql="GRANT SELECT, SHOW VIEW ON sentinel_portfolio.outbox_events TO 'sentinel_cdc'@'%';"
    command=['docker','compose','-p',runtime.PROJECT,'-f',str(runtime.WORKSPACE/'docker-compose.portfolio.yml'),'exec','-T','mysql','sh','-c','MYSQL_PWD="$(cat /run/secrets/mysql-root)" mysql -u root']
    subprocess.run(command,input=sql,text=True,env=runtime.environment(),capture_output=True,check=True)
    for topic in TOPICS+(HISTORY,):
        options=['exec','-T','kafka','/opt/kafka/bin/kafka-topics.sh','--bootstrap-server','kafka:9092','--create','--if-not-exists','--topic',topic,'--partitions','1' if topic==HISTORY else '3','--replication-factor','1']
        if topic==HISTORY:options+=['--config','cleanup.policy=delete','--config','retention.ms=-1','--config','retention.bytes=-1']
        runtime.compose(options,True)
    desired=template()
    try:existing=cdc.request('/connectors/'+NAME+'/config')
    except urllib.error.HTTPError as error:
        if error.code!=404:raise
        existing=None
    if existing is not None:
        for field in ('connector.class','database.include.list','table.include.list','topic.prefix','schema.history.internal.kafka.topic'):
            if existing.get(field)!=desired.get(field):raise ValueError('기존 connector 범위가 다릅니다. 위치 보존 검토가 필요합니다')
    if existing!=desired:cdc.request('/connectors/'+NAME+'/config','PUT',desired)
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        try:state=cdc.request('/connectors/'+NAME+'/status')
        except urllib.error.HTTPError as error:
            if error.code not in (404,409,503):raise
            time.sleep(.25);continue
        tasks=state.get('tasks',[])
        if state['connector']['state']=='FAILED' or any(t['state']=='FAILED' for t in tasks):raise RuntimeError('업무 CDC task 실패; 전용 worker 상태를 확인하세요')
        if state['connector']['state']=='RUNNING' and len(tasks)==1 and tasks[0]['state']=='RUNNING':return {'connector':NAME,'state':'RUNNING','topics':TOPICS}
        time.sleep(.25)
    raise RuntimeError('업무 CDC 준비 시간 초과')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['configure','status','recover']);args=parser.parse_args()
    result=configure() if args.action=='configure' else recover() if args.action=='recover' else cdc.request('/connectors/'+NAME+'/status')
    if args.action=='status':result={'connector':NAME,'state':result['connector']['state'],'tasks':[task['state'] for task in result.get('tasks',[])]}
    print(json.dumps(result));return 0

def recover():
    runtime.sync_workspace();runtime.setup()
    if json.loads((runtime.STATE/'relay-mode.json').read_text()).get('relayMode')!='cdc':raise ValueError('E03 CDC 선택 기록이 필요합니다')
    cdc.wait_worker();existing=cdc.request('/connectors/'+NAME+'/config');desired=template()
    for field in ('connector.class','database.include.list','table.include.list','topic.prefix','schema.history.internal.kafka.topic'):
        if existing.get(field)!=desired.get(field):raise ValueError('기존 connector 범위가 달라 복구를 중단합니다')
    before=cdc.request('/connectors/'+NAME+'/offsets');started=time.monotonic()
    # 비동기 restart 직후의 오래된 RUNNING을 성공으로 보지 않는다. STOPPED 경계를 먼저 확인한다.
    cdc.request('/connectors/'+NAME+'/stop','PUT')
    wait_transition('STOPPED',lambda state:state['connector']['state']=='STOPPED' and not state.get('tasks'))
    stopped=cdc.request('/connectors/'+NAME+'/offsets')
    cdc.request('/connectors/'+NAME+'/resume','PUT')
    wait_transition('RUNNING',lambda state:state['connector']['state']=='RUNNING' and len(state.get('tasks',[]))==1 and state['tasks'][0]['state']=='RUNNING')
    return {'connector':NAME,'state':'RUNNING','stoppedConfirmed':True,'deliveryVerified':False,'recoveryMs':round((time.monotonic()-started)*1000),'checkpointBefore':before,'checkpointStopped':stopped,'checkpointAfter':cdc.request('/connectors/'+NAME+'/offsets')}

def wait_transition(phase,predicate):
    deadline=time.monotonic()+90
    while time.monotonic()<deadline:
        try:
            state=cdc.request('/connectors/'+NAME+'/status')
            if predicate(state):return
            if phase=='RUNNING' and (state['connector']['state']=='FAILED' or any(task['state']=='FAILED' for task in state.get('tasks',[]))):raise RuntimeError('업무 CDC 재개 후 task 실패')
        except (OSError,ValueError):pass
        time.sleep(.25)
    raise RuntimeError('업무 CDC '+phase+' 전환 확인 시간 초과')

if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as failure:print('업무 CDC 구성 실패: '+type(failure).__name__);raise SystemExit(1)
