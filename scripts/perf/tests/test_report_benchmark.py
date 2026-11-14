import sys
import unittest
from unittest.mock import patch
from pathlib import Path
import argparse
import tempfile
import subprocess
import textwrap
import json,time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from report_benchmark import reconcile
import report_benchmark

class ReportSummaryTest(unittest.TestCase):
    def test_read_http_failure_stops_load_and_retains_failed_sample(self):
        # given: 실제 loop/CSV/스레드 수명은 사용하고 외부 HTTP/DB만 고정한다.
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);state=base/'state';root=base/'repo';(state/'r07-m04').mkdir(parents=True)
            users=[dict(id=i+1,email='synthetic',password='synthetic') for i in range(20)]
            for name,value in [('browser-fixture.json',dict(users=users,company=1)),('browser-ready.json',dict(pid=1,cache='off')),('app-source.json',dict(mainSha256={}))]:
                (state/'r07-m04'/name).write_text(json.dumps(value))
            for name in ('infra/portfolio-images.lock.json','infra/cdc/images.lock.json'):
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('{}')
            id='00000000-0000-4000-8000-000000000001';reads=[]
            def fake_http(method,path,*args,**kwargs):
                if path=='/api/auth/login':return 200,{'data':{'accessToken':'synthetic'}},{},0,0
                reads.append(path);time.sleep(.01)
                if len(reads)==1:return 200,{'success':True,'data':dict(requestId=id,status='SUCCEEDED',stateVersion=4,generation=1,attemptNo=1,resultUrl='/api/report-requests/'+id+'/file')},{},0,0
                return 500,{'success':False},{'X-Portfolio-Sql-Count':'0'},time.time()*1000,10
            args=argparse.Namespace(run_id='read-error-guard',mode='status',cache='off',rate=1,concurrency=1,warmup=0,duration=1,latency=200)
            with patch.object(report_benchmark,'ROOT',root),patch.object(report_benchmark.runtime,'STATE',state),patch.object(report_benchmark,'fingerprint',return_value={}),patch.object(report_benchmark,'dataset',return_value={}),patch.object(report_benchmark,'mysql',return_value=id+'\t1\t4\t1\t1'),patch.object(report_benchmark,'http',side_effect=fake_http),patch.object(report_benchmark,'proc_snapshot',return_value={}),patch.object(report_benchmark.http_client,'HTTPConnection') as connection:
                response=connection.return_value.getresponse.return_value;response.status=200;response.read.return_value=b'hikaricp_connections_pending 0\n'
                # when
                result=report_benchmark.run(args)
            # then
            self.assertTrue(result['guardStopped'])
            self.assertEqual(result['http']['status_read']['errors'],1)
            self.assertEqual(len(reads),2)
    def test_reference_preparation_failure_exits_process_and_archives_partial_csv(self):
        # given: HTTP/DB는 대체하되 실제 sampler 스레드와 run의 종료 경계는 실행한다.
        with tempfile.TemporaryDirectory() as directory:
            script=textwrap.dedent('''
                import argparse,json,os,sys
                from pathlib import Path
                from unittest.mock import patch
                sys.path.insert(0,sys.argv[1]);import report_benchmark as b
                base=Path(sys.argv[2]);state=base/'state';root=base/'repo'
                (state/'r07-m04').mkdir(parents=True)
                users=[dict(id=i+1,email='synthetic',password='synthetic') for i in range(20)]
                for name,value in [('browser-fixture.json',dict(users=users,company=1)),('browser-ready.json',dict(pid=os.getpid(),cache='off')),('app-source.json',dict(mainSha256={}))]:
                    (state/'r07-m04'/name).write_text(json.dumps(value))
                for name in ('infra/portfolio-images.lock.json','infra/cdc/images.lock.json'):
                    path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('{}')
                args=argparse.Namespace(run_id='failure-exit',mode='status',cache='off',rate=1,concurrency=1,warmup=0,duration=1,latency=200)
                with patch.object(b,'ROOT',root),patch.object(b.runtime,'STATE',state),patch.object(b,'fingerprint',return_value={}),patch.object(b,'dataset',return_value={}),patch.object(b,'mysql',return_value=''),patch.object(b,'http',return_value=(200,{'data':{'accessToken':'synthetic'}},{},0,0)),patch.object(b.http_client,'HTTPConnection',side_effect=RuntimeError('sampler-fixture')):
                    try:b.run(args)
                    except RuntimeError as failure:
                        assert str(failure)=='소유 완료 fixture 없음'
                    else:raise AssertionError('예외 주입 실패')
            ''')
            # when / then: 이전 구현은 비데몬 sampler가 살아 있어 이 제한을 넘는다.
            try:result=subprocess.run([sys.executable,'-c',script,str(Path(report_benchmark.__file__).parent),directory],capture_output=True,text=True,timeout=8)
            except subprocess.TimeoutExpired:self.fail('측정 준비 실패 뒤 sampler가 종료되지 않았습니다')
            self.assertEqual(result.returncode,0,result.stderr)
            partial=Path(directory)/'repo/perf/results/20261112-r07-m03-m04/failure-exit/requests.csv'
            self.assertTrue(partial.read_text().startswith('label,'))
    def test_fixed_arrival_phase_waits_until_full_window_after_last_submission(self):
        class ClockEvent:
            now=0.0
            def is_set(self):return False
            def wait(self,seconds):self.now+=seconds
        event=ClockEvent();submitted=[]
        elapsed=report_benchmark.fixed_arrivals(lambda index,planned:submitted.append((index,event.now)),1,30,event,clock=lambda:event.now)
        self.assertEqual(len(submitted),30)
        self.assertEqual(submitted[-1][1],29)
        self.assertEqual(elapsed,30)
    def test_connection_failure_keeps_actual_elapsed_instead_of_fabricated_timeout(self):
        with patch('http.client.HTTPConnection') as connection,patch.object(report_benchmark.time,'perf_counter',side_effect=[10.0,10.025]):
            connection.return_value.request.side_effect=ConnectionRefusedError('fixture')
            with self.assertRaises(report_benchmark.MeasuredTransportError) as failure:report_benchmark.http('GET','/fixture')
            self.assertAlmostEqual(failure.exception.elapsed_ms,25.0)
            self.assertEqual(failure.exception.status,0)
            self.assertEqual(failure.exception.reason,'ConnectionRefusedError')
    def test_load_parameters_are_bounded_before_creating_a_run(self):
        base=dict(rate=1,concurrency=10,warmup=30,duration=120,latency=200)
        for field,value in [('rate',0),('rate',21),('rate',float('nan')),('concurrency',101),('duration',0),('latency',10001)]:
            with self.subTest(field=field,value=value):
                with self.assertRaises(ValueError):report_benchmark.validate_options(argparse.Namespace(**(base|{field:value})))
    def test_native_results_are_archived_even_after_failed_run(self):
        with tempfile.TemporaryDirectory() as directory:
            base=Path(directory);args=argparse.Namespace(run_id='native-failure')
            def fail_run(value):
                target=Path(value.output_dir);target.mkdir(parents=True);(target/'partial.json').write_text('{}')
                raise RuntimeError('fixture')
            with patch.object(report_benchmark,'ROOT',base/'repo'),patch.object(report_benchmark.runtime,'STATE',base/'state'),patch.object(report_benchmark,'_run',side_effect=fail_run):
                with self.assertRaises(RuntimeError):report_benchmark.run(args)
            self.assertTrue((base/'repo/perf/results/20261112-r07-m03-m04/native-failure/partial.json').exists())
    def test_http_transport_returns_safe_metadata(self):
        with patch('http.client.HTTPConnection') as connection:
            response=connection.return_value.getresponse.return_value
            response.status=202;response.read.return_value=b'{"data":{"requestId":"fixture"}}';response.getheaders.return_value=[]
            result=report_benchmark.http('POST','/fixture',body={})
            self.assertEqual(result[0],202)
            self.assertEqual(result[1]['data']['requestId'],'fixture')
    def test_202_is_not_business_completion_and_waiting_is_retained(self):
        # given / when
        result = reconcile([str(i) for i in range(100)], [dict(id=str(i), status='SUCCEEDED' if i < 60 else 'FAILED' if i < 80 else 'ENQUEUED', artifacts=1 if i < 60 else 0) for i in range(100)])
        # then
        self.assertEqual(result['accepted'],100)
        self.assertEqual(result['succeeded'],60)
        self.assertEqual(result['failed'],20)
        self.assertEqual(result['remaining'],20)
        self.assertEqual(result['missing'],0)

    def test_missing_rows_duplicates_and_invalid_artifacts_are_errors(self):
        result = reconcile(['a','b'],[dict(id='a',status='SUCCEEDED',artifacts=2),dict(id='a',status='SUCCEEDED',artifacts=2)])
        self.assertEqual(result['missing'],1)
        self.assertEqual(result['duplicateRows'],1)
        self.assertEqual(result['invalidArtifacts'],1)

if __name__=='__main__': unittest.main()
