"""증거가 없는 수치와 부정확한 분모/코드 연결을 거부한다."""
import hashlib,json,sys,tempfile,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import evidence

class EvidenceTest(unittest.TestCase):
    def fixture(self,root):
        raw={'accepted':2,'succeeded':2,'failed':0,'nonterminal':0,'quarantinedMessages':7,'sourceFingerprint':'a'*64}
        (root/'raw.json').write_text(json.dumps(raw));digest=hashlib.sha256((root/'raw.json').read_bytes()).hexdigest()
        return {'version':1,'files':{'raw.json':digest},'runs':[{'raw':'raw.json','sourceFingerprint':'a'*64,'expectedAccepted':2,'expectedFailed':0}],
            'claims':[{'label':'완료','kind':'value','file':'raw.json','path':['succeeded'],'value':2}],
            'coverage':{'F01':{'state':'passed','repeats':3},'F15':{'state':'N/A','reason':'CDC'}},'requiredCases':['F01','F15']}
    def test_valid_evidence_keeps_quarantine_outside_request_denominator(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root);self.assertTrue(evidence.verify(package,root)['passed'])
    def test_missing_raw_hash_mismatch_source_mismatch_and_mixed_denominator_fail(self):
        # given / when / then
        for mutation in ('missing','hash','source','denominator'):
            with self.subTest(mutation=mutation),tempfile.TemporaryDirectory() as directory:
                root=Path(directory);package=self.fixture(root)
                if mutation=='missing':(root/'raw.json').unlink()
                elif mutation=='hash':(root/'raw.json').write_text('{}')
                elif mutation=='source':package['runs'][0]['sourceFingerprint']='b'*64
                else:package['runs'][0]['expectedAccepted']=9
                self.assertFalse(evidence.verify(package,root)['passed'])
    def test_unmeasured_improvement_and_missing_coverage_fail(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root)
            package['claims'].append({'label':'근거없는개선','kind':'improvement','value':30})
            self.assertFalse(evidence.verify(package,root)['passed'])
            package=self.fixture(root);package['coverage']['F01']['repeats']=2
            self.assertFalse(evidence.verify(package,root)['passed'])
    def test_evidence_paths_cannot_escape_root(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root);package['files']['../outside']='a'*64
            self.assertFalse(evidence.verify(package,root)['passed'])

    def test_strict_coverage_requires_three_distinct_executed_seed_proofs(self):
        # given: 횟수만 3이라고 적어 놓은 coverage는 실제 시험 근거가 아니다.
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root);package['strictCoverage']=True
            self.assertFalse(evidence.verify(package,root)['passed'])

    def test_strict_complete_packet_accepts_only_matching_executed_xml_and_case_methods(self):
        # given: 16개 조건과 실제 XML/독립 반복을 모두 연결한다.
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root);package['strictCoverage']=True
            ids=[f'F{n:02}' for n in range(1,17)];package['requiredCases']=ids;package['matrixFile']='matrix.json'
            def save(name,value,raw=False):
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value if raw else json.dumps(value));package['files'][name]=hashlib.sha256(path.read_bytes()).hexdigest()
            save('matrix.json',{'cases':[{'id':case,'methods':[] if case=='F15' else ['exercise']} for case in ids]})
            for seed in (1,2,3):
                save(f'seed{seed}/scope/tests.xml','<testsuite><testcase name="exercise()" classname="Boundary" time="0.1"/></testsuite>',True)
                tests={'exercise':[{'name':'exercise()','className':'Boundary','state':'passed','xml':'scope/tests.xml'}]}
                save(f'seed{seed}/summary.json',{'seed':seed,'complete':True,'coverage':[{'id':case,'state':'N/A' if case=='F15' else 'passed','tests':{} if case=='F15' else tests} for case in ids]})
            package['coverage']={case:({'state':'N/A','reason':'CDC'} if case=='F15' else {'state':'passed','repeats':3,'proofs':[{'seed':seed,'file':f'seed{seed}/summary.json'} for seed in (1,2,3)]}) for case in ids}
            self.assertTrue(evidence.verify(package,root)['passed'])
            # when / then: 선언 키만 유지하고 다른 통과 XML을 연결해도 거부한다.
            for seed in (1,2,3):
                save(f'seed{seed}/scope/other.xml','<testsuite><testcase name="unrelated()" classname="Other" time="0.1"/></testsuite>',True)
                wrong={'exercise':[{'name':'unrelated()','className':'Other','state':'passed','xml':'scope/other.xml'}]}
                save(f'seed{seed}/summary.json',{'seed':seed,'complete':True,'coverage':[{'id':case,'state':'N/A' if case=='F15' else 'passed','tests':{} if case=='F15' else wrong} for case in ids]})
            self.assertFalse(evidence.verify(package,root)['passed'])
            # when / then: XML이 통과해도 선언 조건과 다른 메서드를 재사용하면 거부한다.
            save('matrix.json',{'cases':[{'id':case,'methods':[] if case=='F15' else ['otherMethod']} for case in ids]})
            self.assertFalse(evidence.verify(package,root)['passed'])

    def test_local_document_links_must_resolve_inside_repository(self):
        # given / when / then
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=self.fixture(root);doc=root/'result.md';doc.write_text('[원자료](missing.json)')
            package['files']['result.md']=hashlib.sha256(doc.read_bytes()).hexdigest();package['documents']=['result.md']
            self.assertFalse(evidence.verify(package,root)['passed'])
            doc.write_text('[원자료](raw.json)');package['files']['result.md']=hashlib.sha256(doc.read_bytes()).hexdigest()
            self.assertTrue(evidence.verify(package,root)['passed'])
