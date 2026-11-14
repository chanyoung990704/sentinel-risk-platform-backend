"""수락 요청 분모에 격리 메시지와 중복/대기를 숨길 수 없다."""
import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from reconcile import reconcile

class ReconcileTest(unittest.TestCase):
    def good(self):return [dict(id='a',status='SUCCEEDED',jobs=1,artifacts=1,files=1,slot=0),dict(id='b',status='FAILED',jobs=1,artifacts=0,files=0,slot=0)]
    def test_expected_failure_and_quarantine_have_separate_denominators(self):
        # given / when / then
        result=reconcile(['a','b'],self.good(),expected_failed=['b'],quarantined=7)
        self.assertTrue(result['passed']);self.assertEqual(result['accepted'],2);self.assertEqual(result['quarantinedMessages'],7)
    def test_missing_duplicate_stuck_unexpected_failure_and_artifacts_fail(self):
        # given / when / then
        for rows in [self.good()[:1],self.good()+[self.good()[0]],
                     [self.good()[0],dict(self.good()[1],status='ENQUEUED')],
                     [dict(self.good()[0],artifacts=2),self.good()[1]],
                     [dict(self.good()[0],slot=1),self.good()[1]],
                     [dict(self.good()[0],files=0),self.good()[1]]]:
            self.assertFalse(reconcile(['a','b'],rows,expected_failed=['b'])['passed'])
        self.assertFalse(reconcile(['a','b'],self.good())['passed'])
    def test_duplicate_accepted_or_unknown_expected_failure_is_invalid(self):
        # given / when / then
        for accepted,failed in [(['a','a'],[]),(['a'],['unknown'])]:
            with self.assertRaises(ValueError):reconcile(accepted,self.good(),expected_failed=failed)
