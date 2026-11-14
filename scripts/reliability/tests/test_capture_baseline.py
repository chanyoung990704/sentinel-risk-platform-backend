import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("capture", Path(__file__).parents[1] / "capture_baseline.py")
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class BaselineCaptureTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        folder = self.root / "src/test/java"
        folder.mkdir(parents=True)
        (folder / "SampleTest.java").write_text('@org.junit.jupiter.api.Disabled\nclass SampleTest {}')
        for name in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat"):
            (self.root / name).write_text(name)

    def test_qualified_disabled_is_included_without_invented_reason(self):
        result = capture.capture(self.root, self.root)
        self.assertEqual(1, len(result["testSourceInventory"][0]["annotations"]))
        self.assertEqual('@org.junit.jupiter.api.Disabled', result["testSourceInventory"][0]["annotations"][0]["annotation"])

    def test_fingerprint_detects_missing_and_changed_inputs(self):
        other = self.root / "other"
        other.mkdir()
        for name in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat"):
            (other / name).write_text(name)
        (other / "build.gradle").write_text("changed")
        result = capture.capture(self.root, other)
        self.assertEqual(2, result["sourceMismatchCount"])
        self.assertIn('build.gradle', result["sourceMismatches"])

    def test_perf_driver_and_fixture_changes_affect_fingerprint(self):
        # given
        (self.root / "scripts/perf").mkdir(parents=True)
        (self.root / "perf/fixtures").mkdir(parents=True)
        driver = self.root / "scripts/perf/driver.py"
        fixture = self.root / "perf/fixtures/sample.csv"
        driver.write_text("before"); fixture.write_text("before")
        before = capture.fingerprint(self.root)
        # when
        driver.write_text("after"); fixture.write_text("after")
        # then
        self.assertNotEqual(before, capture.fingerprint(self.root))
