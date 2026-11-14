import importlib.util,json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).parents[1]))
spec=importlib.util.spec_from_file_location('business_cdc',Path(__file__).parents[1]/'configure_cdc.py');cdc=importlib.util.module_from_spec(spec);spec.loader.exec_module(cdc)

class RecoveryTransitionTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);root=Path(self.temp.name);(root/'relay-mode.json').write_text('{"relayMode":"cdc"}')
        self.desired={name:name for name in ('connector.class','database.include.list','table.include.list','topic.prefix','schema.history.internal.kafka.topic')};self.stage='initial';self.calls=[];self.stop_polls=0;self.resume_polls=0;self.clock=0;self.never_stops=False;self.resume_fails=False
        for target,name,value in [(cdc.runtime,'STATE',root),(cdc.runtime,'sync_workspace',lambda:None),(cdc.runtime,'setup',lambda:None),(cdc,'template',lambda:self.desired),(cdc.cdc,'wait_worker',lambda:None),(cdc.cdc,'request',self.request),(cdc.time,'sleep',lambda value:None),(cdc.time,'monotonic',self.tick)]:
            p=patch.object(target,name,value);p.start();self.addCleanup(p.stop)
    def tick(self):self.clock+=1;return self.clock
    def running(self):return {'connector':{'state':'RUNNING'},'tasks':[{'state':'RUNNING'}]}
    def request(self,path,method='GET',data=None):
        self.calls.append((path,method))
        if path.endswith('/config'):return dict(self.desired)
        if path.endswith('/offsets'):return {'offsets':[{'partition':{'server':'fixed'},'offset':{'file':'mysql-bin.000001','pos':123}}]}
        if path.endswith('/stop'):self.stage='stopping';return None
        if path.endswith('/resume'):
            self.assertEqual('stopped',self.stage);self.stage='resuming';return None
        if '/restart?' in path:self.stage='old-restart';return {}
        if path.endswith('/status'):
            if self.stage=='old-restart':return self.running() # 비동기 요청 직후의 오래된 성공 상태
            if self.stage=='stopping':
                self.stop_polls+=1
                if self.stop_polls==1 or self.never_stops:return self.running()
                self.stage='stopped';return {'connector':{'state':'STOPPED'},'tasks':[]}
            if self.stage=='resuming':
                self.resume_polls+=1
                if self.resume_fails:return {'connector':{'state':'RUNNING'},'tasks':[{'state':'FAILED'}]}
                return self.running()
            return self.running()
        raise AssertionError('예상하지 않은 CDC 작업')
    def test_old_running_then_new_failed_cannot_report_recovered(self):
        # given / when / then
        self.resume_fails=True
        with self.assertRaises(RuntimeError):cdc.recover()
    def test_success_requires_confirmed_stop_then_new_running_without_offset_changes(self):
        # given / when
        result=cdc.recover()
        # then
        self.assertTrue(result['stoppedConfirmed']);self.assertEqual('RUNNING',result['state']);self.assertEqual(result['checkpointBefore'],result['checkpointAfter'])
        self.assertIn(('/connectors/'+cdc.NAME+'/stop','PUT'),self.calls);self.assertIn(('/connectors/'+cdc.NAME+'/resume','PUT'),self.calls)
        self.assertFalse(any(method in ('DELETE','PATCH') for _,method in self.calls))
    def test_unconfirmed_stop_never_resumes_or_reports_success(self):
        # given / when / then
        self.never_stops=True
        with self.assertRaises(RuntimeError):cdc.recover()
        self.assertFalse(any(path.endswith('/resume') for path,_ in self.calls))
    def test_changed_scope_is_rejected_before_stopping(self):
        # given
        original=self.request
        def changed(path,method='GET',data=None):
            result=original(path,method,data)
            if path.endswith('/config'):result['table.include.list']='other.table'
            return result
        # when / then
        with patch.object(cdc.cdc,'request',changed),self.assertRaises(ValueError):cdc.recover()
        self.assertFalse(any(method!='GET' for _,method in self.calls))

if __name__=='__main__':unittest.main()
