import sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from report_summary import window_summary, response_window_summary

class CompletionWindowTest(unittest.TestCase):
    def test_read_throughput_counts_completed_window_and_keeps_boundary_samples(self):
        rows=[dict(phase='warmup',start_ms=990,elapsed_ms=20,success=False),dict(phase='measure',start_ms=1100,elapsed_ms=100,success=True),dict(phase='measure',start_ms=1950,elapsed_ms=100,success=True)]
        result=response_window_summary(rows,dict(startMs=1000,endMs=2000))
        self.assertEqual(result['successTps'],1)
        self.assertEqual(result['completedResponses'],2)
        self.assertEqual(result['completedErrors'],1)
        self.assertEqual(result['carryOutResponses'],1)
        self.assertEqual(result['carryInResponses'],1)
        self.assertEqual(result['startedMainSamples'],2)
    def test_acceptance_and_observed_completion_use_different_clocks_and_counts(self):
        # given
        posts=[dict(phase='measure',label='report_accept',status='202',start_ms='1000',elapsed_ms='10',schedule_lag_ms='0',requestId='a'),dict(phase='measure',label='report_accept',status='202',start_ms='1500',elapsed_ms='700',schedule_lag_ms='0',requestId='b')]
        observed=[dict(id='a',status='SUCCEEDED',observedAtMs=1700),dict(id='b',status='SUCCEEDED',observedAtMs=2300)]
        lifecycle=[dict(id='a',status='SUCCEEDED',lastStateDbAt='1970-01-01 00:00:01.600000'),dict(id='b',status='SUCCEEDED',lastStateDbAt='1970-01-01 00:00:01.900000')]
        # when
        result=window_summary(posts,observed,lifecycle,1,False)
        # then
        self.assertEqual(result['acceptedResponseTps'],1)
        self.assertEqual(result['dbRecordedSucceededTps'],2)
        self.assertEqual(result['clientObservedSucceededTps'],1)
        self.assertEqual(result['lateAcceptedResponses'],1)

    def test_guarded_run_is_not_reported_as_a_full_capacity_measurement(self):
        result=window_summary([],[],[],120,True)
        self.assertFalse(result['validFullWindow'])
        self.assertIsNone(result['acceptedResponseTps'])

if __name__=='__main__':unittest.main()
