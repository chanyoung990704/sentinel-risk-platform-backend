"""요청 상태와 메시지 격리를 독립 분모로 대조한다."""
from collections import Counter

def reconcile(accepted,rows,expected_failed=(),quarantined=0):
    accepted=list(accepted);expected=set(accepted);failed=set(expected_failed)
    if len(accepted)!=len(expected) or not failed<=expected or quarantined<0:raise ValueError('집계 분모가 잘못되었습니다')
    counts=Counter(row['id'] for row in rows);by_id={row['id']:row for row in rows}
    errors=[];success=0;failure=0;pending=0
    for request in sorted(expected):
        if counts[request]!=1:errors.append(dict(id=request,code='MISSING_OR_DUPLICATE'));continue
        row=by_id[request];status=row['status'];want='FAILED' if request in failed else 'SUCCEEDED'
        if status=='SUCCEEDED':success+=1
        elif status=='FAILED':failure+=1
        else:pending+=1
        if status!=want:errors.append(dict(id=request,code='UNEXPECTED_STATE',state=status))
        if row['jobs']!=1 or row['slot']!=0:errors.append(dict(id=request,code='JOB_OR_SLOT'))
        if row['artifacts']!=(1 if status=='SUCCEEDED' else 0) or row['files']!=(1 if status=='SUCCEEDED' else 0):errors.append(dict(id=request,code='ARTIFACT_OR_FILE'))
    extras=sorted(set(counts)-expected)
    if extras:errors.append(dict(code='UNEXPECTED_REQUESTS',ids=extras))
    return dict(passed=not errors,accepted=len(expected),succeeded=success,failed=failure,nonterminal=pending,expectedFailed=len(failed),quarantinedMessages=quarantined,errors=errors)
