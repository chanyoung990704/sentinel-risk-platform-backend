import csv
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from portfolio_summary import percentile, summarize


class SummaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def sample(self, start=0, elapsed=100, status=200, success=True, phase="measure", label="company_overview", sql="2", expected=200):
        return dict(label=label, phase=phase, start_ms=start, elapsed_ms=elapsed, status=status,
                    expected_status=expected, success=str(success).lower(), sql_count=sql,
                    response_hash="a" * 64, request_key="company-1")

    def run_samples(self, rows, finished=True, lifecycle=None):
        with (self.root / "requests.csv").open("w", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=list(self.sample()))
            writer.writeheader(); writer.writerows(rows)
        (self.root / "manifest.json").write_text(json.dumps(dict(schemaVersion=1, runId="golden", mode="read", fixture=True)))
        if finished:
            (self.root / "runner-finished.json").write_text(json.dumps(dict(requestRows=len(rows), completed=True)))
        if lifecycle is not None:
            with (self.root / "lifecycle.csv").open("w", newline="") as file:
                writer = csv.DictWriter(file, fieldnames=["request_id", "state", "timestamp_ms"])
                writer.writeheader(); writer.writerows(lifecycle)
        return summarize(self.root)

    def test_nearest_rank(self):
        self.assertEqual(50, percentile([10, 20, 30, 40, 50], .95))
        self.assertEqual(30, percentile([50, 40, 30, 20, 10], .5))

    def test_empty_percentile_is_unavailable(self):
        self.assertIsNone(percentile([], .99))

    def test_observation_ends_at_last_completion(self):
        result = self.run_samples([self.sample(), self.sample(start=100)])['labels']['company_overview']
        self.assertEqual(200, result['durationMs']); self.assertEqual(10, result['successTps'])

    def test_fast_failures_are_reported_separately(self):
        result = self.run_samples([self.sample(), self.sample(elapsed=1, status=500, success=False)])['labels']['company_overview']
        self.assertEqual(100, result['successLatencyMs']['p95'])
        self.assertEqual(1, result['failed']); self.assertEqual(50, result['httpErrorPercent'])
        self.assertEqual(1, result['failureLatencyMs']['p95'])

    def test_warmup_is_excluded(self):
        result = self.run_samples([self.sample(elapsed=10000, phase='warmup'), self.sample(start=20000)])['labels']['company_overview']
        self.assertEqual(1, result['requests']); self.assertEqual(100, result['durationMs'])

    def test_quoted_csv_label_and_header_order(self):
        result = self.run_samples([self.sample(label='company,overview')])
        self.assertIn('company,overview', result['labels'])

    def test_empty_measurement_is_not_zero_performance(self):
        result = self.run_samples([])
        self.assertFalse(result['measurementComplete']); self.assertEqual({}, result['labels'])

    def test_zero_interval_is_invalid(self):
        with self.assertRaises(ValueError): self.run_samples([self.sample(elapsed=0)])

    def test_negative_and_nonfinite_duration_are_invalid(self):
        for value in [-1, 'NaN', 'Infinity']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.run_samples([self.sample(elapsed=value)])

    def test_expected_rejections_remain_in_http_error_denominator(self):
        result = self.run_samples([self.sample(), self.sample(status=503, expected=503, success=False)])['labels']['company_overview']
        self.assertEqual(1, result['expectedRejections']); self.assertEqual(0, result['unexpectedResponses'])
        self.assertEqual(50, result['httpErrorPercent'])

    def test_missing_sql_is_unavailable(self):
        result = self.run_samples([self.sample(sql='')])['labels']['company_overview']
        self.assertIsNone(result['sqlPerRequest']); self.assertEqual(0, result['sqlSampleCount'])

    def test_partial_sql_coverage_is_explicit(self):
        result = self.run_samples([self.sample(sql='3'), self.sample(start=100, sql='')])['labels']['company_overview']
        self.assertEqual(1, result['sqlSampleCount']); self.assertEqual(50, result['sqlCoveragePercent'])
        self.assertIsNone(result['sqlPerRequest'])

    def test_202_is_not_job_completion(self):
        result = self.run_samples([self.sample(status=202, expected=202)])
        self.assertEqual(1, result['labels']['company_overview']['accepted202'])
        self.assertIsNone(result['lifecycle'])

    def test_lifecycle_terminal_states_are_separate(self):
        result = self.run_samples([self.sample(status=202, expected=202)], lifecycle=[
            dict(request_id='r1', state='SUCCEEDED', timestamp_ms=200),
            dict(request_id='r1', state='SUCCEEDED', timestamp_ms=201),
            dict(request_id='r2', state='FAILED', timestamp_ms=300)])
        self.assertEqual(1, result['lifecycle']['succeeded']); self.assertEqual(1, result['lifecycle']['failed'])

    def test_missing_finished_marker_is_incomplete(self):
        result = self.run_samples([self.sample()], finished=False)
        self.assertFalse(result['measurementComplete'])

    def test_finished_row_count_must_match(self):
        self.run_samples([self.sample()])
        (self.root / 'runner-finished.json').write_text(json.dumps(dict(completed=True, requestRows=2)))
        self.assertFalse(summarize(self.root)['measurementComplete'])

    def test_non_successful_http_cannot_be_a_success(self):
        with self.assertRaises(ValueError): self.run_samples([self.sample(status=500, success=True)])

    def test_small_p99_is_marked_as_reference(self):
        result = self.run_samples([self.sample()])['labels']['company_overview']
        self.assertTrue(result['p99ReferenceOnly'])


if __name__ == '__main__': unittest.main()
