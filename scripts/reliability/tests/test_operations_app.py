"""ready 이전 실패도 소유 PID로 추적하되 재사용된 PID에는 신호를 보내지 않는다."""
import json,sys,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import operations_app as app

class OperationsAppTest(unittest.TestCase):
    def test_launch_ownership_exists_before_ready_and_reused_pid_is_rejected(self):
        # given: 앱이 포트를 열기 전에 시작한 PID와 nonce를 기록했다.
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)/'state';state.mkdir();proc=Path(directory)/'proc';process=proc/'123';process.mkdir(parents=True)
            value=dict(pid=123,nonce='owned-launch-nonce');(state/'app-launch.json').write_text(json.dumps(value))
            (process/'stat').write_text('123 (java) S');(process/'cmdline').write_bytes(b'java\0PortfolioOperationsProcess.java\0'+str(state).encode()+b'\0owned-launch-nonce\0')
            with patch.object(app,'STATE',state),patch.object(app,'PROC',proc,create=True):
                # when / then
                self.assertEqual(123,app.owned()['pid'])
                (process/'cmdline').write_bytes(b'java\0PortfolioOperationsProcess.java\0'+str(state).encode()+b'\0different-nonce\0')
                with self.assertRaises(RuntimeError):app.owned()
