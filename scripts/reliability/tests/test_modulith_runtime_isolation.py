"""과거 작업이 남은 DB를 회귀 시험이 처리하지 못하도록 막는다."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import local_runtime as runtime
import run_verification as runner


class ModulithRuntimeIsolationTest(unittest.TestCase):
    def test_modulith_namespace_has_distinct_paths_and_compose_project(self):
        # given / when
        config = runtime.namespace_config('modulith')
        others = [runtime.namespace_config(name) for name in ('portfolio', 'operations', 'restore')]
        # then
        for existing in others:
            for key in ('project', 'state', 'workspace'):
                self.assertNotEqual(config[key], existing[key])

    def test_existing_inflight_work_rejects_before_gradle_starts(self):
        # given: Gradle 자체는 정상 통과하도록 고정한다. 기존 미종결 작업이 거절 사유여야 한다.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);native = root/'native';native.mkdir()
            def gradle(*args, **kwargs):
                xml = native/'build/test-results/realInfraTest/TEST-Fixture.xml'
                xml.parent.mkdir(parents=True)
                xml.write_text('<testsuite name="Fixture" tests="1" failures="0" errors="0" skipped="0"><testcase classname="Fixture" name="passes"/></testsuite>')
                os.utime(xml, (time.time()+5, time.time()+5))
                return subprocess.CompletedProcess([], 0)
            with patch.object(runner.ops, 'require_experiment'), patch.object(runner.ops, 'verify_topology'), \
                    patch.object(runner.ops, 'RESULTS', root/'results'), \
                    patch.object(runtime, 'STATE', root/'state'), patch.object(runtime, 'SOURCE', root), \
                    patch.object(runtime, 'WORKSPACE', native), patch.object(runtime, 'sync_workspace'), \
                    patch.object(runtime, 'setup'), patch.object(runner, 'test_environment', return_value={}), \
                    patch.object(runner, 'fingerprint', return_value={}), \
                    patch.object(runner.ops, 'sql', return_value='74\n'), \
                    patch.object(runner.subprocess, 'run', side_effect=gradle) as command:
                (root/'results').mkdir()
                # when / then
                with self.assertRaisesRegex(RuntimeError, '미종결|실행 중'):
                    runner.run('busy-db', ['realInfraTest'])
                command.assert_not_called()


if __name__ == '__main__': unittest.main()
