"""고정 Connect 포트를 사용하는 모든 변경 요청의 실제 소유권을 확인한다."""
import copy,json,subprocess,sys,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import cdc_runtime as cdc

class CdcOwnerTest(unittest.TestCase):
    def test_mutation_cannot_reach_http_before_owner_validation(self):
        # given / when / then
        with patch.object(cdc,'verify_worker_owner',side_effect=ValueError('foreign worker'),create=True),patch.object(cdc.urllib.request,'build_opener') as opener:
            opener.return_value.open.return_value.__enter__.return_value.read.return_value=b'null'
            with self.assertRaisesRegex(ValueError,'foreign worker'):cdc.request('/connectors/owned/stop','PUT')
            opener.assert_not_called()

    def test_worker_owner_checks_project_service_running_and_loopback_port(self):
        # given
        good={'Config':{'Labels':{'com.docker.compose.project':'sentinel-restore','com.docker.compose.service':'connect'}},'State':{'Running':True},'NetworkSettings':{'Ports':{'8083/tcp':[{'HostIp':'127.0.0.1','HostPort':'18083'}]}}}
        # when / then
        for problem in ('valid','project','service','stopped','port'):
            value=copy.deepcopy(good)
            if problem=='project':value['Config']['Labels']['com.docker.compose.project']='sentinel-portfolio'
            elif problem=='service':value['Config']['Labels']['com.docker.compose.service']='other'
            elif problem=='stopped':value['State']['Running']=False
            elif problem=='port':value['NetworkSettings']['Ports']['8083/tcp'][0]['HostIp']='0.0.0.0'
            with self.subTest(problem=problem),patch.object(cdc.runtime,'PROJECT','sentinel-restore'),patch.object(cdc.runtime,'compose',return_value=subprocess.CompletedProcess([],0,'owned-id\n','')),patch.object(cdc.runtime,'environment',return_value={}),patch.object(cdc.subprocess,'run',return_value=subprocess.CompletedProcess([],0,json.dumps([value]),'')):
                if problem=='valid':cdc.verify_worker_owner()
                else:
                    with self.assertRaises(ValueError):cdc.verify_worker_owner()
