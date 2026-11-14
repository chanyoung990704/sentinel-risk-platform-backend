import sys,unittest,json,secrets,tempfile
from unittest.mock import patch
from types import SimpleNamespace
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import portfolio_run as runner

class RunnerTest(unittest.TestCase):
    def test_sql_rejects_multi_statement_and_write_forms_before_process(self):
        for sql in ('SELECT 1; DELETE FROM active_data_snapshots', 'EXPLAIN ANALYZE SELECT 1; UPDATE active_data_snapshots SET generation=0',
                    'EXPLAIN ANALYZE UPDATE active_data_snapshots SET generation=0', "SELECT 1 INTO OUTFILE '/tmp/fixture'",'SELECT 1 /*!; DELETE FROM active_data_snapshots */'):
            with self.subTest(sql=sql),patch.object(runner.runtime,'environment',return_value={}),patch.object(runner.subprocess,'run',return_value=SimpleNamespace(stdout='')) as execute:
                with self.assertRaises(ValueError):runner.mysql(sql)
                execute.assert_not_called()
    def test_valid_read_uses_select_only_account_without_password_arguments(self):
        credential=dict(username='sentinel_perf_'+secrets.token_hex(6),password=secrets.token_hex(32))
        with patch.object(runner,'ensure_mysql_reader',return_value=credential,create=True),patch.object(runner.runtime,'environment',return_value={}),patch.object(runner.subprocess,'run',return_value=SimpleNamespace(stdout='1\n')) as execute:
            self.assertEqual('1\n',runner.mysql('SELECT 1;'))
        args=execute.call_args.args[0];self.assertIn(credential['username'],args);self.assertNotIn(credential['password'],' '.join(args));self.assertEqual(credential['password'],execute.call_args.kwargs['env']['MYSQL_PWD'])
    def credentials_state(self):
        temporary=tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup)
        root=Path(temporary.name);(root/'secrets').mkdir()
        state=patch.object(runner.runtime,'STATE',root);state.start();self.addCleanup(state.stop)
        return root/'secrets/perf-user.json'
    def test_signup_temporary_failure_recovers_on_next_run_with_same_credentials(self):
        path=self.credentials_state();state=dict(created=False,signups=0)
        def request(method,url,body=None,token=None):
            if url.endswith('/login'):return (200,{'data':{'accessToken':'fixture-access'}}) if state['created'] else (401,{'success':False})
            state['signups']+=1
            if state['signups']==1:return 503,{'success':False}
            state['created']=True;return 201,{'success':True}
        with patch.object(runner,'json_request',side_effect=request):
            with self.assertRaises(RuntimeError):runner.login()
            before=path.read_bytes();self.assertEqual('fixture-access',runner.login());self.assertEqual(before,path.read_bytes())
    def test_signup_commit_response_loss_is_recovered_by_login_probe(self):
        path=self.credentials_state();state=dict(created=False)
        def request(method,url,body=None,token=None):
            if url.endswith('/login'):return (200,{'data':{'accessToken':'fixture-access'}}) if state['created'] else (401,{'success':False})
            state['created']=True;raise ConnectionResetError('fixture response loss after commit')
        with patch.object(runner,'json_request',side_effect=request):
            self.assertEqual('fixture-access',runner.login());before=path.read_bytes();self.assertEqual('fixture-access',runner.login());self.assertEqual(before,path.read_bytes())
    def test_signup_precommit_response_loss_can_retry_without_rotating_credentials(self):
        path=self.credentials_state();state=dict(created=False,signups=0)
        def request(method,url,body=None,token=None):
            if url.endswith('/login'):return (200,{'data':{'accessToken':'fixture-access'}}) if state['created'] else (401,{'success':False})
            state['signups']+=1
            if state['signups']==1:raise ConnectionResetError('fixture loss before commit')
            state['created']=True;return 201,{'success':True}
        with patch.object(runner,'json_request',side_effect=request):
            with self.assertRaises(RuntimeError):runner.login()
            before=path.read_bytes();self.assertEqual('fixture-access',runner.login());self.assertEqual(before,path.read_bytes())
    def test_existing_successful_user_does_not_repeat_signup(self):
        path=self.credentials_state();value=dict(email='fixture@sentinel.invalid',password=secrets.token_hex(32),name='fixture',turnstileToken='fixture')
        path.write_text(json.dumps(value))
        with patch.object(runner,'json_request',return_value=(200,{'data':{'accessToken':'fixture-access'}})) as request:
            self.assertEqual('fixture-access',runner.login())
        self.assertEqual(1,request.call_count);self.assertEqual('/api/auth/login',request.call_args.args[1])
    def test_canonical_hash_only_excludes_envelope_timestamp(self):
        a={'success':True,'data':{'timestamp':'business-time','values':[1,None]},'error':None,'timestamp':'a'}
        b=dict(a,timestamp='b')
        self.assertEqual(runner.canonical_hash(a),runner.canonical_hash(b))
        b['data']={'timestamp':'changed','values':[1,None]}
        self.assertNotEqual(runner.canonical_hash(a),runner.canonical_hash(b))
    def test_invalid_run_ids_are_rejected(self):
        for value in ('../private','/tmp/other','', 'with space'):
            with self.subTest(value=value),self.assertRaises(ValueError):runner.validate_run_id(value)
        self.assertEqual('read-before-c1-r1',runner.validate_run_id('read-before-c1-r1'))
    def test_missing_runtime_does_not_become_zero(self):
        self.assertIsNone(runner.prometheus_value('other_metric 1','hikaricp_connections_pending'))
        self.assertEqual(3,runner.prometheus_value('hikaricp_connections_pending{pool="main"} 3','hikaricp_connections_pending'))
    def test_guard_keeps_errors_and_unavailable_separate(self):
        label={'httpErrorPercent':1,'responseHashMismatches':0,'sqlCoveragePercent':100}
        self.assertIn('http-errors',runner.guard_reasons(label,[],0))
        label['httpErrorPercent']=0
        self.assertIn('runtime-unavailable',runner.guard_reasons(label,[],0))
    def test_pool_wait_and_memory_guard(self):
        label={'httpErrorPercent':0,'responseHashMismatches':0,'sqlCoveragePercent':100}
        rows=[{'pool_pending':2,'app_rss_bytes':100,'mem_available_bytes':1024**3}]*5
        self.assertIn('pool-pending-5-samples',runner.guard_reasons(label,rows,0))
        rows=[{'pool_pending':0,'app_rss_bytes':100,'mem_available_bytes':100}]
        self.assertIn('host-memory-low',runner.guard_reasons(label,rows,0))
