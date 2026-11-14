import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("verifier", Path(__file__).parents[1] / "verify_test_results.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class ResultVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def report(self, body, attributes='tests="1" failures="0" errors="0" skipped="0"'):
        folder = self.root / "fastTest"
        folder.mkdir(exist_ok=True)
        path = folder / "TEST-example.xml"
        path.write_text(f'<testsuite name="Example" {attributes}>{body}</testsuite>')
        return path

    def test_counts_and_skip_reasons(self):
        self.report('<testcase name="ok"/><testcase name="bad"><failure/></testcase>'
                    '<testcase name="error"><error/></testcase>'
                    '<testcase name="later"><skipped message="needs fixture"/></testcase>',
                    'tests="4" failures="1" errors="1" skipped="1"')
        result = verifier.verify(self.root, ["fastTest"])
        self.assertEqual({"tests": 4, "passed": 1, "failures": 1, "errors": 1, "skipped": 1}, result["totals"])
        self.assertEqual("FAIL", result["status"])
        self.assertEqual("needs fixture", result["tasks"][0]["skips"][0]["reason"])

    def test_missing_empty_and_all_skipped_are_not_success(self):
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"])["status"])
        self.report('')
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"])["status"])
        self.report('<testcase name="later"><skipped/></testcase>', 'tests="1" failures="0" errors="0" skipped="1"')
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"])["status"])

    def test_malformed_or_inconsistent_reports_are_rejected(self):
        path = self.report('<testcase name="ok"/>', 'tests="3" failures="0" errors="0" skipped="0"')
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"])["status"])
        path.write_text('<broken>')
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"])["status"])

    def test_freshness_required_tasks_and_strict_skips(self):
        path = self.report('<testcase name="ok"/>')
        self.assertEqual("PASS", verifier.verify(self.root, ["fastTest"])["status"])
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"], since=path.stat().st_mtime + 10)["status"])
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest", "reliabilityTest"])["status"])
        self.report('<testcase name="ok"/><testcase name="later"><skipped/></testcase>', 'tests="2" failures="0" errors="0" skipped="1"')
        self.assertEqual("PASS_WITH_SKIPS", verifier.verify(self.root, ["fastTest"])["status"])
        self.assertEqual("INCOMPLETE", verifier.verify(self.root, ["fastTest"], deny_skips=True)["status"])


if __name__ == "__main__":
    unittest.main()
