"""보고서 접수 응답과 서로 다른 완료 시계의 본측정 구간을 분리한다."""
from datetime import datetime, timezone

def response_window_summary(rows, window):
    start=float(window['startMs']);end=float(window['endMs']);duration=(end-start)/1000
    if duration<=0:raise ValueError('측정 구간 확인 필요')
    completed=[r for r in rows if start<=float(r['start_ms'])+float(r['elapsed_ms'])<end]
    good=lambda r:r['success'] is True or str(r['success']).lower()=='true'
    return dict(successTps=sum(good(r) for r in completed)/duration,completedResponses=len(completed),
        completedErrors=sum(not good(r) for r in completed),
        carryInResponses=sum(float(r['start_ms'])<start for r in completed),
        carryOutResponses=sum(r['phase']=='measure' and float(r['start_ms'])+float(r['elapsed_ms'])>=end for r in rows),
        startedMainSamples=sum(r['phase']=='measure' for r in rows))

def window_summary(rows, observations, lifecycle, duration, guarded, window=None):
    posts=[row for row in rows if row['label']=='report_accept' and row['phase']=='measure']
    missing=sum(row.get('schedule_lag_ms','') in ('',None) for row in posts)
    timed=[row for row in posts if row.get('schedule_lag_ms','') not in ('',None)]
    valid=not guarded and bool(timed) and not missing
    if not timed:
        return dict(validFullWindow=False,acceptedResponseTps=None,dbRecordedSucceededTps=None,clientObservedSucceededTps=None,lateAcceptedResponses=0,missingScheduleClockSamples=missing)
    start=float(window['startMs']) if window else min(float(row['start_ms'])-float(row['schedule_lag_ms']) for row in timed)
    end=float(window['endMs']) if window else start+duration*1000
    accepted=[row for row in posts if int(row['status'])==202]
    count_accepted=sum(start<=float(row['start_ms'])+float(row['elapsed_ms'])<end for row in accepted)
    count_client=sum(row['status']=='SUCCEEDED' and start<=float(row['observedAtMs'])<end for row in observations)
    count_db=0
    for row in lifecycle:
        if row['status']!='SUCCEEDED' or row.get('lastStateDbAt') in ('NULL',None):continue
        recorded=datetime.fromisoformat(row['lastStateDbAt']).replace(tzinfo=timezone.utc).timestamp()*1000
        count_db+=int(start<=recorded<end)
    return dict(validFullWindow=valid,windowStartMs=start,windowEndMs=end,missingScheduleClockSamples=missing,
        acceptedResponseTps=count_accepted/duration if valid else None,
        dbRecordedSucceededTps=count_db/duration if valid else None,
        clientObservedSucceededTps=count_client/duration if valid else None,
        acceptedResponsesInWindow=count_accepted,dbRecordedSucceededInWindow=count_db,clientObservedSucceededInWindow=count_client,
        lateAcceptedResponses=sum(float(row['start_ms'])+float(row['elapsed_ms'])>=end for row in accepted),
        clockNote='DB timestamps are transaction-internal UTC records; client observation includes polling; guarded run TPS is N/A')
