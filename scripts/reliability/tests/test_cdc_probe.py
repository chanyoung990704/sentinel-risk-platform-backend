import importlib.util
import unittest
import json
import sys
import tempfile
from types import SimpleNamespace
from unittest.mock import patch
from pathlib import Path

spec = importlib.util.spec_from_file_location("cdc_probe", Path(__file__).parents[1] / "cdc_probe.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class CdcDecisionTest(unittest.TestCase):
    def evidence(self):
        summary = {"status": "PASS", "totals": {"tests": 2, "passed": 2, "failures": 0, "errors": 0, "skipped": 0}}
        observations = {"completed": True, "snapshotReceived": True, "committedExpected": 21, "committedObserved": 21,
            "rollbackExpected": 10, "rollbackObserved": 0, "connectorResume": True,
            "brokerResume": True, "offsetsPreserved": True, "historyPreserved": True,
            "internalTopicsCompacted": True, "eventIdsPreserved": True,
            "observedUniqueCount": 21, "databaseCommittedRows": 21}
        return summary, observations

    def test_complete_probe_adopts_cdc(self):
        summary, observations = self.evidence()
        self.assertEqual("cdc", probe.decide(summary, observations)["relayMode"])

    def test_missing_failed_or_skipped_tests_cannot_select_mode(self):
        summary, observations = self.evidence()
        for total in (0, 1):
            summary["totals"]["tests"] = total
            self.assertEqual("undecided", probe.decide(summary, observations)["relayMode"])
        summary["totals"]["tests"] = 2
        summary["totals"]["skipped"] = 1
        self.assertEqual("undecided", probe.decide(summary, observations)["relayMode"])

    def test_partial_resume_or_lost_history_cannot_select_mode(self):
        summary, observations = self.evidence()
        for key in ("connectorResume", "brokerResume", "offsetsPreserved", "historyPreserved", "internalTopicsCompacted", "eventIdsPreserved"):
            damaged = dict(observations, **{key: False})
            self.assertEqual("undecided", probe.decide(summary, damaged)["relayMode"])
        self.assertEqual("undecided", probe.decide(summary, {})["relayMode"])

    def test_missing_or_rollback_events_cannot_select_mode(self):
        summary, observations = self.evidence()
        self.assertEqual("undecided", probe.decide(summary, dict(observations, committedObserved=20))["relayMode"])
        self.assertEqual("undecided", probe.decide(summary, dict(observations, rollbackObserved=1))["relayMode"])
        self.assertEqual("undecided", probe.decide(summary, dict(observations, observedUniqueCount=22))["relayMode"])

    def test_conflicting_lock_and_decision_remain_consistent(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            state = root / "state"
            state.mkdir()
            (state / "relay-mode.json").write_text('{"relayMode":"polling"}')
            (root / "infra/cdc").mkdir(parents=True)
            (root / "infra/cdc/images.lock.json").write_text('{"connect":"fixture"}')
            summary, observations = self.evidence()
            output = root / "perf/results/conflict-test"
            def fake_run(command, **kwargs):
                if command[0] == "bash":
                    (output / "observations.json").write_text(json.dumps(observations))
                return SimpleNamespace(returncode=0)
            runtime = SimpleNamespace(SOURCE=root, WORKSPACE=root, STATE=state,
                sync_workspace=lambda: None, environment=lambda: {})
            verifier = SimpleNamespace(verify=lambda *args, **kwargs: summary)
            with patch.dict(sys.modules, {"local_runtime": runtime, "verify_test_results": verifier}), \
                 patch.object(sys, "argv", ["cdc_probe.py", "--run-id", "conflict-test"]), \
                 patch.object(probe.subprocess, "run", side_effect=fake_run):
                self.assertEqual(1, probe.main())
            result = json.loads((output / "cdc-decision.json").read_text())
            self.assertEqual("undecided", result["relayMode"])
            self.assertTrue(any("selection" in reason for reason in result["rejectedReasons"]))
            self.assertEqual({"relayMode": "polling"}, json.loads((state / "relay-mode.json").read_text()))
