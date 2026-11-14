"""실제 로컬 맞춤 보고서 HTTP/DB 원장 측정. 202와 최종 성공은 따로 계산한다."""
import argparse
import csv
from datetime import datetime
import hashlib
import http.client as http_client
import json
import math
import os
import platform
import shutil
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import ExitStack
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'reliability'))
import local_runtime as runtime
from capture_baseline import fingerprint
from portfolio_run import mysql, dataset, prometheus_value, proc_snapshot, validate_run_id
from report_summary import response_window_summary

ROOT=Path(__file__).resolve().parents[2]
FIELDS=['label','phase','sequence','requestId','keyHash','start_ms','elapsed_ms','status','sql_count','sql_nanos','success','schedule_lag_ms','error']

def reconcile(accepted, rows):
    expected=set(accepted); by_id={r['id']:r for r in rows if r['id'] in expected}
    succeeded=sum(r['status']=='SUCCEEDED' for r in by_id.values())
    failed=sum(r['status']=='FAILED' for r in by_id.values())
    return dict(accepted=len(expected),succeeded=succeeded,failed=failed,remaining=len(expected)-succeeded-failed,
        missing=len(expected-by_id.keys()),duplicateRows=len(rows)-len({r['id'] for r in rows}),
        invalidArtifacts=sum(int(r['artifacts']) != (1 if r['status']=='SUCCEEDED' else 0) for r in by_id.values()))

class MeasuredTransportError(RuntimeError):
    def __init__(self,start_ms,elapsed_ms,status,reason):
        super().__init__(reason);self.start_ms=start_ms;self.elapsed_ms=elapsed_ms;self.status=status;self.reason=reason

def http(method,path,token=None,body=None,key=None,port=18080):
    connection=http_client.HTTPConnection('127.0.0.1',port,timeout=20)
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    if key: headers['Idempotency-Key']=key
    started=time.perf_counter(); wall=time.time()*1000;status=0
    try:
        connection.request(method,path,json.dumps(body,ensure_ascii=False).encode() if body is not None else None,headers)
        response=connection.getresponse();status=response.status;raw=response.read();elapsed=(time.perf_counter()-started)*1000
        return response.status,json.loads(raw) if raw else {},dict(response.getheaders()),wall,elapsed
    except Exception as failure:raise MeasuredTransportError(wall,(time.perf_counter()-started)*1000,status,type(failure).__name__) from None
    finally: connection.close()

def percentile(values,p):
    if not values: return None
    ordered=sorted(values);return ordered[max(0,math.ceil(len(ordered)*p)-1)]

def summary(rows,duration,window=None):
    samples=[r for r in rows if r['phase']=='measure']; success=[r for r in samples if r['success']]
    values=[r['elapsed_ms'] for r in samples]
    result=dict(samples=len(samples),success=len(success),errors=len(samples)-len(success),successTps=len(success)/duration,
        p50=percentile(values,.5),p95=percentile(values,.95),p99=percentile(values,.99),
        meanSql=sum(int(r['sql_count']) for r in samples if r['sql_count']!='')/len(samples) if samples else None,
        missingSqlSamples=sum(r['sql_count']=='' for r in samples))
    if window:
        result['startedWindowSuccessTps']=result['successTps'];result.update(response_window_summary(rows,window))
    return result

def fixed_arrivals(submit,rate,duration,guard,started=None,clock=None):
    clock=clock or time.perf_counter;began=clock() if started is None else started
    for index in range(int(duration*rate)):
        if guard.is_set():break
        planned=began+index/rate;guard.wait(max(0,planned-clock()))
        if guard.is_set():break
        submit(index,planned)
    if not guard.is_set():guard.wait(max(0,began+duration-clock()))
    return clock()-began

def ledger(ids):
    if not ids: return []
    # ID는 서버가 반환한 UUID만 허용하고 SELECT 전용 계정으로 대조한다.
    names=','.join("'"+str(uuid.UUID(i))+"'" for i in ids)
    sql="SELECT r.id,r.status,r.state_version,r.created_at,r.updated_at,j.id,j.created_at,MIN(a.started_at),MAX(a.completed_at),COUNT(DISTINCT f.id),MAX(j.generation),MAX(j.attempt_no) FROM report_requests r LEFT JOIN report_jobs j ON j.request_id=r.id LEFT JOIN report_attempts a ON a.job_id=j.id LEFT JOIN report_artifacts f ON f.request_id=r.id WHERE r.id IN ("+names+") GROUP BY r.id,r.status,r.state_version,r.created_at,r.updated_at,j.id,j.created_at"
    keys=['id','status','stateVersion','acceptedDbAt','lastStateDbAt','jobId','registeredDbAt','firstStartedDbAt','lastAttemptEndedDbAt','artifacts','generation','attemptNo']
    return [dict(zip(keys,line.split('\t'))) for line in mysql(sql).splitlines()]

def validate_options(args):
    for name,low,high in [('rate',.1,20),('concurrency',1,100),('warmup',0,3600),('duration',1,3600),('latency',0,10000)]:
        value=getattr(args,name)
        if not math.isfinite(value) or not low<=value<=high:raise ValueError('측정 입력 범위 확인 필요: '+name)

def run(args):
    validate_run_id(args.run_id)
    archive=ROOT/'perf/results/20261112-r07-m03-m04'/args.run_id
    if archive.exists():raise FileExistsError('원자료 덮어쓰기 거부')
    native=runtime.STATE/'measurements/r07-m04'/args.run_id
    args.output_dir=native
    try:return _run(args)
    finally:
        if native.exists():
            archive.parent.mkdir(parents=True,exist_ok=True)
            shutil.copytree(native,archive)

def _run(args):
    # 준비 실패도 파일 보관 전에 소유 스레드와 CSV를 종료한다.
    with ExitStack() as cleanup:
        return _execute(args,cleanup)


def _stop_thread(event,thread,timeout):
    event.set()
    if thread.ident is not None:
        thread.join(timeout)
        if thread.is_alive():raise RuntimeError('측정 소유 스레드 종료 한도 초과')


def _execute(args,cleanup):
    validate_options(args)
    out=Path(args.output_dir);out.mkdir(parents=True,exist_ok=False)
    fixture=json.loads((runtime.STATE/'r07-m04/browser-fixture.json').read_text()); company=fixture['company']; tokens=[]
    for user in fixture['users']:
        code,payload,*_=http('POST','/api/auth/login',body={'email':user['email'],'password':user['password']})
        if code!=200: raise RuntimeError('로그인 준비 실패: '+str(code))
        tokens.append(payload['data']['accessToken'])
    hashes=fingerprint(ROOT);ready=json.loads((runtime.STATE/'r07-m04/browser-ready.json').read_text())
    built=json.loads((runtime.STATE/'r07-m04/app-source.json').read_text())
    if built['mainSha256']!={k:v for k,v in hashes.items() if k.startswith('src/main/')}: raise RuntimeError('계측 main 소스와 실행 앱이 다릅니다. 재기동 필요')
    if ready['cache']!=args.cache: raise RuntimeError('cache mode 불일치')
    manifest=dict(runId=args.run_id,startedUtc=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),os=platform.platform(),python=sys.version,
        cpu=Path('/proc/cpuinfo').read_text().split('model name')[1].splitlines()[0] if 'model name' in Path('/proc/cpuinfo').read_text() else None,
        memory=Path('/proc/meminfo').read_text().splitlines()[0],sourceSha256=hashes,sourceFingerprint=hashlib.sha256(json.dumps(hashes,sort_keys=True).encode()).hexdigest(),
        dataset=dataset(),mode=args.mode,cache=args.cache,users=20,workers=4,poolMax=10,heapMiB=512,ttlSeconds=2,globalCap=10000,userCap=10000,
        rate=args.rate,concurrency=args.concurrency,mockLatencyMs=args.latency,warmup=args.warmup,duration=args.duration,drainLimitSeconds=180,rawStorage='Ubuntu native filesystem; copied to worktree after run',
        clock='client monotonic elapsed + wall epoch; DB UTC TIMESTAMP is transaction-internal, NOT an exact commit timestamp',
        generator='python-stdlib-fixed-arrival-threadpool' if args.mode=='report' else 'python-stdlib-closed-loop',
        metricAvailability=dict(jobBacklog='not sampled each second; final ledger and request lifecycle retained',outboxAge='not inferred from immutable CDC rows; accept-to-job latency retained',kafkaLag='sum of exporter client lag-max gauges if present, not exact group lag; group offsets are verified in fault tests'),
        guard='observed backlog>=200 OR poolPending positive 5 consecutive samples OR MemAvailable<512MiB OR HTTP error OR 40 client requests in flight; retain accepted and drain',
        imageLocks={str(p.relative_to(ROOT)):json.loads(p.read_text()) for p in [ROOT/'infra/portfolio-images.lock.json',ROOT/'infra/cdc/images.lock.json']})
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    stop=threading.Event(); guard=threading.Event(); rows=[]; accepted={}; observations={}; lock=threading.Lock(); runtime_rows=[]
    phase={'name':'warmup'}; stop_poll=threading.Event();windows={}
    def open_window(name,seconds):
        mono=time.perf_counter();wall=time.time()*1000
        windows[name]=dict(startMs=wall,endMs=wall+seconds*1000,startMonotonic=mono,endMonotonic=mono+seconds)
        (out/'windows.json').write_text(json.dumps(windows,indent=2));return mono
    csvfile=(out/'requests.csv').open('w',newline=''); writer=csv.DictWriter(csvfile,fieldnames=FIELDS);writer.writeheader()
    cleanup.callback(csvfile.close)
    def save(row):
        with lock: rows.append(row);writer.writerow(row);csvfile.flush()
    def sample():
        consecutive=0
        while not stop.is_set():
            record={'time_ms':time.time()*1000,'phase':phase['name']}
            try:
                c=http_client.HTTPConnection('127.0.0.1',18080,timeout=3); c.request('GET','/actuator/prometheus',headers={'Authorization':'Bearer '+tokens[0]});resp=c.getresponse();text=resp.read().decode();c.close()
                if resp.status!=200: raise RuntimeError('prometheus '+str(resp.status))
                for key,name,tag in [('poolActive','hikaricp_connections_active',None),('poolPending','hikaricp_connections_pending',None),('poolTimeout','hikaricp_connections_timeout_total',None),('poolAcquireSeconds','hikaricp_connections_acquire_seconds_sum',None),('poolAcquireCount','hikaricp_connections_acquire_seconds_count',None),('httpSqlStatements','portfolio_jdbc_statements_total',None),('consumerLagMaxSum','kafka_consumer_fetch_manager_records_lag_max',None),('heapBytes','jvm_memory_used_bytes','area="heap"'),('threads','jvm_threads_live_threads',None),('gcSeconds','jvm_gc_pause_seconds_sum',None),('cacheHit','report_status_cache_total','outcome="hit"'),('cacheMiss','report_status_cache_total','outcome="miss"')]: record[key]=prometheus_value(text,name,tag)
                record['jobBacklog']=None;record['outboxAgeSeconds']=None
                record.update(proc_snapshot(ready['pid']));record['clientCpuSeconds']=time.process_time()
                mem=int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))*1024;record['memAvailableBytes']=mem
                consecutive=consecutive+1 if (record['poolPending'] or 0)>0 else 0
                with lock: backlog=len(accepted)-len(observations)
                record['observedBacklog']=backlog
                if consecutive>=5 or mem<512*1024**2 or backlog>=200: guard.set()
            except Exception as exc: record['sampleError']=type(exc).__name__
            runtime_rows.append(record)
            stop.wait(1)
    sampler=threading.Thread(target=sample);cleanup.callback(_stop_thread,stop,sampler,5);sampler.start()
    def post(sequence,planned,which):
        began=time.perf_counter();wall_start=time.time()*1000
        key=str(uuid.uuid4());key_hash=hashlib.sha256(key.encode()).hexdigest()
        try:
            status,payload,headers,start,elapsed=http('POST',f'/api/companies/{company}/custom-report-requests',tokens[sequence%20],{'additionalInstructions':'현금흐름 검토와 단기 차입 구조를 분석해 주세요.'},key)
            id=payload.get('data',{}).get('requestId','') if status==202 and payload.get('success') is True else ''
            row=dict(label='report_accept',phase=which,sequence=sequence,requestId=id,start_ms=start,elapsed_ms=elapsed,status=status,sql_count=headers.get('X-Portfolio-Sql-Count',''),sql_nanos=headers.get('X-Portfolio-Sql-Nanos',''),success=status==202 and bool(id),schedule_lag_ms=max(0,(time.perf_counter()-elapsed/1000-planned)*1000),error='')
            if id:
                uuid.UUID(id)
                with lock: accepted[id]=(sequence%20,start,which)
        except Exception as exc: row=dict(label='report_accept',phase=which,sequence=sequence,requestId='',start_ms=getattr(exc,'start_ms',wall_start),elapsed_ms=getattr(exc,'elapsed_ms',(time.perf_counter()-began)*1000),status=getattr(exc,'status',0),sql_count='',sql_nanos='',success=False,schedule_lag_ms='',error=getattr(exc,'reason',type(exc).__name__))
        row['keyHash']=key_hash;save(row)
        if not row['success']:guard.set()
    def poll():
        while not stop_poll.is_set():
            with lock: pending=[(id,value) for id,value in accepted.items() if id not in observations]
            for id,(user,start,which) in pending:
                if stop_poll.is_set():break
                began=time.perf_counter();wall_start=time.time()*1000
                try:
                    code,payload,headers,wall,elapsed=http('GET',f'/api/report-requests/{id}',tokens[user]);state=payload.get('data',{}).get('status')
                    save(dict(label='report_status_poll',phase=phase['name'],sequence='',requestId=id,start_ms=wall,elapsed_ms=elapsed,status=code,sql_count=headers.get('X-Portfolio-Sql-Count',''),sql_nanos=headers.get('X-Portfolio-Sql-Nanos',''),success=code==200,schedule_lag_ms='',error=''))
                    if state in ('SUCCEEDED','FAILED'):
                        with lock: observations[id]={'id':id,'status':state,'observedAtMs':wall+elapsed,'clientElapsedMs':wall+elapsed-start,'phase':which}
                except Exception as exc:
                    save(dict(label='report_status_poll',phase=phase['name'],sequence='',requestId=id,start_ms=getattr(exc,'start_ms',wall_start),elapsed_ms=getattr(exc,'elapsed_ms',(time.perf_counter()-began)*1000),status=getattr(exc,'status',0),sql_count='',sql_nanos='',success=False,schedule_lag_ms='',error=getattr(exc,'reason',type(exc).__name__)))
            stop_poll.wait(1)
    poller=None
    if args.mode=='report':
        http('POST','/control/custom-reports',body={'delay_ms':args.latency,'seed':'m03-fixed'},port=18081)
        poller=threading.Thread(target=poll);cleanup.callback(_stop_thread,stop_poll,poller,25);poller.start()
        sequence=0; inflight_limit=threading.BoundedSemaphore(40)
        with ThreadPoolExecutor(max_workers=40) as pool:
            for which,seconds in [('warmup',args.warmup),('measure',args.duration)]:
                phase['name']=which;began=open_window(which,seconds)
                def submit(index,planned):
                    nonlocal sequence
                    if not inflight_limit.acquire(blocking=False):guard.set();return
                    future=pool.submit(post,sequence,planned,which);future.add_done_callback(lambda completed:inflight_limit.release());sequence+=1
                fixed_arrivals(submit,args.rate,seconds,guard,started=began)
                if guard.is_set(): break
            phase['name']='drain';drain_start=time.perf_counter();windows['loadStopped']=dict(atMs=time.time()*1000,atMonotonic=drain_start,guarded=guard.is_set())
            (out/'windows.json').write_text(json.dumps(windows,indent=2))
        while time.perf_counter()-drain_start<180:
            with lock: incomplete=len(accepted)-len(observations)
            if incomplete==0: break
            time.sleep(1)
        drain=time.perf_counter()-drain_start
    else:
        candidates=mysql('SELECT r.id,r.user_id,r.state_version,j.generation,j.attempt_no FROM report_requests r JOIN report_jobs j ON j.request_id=r.id WHERE r.user_id IN ('+','.join(str(u['id']) for u in fixture['users'])+") AND r.status='SUCCEEDED' ORDER BY r.id LIMIT 100").splitlines()
        if not candidates: raise RuntimeError('소유 완료 fixture 없음')
        user_indexes={str(u['id']):i for i,u in enumerate(fixture['users'])}
        expected={line.split('\t')[0]:[int(x) for x in line.split('\t')[2:]] for line in candidates}
        paths=[(line.split('\t')[0],user_indexes[line.split('\t')[1]]) for line in candidates];references={}
        for id,user in paths:
            code,payload,*_=http('GET',f'/api/report-requests/{id}',tokens[user]);value=payload.get('data')
            if code!=200 or payload.get('success') is not True or not isinstance(value,dict) or value.get('requestId')!=id or value.get('status')!='SUCCEEDED' or [value.get('stateVersion'),value.get('generation'),value.get('attemptNo')]!=expected[id] or value.get('resultUrl')!=f'/api/report-requests/{id}/file' or value.get('errorCode') is not None or value.get('retryAt') is not None:
                raise RuntimeError('상태 reference와 DB 원장 불일치')
            references[id]=value
        (out/'references.json').write_text(json.dumps(dict(responseHash=hashlib.sha256(json.dumps(references,sort_keys=True).encode()).hexdigest(),requestIds=sorted(references),responses=references,databaseVerified=True),indent=2))
        if args.cache=='on':
            from cache_runtime import evict_keys
            evict_keys([f'custom-report:status:v1:{fixture["users"][user]["id"]}:{id}' for id,user in paths])
        began=open_window('warmup',args.warmup);base_wall=windows['warmup']['startMs'];measure_start=began+args.warmup;end=measure_start+args.duration
        windows['measure']=dict(startMs=base_wall+args.warmup*1000,endMs=base_wall+(args.warmup+args.duration)*1000,startMonotonic=measure_start,endMonotonic=end)
        (out/'windows.json').write_text(json.dumps(windows,indent=2))
        def reader(worker):
            seq=worker
            while time.perf_counter()<end and not guard.is_set():
                which='measure' if time.perf_counter()>=measure_start else 'warmup';phase['name']=which
                id,user=paths[seq%len(paths)];seq+=args.concurrency
                began=time.perf_counter();wall_start=time.time()*1000
                try:
                    code,payload,headers,start,elapsed=http('GET',f'/api/report-requests/{id}',tokens[user]);ok=code==200 and payload.get('success') is True and payload.get('data')==references[id]
                    save(dict(label='status_read',phase=which,sequence=seq,requestId=id,start_ms=start,elapsed_ms=elapsed,status=code,sql_count=headers.get('X-Portfolio-Sql-Count',''),sql_nanos=headers.get('X-Portfolio-Sql-Nanos',''),success=ok,schedule_lag_ms='',error='' if ok else 'status-or-response-mismatch'))
                    if not ok:guard.set()
                except Exception as exc:
                    save(dict(label='status_read',phase=which,sequence=seq,requestId=id,start_ms=getattr(exc,'start_ms',wall_start),elapsed_ms=getattr(exc,'elapsed_ms',(time.perf_counter()-began)*1000),status=getattr(exc,'status',0),sql_count='',sql_nanos='',success=False,schedule_lag_ms='',error=getattr(exc,'reason',type(exc).__name__)))
                    guard.set()
        with ThreadPoolExecutor(max_workers=args.concurrency) as pool: list(pool.map(reader,range(args.concurrency)))
        drain=0
    stop_poll.set()
    if poller: poller.join(25)
    stop.set();sampler.join(5);csvfile.close()
    (out/'runtime.json').write_text(json.dumps(runtime_rows,indent=2));records=ledger(list(accepted));(out/'lifecycle.json').write_text(json.dumps(records,indent=2))
    for filename,items in [('runtime.csv',runtime_rows),('lifecycle.csv',records)]:
        if items:
            with (out/filename).open('w',newline='') as file:
                csv_writer=csv.DictWriter(file,fieldnames=sorted(set().union(*(r.keys() for r in items))));csv_writer.writeheader();csv_writer.writerows(items)
    (out/'observations.json').write_text(json.dumps(list(observations.values()),indent=2));result=reconcile(list(accepted),records)
    result.update(http={label:summary([r for r in rows if r['label']==label],args.duration,windows.get('measure')) for label in set(r['label'] for r in rows)},guardStopped=guard.is_set(),drainSeconds=drain)
    if guard.is_set():
        for measured in result['http'].values():measured.update(successTps=None,windowIncomplete=True)
    main_ids={id for id,value in accepted.items() if value[2]=='measure'};main_records=[r for r in records if r['id'] in main_ids];result['main']=reconcile(main_ids,main_records)
    valid_main=args.mode=='report' and not guard.is_set()
    result['main']['acceptedTps']=len(main_ids)/args.duration if valid_main else None;result['main']['completedByObservedDrainTps']=result['main']['succeeded']/(args.duration+drain) if valid_main else None
    if 'measure' in windows and 'loadStopped' in windows:result['main']['actualSubmissionWindowSeconds']=max(0,windows['loadStopped']['atMonotonic']-windows['measure']['startMonotonic'])
    result['clientCompletionMs']={p:percentile([x['clientElapsedMs'] for x in observations.values() if x['phase']=='measure' and x['status']=='SUCCEEDED'],v) for p,v in [('p50',.5),('p95',.95),('p99',.99)]}
    lifecycle_metrics={}
    for label,start,end in [('deliveryAndRegister','acceptedDbAt','registeredDbAt'),('queueWait','registeredDbAt','firstStartedDbAt'),('dbRecordedCompletion','acceptedDbAt','lastStateDbAt')]:
        values=[(datetime.fromisoformat(row[end])-datetime.fromisoformat(row[start])).total_seconds()*1000 for row in main_records if row['status']=='SUCCEEDED' and row[start]!='NULL' and row[end]!='NULL']
        lifecycle_metrics[label]={'samples':len(values),'p50':percentile(values,.5),'p95':percentile(values,.95),'p99':percentile(values,.99)}
    result['dbInternalTimestampMetricsMs']=lifecycle_metrics
    if accepted:
        _,journal,*_=http('GET','/journal',port=18081)
        matching=[row for row in journal if row.get('requestId') in accepted]
        (out/'mock-journal.json').write_text(json.dumps(matching,indent=2))
        result['externalHttpCalls']=len(matching)
        result['externalExtraCalls']=len(matching)-len({row['requestId'] for row in matching})
    (out/'reconciliation.json').write_text(json.dumps(result,indent=2));print(json.dumps({'run':args.run_id,'main':result['main'],'guard':guard.is_set(),'drainSeconds':drain}),flush=True)
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--run-id',required=True);parser.add_argument('--mode',choices=['report','status'],default='report');parser.add_argument('--cache',choices=['off','on'],default='off');parser.add_argument('--rate',type=float,default=1);parser.add_argument('--latency',type=int,default=200);parser.add_argument('--concurrency',type=int,default=10);parser.add_argument('--warmup',type=int,default=30);parser.add_argument('--duration',type=int,default=120)
    run(parser.parse_args())
