"""고정 localhost 포트가 다른 프로젝트를 가리키면 시험 전에 거부한다."""
import copy,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import operations_runtime as ops

class TopologyTest(unittest.TestCase):
    def fixtures(self):
        return [dict(service=service,project='sentinel-restore',running=True,ports={inside:[{'HostIp':'127.0.0.1','HostPort':str(outside)}]}) for service,(inside,outside) in {'mysql':('3306/tcp',13306),'redis':('6379/tcp',16379),'status-cache-redis':('6379/tcp',16380),'kafka':('9094/tcp',19092),'mock-ai':('8080/tcp',18081),'connect':('8083/tcp',18083)}.items()]
    def test_all_host_ports_must_belong_to_the_same_expected_project(self):
        # given / when / then
        rows=self.fixtures();ops.validate_topology(rows,'sentinel-restore')
        for problem in ('missing','project','stopped','port','public'):
            with self.subTest(problem=problem):
                changed=copy.deepcopy(rows)
                if problem=='missing':changed.pop()
                elif problem=='project':changed[3]['project']='sentinel-portfolio'
                elif problem=='stopped':changed[3]['running']=False
                elif problem=='port':changed[3]['ports']['9094/tcp'][0]['HostPort']='19093'
                else:changed[3]['ports']['9094/tcp'][0]['HostIp']='0.0.0.0'
                with self.assertRaises(ValueError):ops.validate_topology(changed,'sentinel-restore')
