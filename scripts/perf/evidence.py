"""문서 수치를 원자료 경로/지문/계산/업무 분모와 대조한다. 서명 또는 운영 보증은 아니다."""
import argparse,hashlib,json,math,re,xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import unquote

def sha(path):
    value=hashlib.sha256()
    with path.open('rb') as file:
        for chunk in iter(lambda:file.read(1024*1024),b''):value.update(chunk)
    return value.hexdigest()

def verify(package,root):
    root=Path(root).resolve();errors=[];loaded={};registered=package.get('files',{})
    def read(name):
        if name not in registered:raise ValueError('등록되지 않은 근거')
        if name not in loaded:loaded[name]=json.loads((root/name).read_text())
        return loaded[name]
    def at(value,path):
        for part in path:value=value[part]
        return value
    if package.get('version')!=1:errors.append('증거 형식 버전 불일치')
    for name,expected in registered.items():
        relative=Path(name);path=root/relative
        try:
            if relative.is_absolute() or '..' in relative.parts or not path.resolve().is_relative_to(root) or path.is_symlink() or not path.is_file() or sha(path)!=expected:raise ValueError()
        except (OSError,ValueError):errors.append('파일 누락/지문/경로: '+name)
    if errors:return dict(passed=False,errors=errors)
    for run in package.get('runs',[]):
        try:
            raw=read(run['raw']);accepted=run['expectedAccepted'];failed=run['expectedFailed']
            if raw['sourceFingerprint']!=run['sourceFingerprint']:raise ValueError('측정 코드 불일치')
            if 'sourceMap' in run:
                digest=hashlib.sha256(json.dumps(read(run['sourceMap']),sort_keys=True).encode()).hexdigest()
                if digest!=run['sourceFingerprint']:raise ValueError('측정 코드 map 지문 불일치')
            if raw['accepted']!=accepted or raw['succeeded']+raw['failed']+raw['nonterminal']!=accepted or raw['failed']!=failed or raw['nonterminal']!=0 or raw['succeeded']!=accepted-failed:raise ValueError('업무 상태 분모/예상 실패/종결 불일치')
            # quarantinedMessages는 이 합산식에 넣지 않는다.
        except (KeyError,TypeError,ValueError,OSError):errors.append('실행 코드/분모 불일치: '+str(run.get('raw')))
    for claim in package.get('claims',[]):
        try:
            kind=claim['kind']
            if kind=='value':actual=at(read(claim['file']),claim['path'])
            elif kind=='percentReduction':
                before=at(read(claim['before']['file']),claim['before']['path']);after=at(read(claim['after']['file']),claim['after']['path'])
                if not claim.get('comparisonConditions') or not isinstance(before,(int,float)) or before<=0 or not isinstance(after,(int,float)):raise ValueError('비교 조건/측정 없음')
                actual=100*(before-after)/before
            else:raise ValueError('미지원/미측정 개선 주장')
            expected=claim['value']
            if isinstance(actual,(int,float)) and not isinstance(actual,bool):
                if not isinstance(expected,(int,float)) or not math.isfinite(actual) or not math.isclose(actual,expected,rel_tol=1e-9,abs_tol=claim.get('tolerance',0)):raise ValueError('수치 불일치')
            elif actual!=expected:raise ValueError('값 불일치')
        except (KeyError,IndexError,TypeError,ValueError,OSError):errors.append('미입증 주장: '+str(claim.get('label')))
    for case in package.get('requiredCases',[]):
        evidence=package.get('coverage',{}).get(case,{})
        if case=='F15' and evidence.get('state')=='N/A' and evidence.get('reason'):continue
        if evidence.get('state')!='passed' or evidence.get('repeats')!=3:errors.append('필수 coverage 미충족: '+case)
    if package.get('strictCoverage'):
        if set(package.get('requiredCases',[]))!={f'F{n:02}' for n in range(1,17)}:errors.append('전체 F01~F16 coverage 필요')
        try:matrix={case['id']:case for case in read(package['matrixFile'])['cases']}
        except (KeyError,TypeError,ValueError,OSError):matrix={};errors.append('고정 장애 조건 근거 누락')
        xml_cache={}
        for case,item in package.get('coverage',{}).items():
            if case=='F15':continue
            try:
                proofs=item['proofs']
                if len(proofs)!=3 or {proof['seed'] for proof in proofs}!={1,2,3}:raise ValueError('3개 독립 반복 필요')
                for proof in proofs:
                    summary=read(proof['file'])
                    if summary['seed']!=proof['seed'] or summary['complete'] is not True:raise ValueError('미완료 반복')
                    entry=next(row for row in summary['coverage'] if row['id']==case)
                    if entry['state']!='passed' or not entry['tests']:raise ValueError('실행 시험 누락')
                    if set(entry['tests'])!=set(matrix[case]['methods']):raise ValueError('선언한 경계와 시험 메서드 불일치')
                    for method,rows in entry['tests'].items():
                        if not rows:raise ValueError('시험 발견 실패')
                        for row in rows:
                            if not (row['name']==method or row['name'].startswith(method+'(') or row['name'].startswith(method+'[')):raise ValueError('선언 메서드와 실행 이름 불일치')
                            name=(Path(proof['file']).parent/row['xml']).as_posix()
                            if name not in registered or row['state']!='passed':raise ValueError('XML 근거 누락')
                            if name not in xml_cache:xml_cache[name]=ET.parse(root/name).getroot()
                            actual=[test for test in xml_cache[name].findall('testcase') if test.get('name')==row['name'] and test.get('classname')==row['className']]
                            if len(actual)!=1 or any(actual[0].find(tag) is not None for tag in ('failure','error','skipped')):raise ValueError('XML 미통과')
            except (KeyError,TypeError,ValueError,StopIteration,ET.ParseError,OSError):errors.append('반복/XML 근거 미충족: '+case)
    for name,expected in package.get('currentSourceFiles',{}).items():
        path=root/name
        if not path.resolve().is_relative_to(root) or path.is_symlink() or not path.is_file() or sha(path)!=expected:errors.append('현재 코드 변경: '+name)
    for name in package.get('documents',[]):
        try:
            if name not in registered:raise ValueError('등록되지 않은 문서')
            document=root/name
            for link in re.findall(r'!?\[[^\]]*\]\(([^)]+)\)',document.read_text()):
                link=link.strip().strip('<>')
                if link.startswith(('https://','http://','mailto:','#')):continue
                target=unquote(link.split('#',1)[0].split('?',1)[0])
                if not target:continue
                path=(document.parent/target).resolve()
                if not path.is_relative_to(root) or not path.exists():raise ValueError('깨진 로컬 파일 링크')
        except (OSError,ValueError):errors.append('문서 링크 누락/경로: '+name)
    return dict(passed=not errors,errors=errors,verifiedFiles=len(registered),claims=len(package.get('claims',[])),coverage=len(package.get('requiredCases',[])),limit='등록된 지문/계산/완료 분모 대조이며 암호 서명·실제 AI 정확도·운영 무손실 보증은 아니다')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--package',type=Path,required=True);parser.add_argument('--root',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    result=verify(json.loads(args.package.read_text()),args.root);args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False));raise SystemExit(0 if result['passed'] else 2)
