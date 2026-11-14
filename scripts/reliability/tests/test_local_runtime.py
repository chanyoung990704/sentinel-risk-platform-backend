import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("local_runtime", Path(__file__).parents[1] / "local_runtime.py")
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class LocalEnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "infra").mkdir()
        self.lock = self.root / "infra/portfolio-images.lock.json"
        self.lock.write_text(json.dumps({key: f"{key}@sha256:" + "a" * 64 for key in ("mysql", "redis", "kafka", "python")}))

    def test_parent_environment_cannot_redirect_local_runtime(self):
        # given: 다른 프로젝트를 실행할 때 사용한 외부 주소가 남아 있다.
        with patch.object(runtime, "WORKSPACE", self.root), patch.dict(os.environ, {
            "SPRING_DATASOURCE_URL": "jdbc:mysql://remote/other",
            "SPRING_DATASOURCE_PASSWORD": "not-a-real-secret",
            "AI_SERVER_URL": "https://remote.invalid",
            "REDIS_HOST": "remote.invalid",
            "SERVER_ADDRESS": "0.0.0.0",
            "SPRING_DATA_REDIS_URL": "redis://external.invalid:6379",
            "TURNSTILE_VERIFY_URL": "https://external.invalid/verify",
            "JAVA_TOOL_OPTIONS": "-Dspring.profiles.active=prod",
        }):
            # when/then: local 실행은 지정한 로컬 구성과 비밀 파일만 사용한다.
            env = runtime.environment()
            self.assertTrue(env["SPRING_DATASOURCE_URL"].startswith("jdbc:mysql://127.0.0.1:13306/sentinel_portfolio"))
            self.assertNotIn("SPRING_DATASOURCE_PASSWORD", env)
            self.assertEqual("http://127.0.0.1:18081", env["AI_SERVER_URL"])
            self.assertEqual("127.0.0.1", env["REDIS_HOST"])
            self.assertEqual("127.0.0.1", env["SERVER_ADDRESS"])
            self.assertNotIn("SPRING_DATA_REDIS_URL", env)
            self.assertNotIn("JAVA_TOOL_OPTIONS", env)
            self.assertEqual("http://127.0.0.1:18081/turnstile-disabled", env["TURNSTILE_VERIFY_URL"])

    def test_floating_image_reference_is_rejected(self):
        images = json.loads(self.lock.read_text())
        images["mysql"] = "mysql:latest"
        self.lock.write_text(json.dumps(images))
        with patch.object(runtime, "WORKSPACE", self.root):
            with self.assertRaises(ValueError):
                runtime.environment()

    def test_only_application_launch_enables_durable_delivery_and_worker(self):
        # given/when: Gradle/도구 환경과 실제 앱의 기동 역할을 분리한다.
        with patch.object(runtime, "WORKSPACE", self.root):
            base = runtime.environment()
            application = runtime.application_environment()
        # then: 컴포넌트 시험에 자동 작업자가 섞이지 않는다.
        self.assertNotIn("CUSTOM_REPORT_MESSAGING_ENABLED", base)
        self.assertNotIn("CUSTOM_REPORT_WORKER_ENABLED", base)
        self.assertEqual("true", application["CUSTOM_REPORT_MESSAGING_ENABLED"])
        self.assertEqual("true", application["CUSTOM_REPORT_WORKER_ENABLED"])
        self.assertNotIn("SPRING_KAFKA_LISTENER_AUTO_STARTUP", application)
        self.assertEqual("true", application["CUSTOM_REPORT_MESSAGING_AUTO_STARTUP"])

    def test_optimized_python_still_rejects_failed_dependency_probe(self):
        # given/when: 최적화 모드에서도 잘못된 응답을 UP으로 보고하면 안 된다.
        code = f"""
import importlib.util, io
from types import SimpleNamespace
spec = importlib.util.spec_from_file_location('runtime', {str(Path(runtime.__file__).resolve())!r})
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.compose = lambda *args, **kwargs: SimpleNamespace(stdout='WRONG')
module.urllib.request.urlopen = lambda *args, **kwargs: io.BytesIO(b'{{"status":"UP"}}')
try:
    module.verify()
except RuntimeError:
    raise SystemExit(0)
raise SystemExit(1)
"""
        result = subprocess.run([sys.executable, "-B", "-O", "-c", code], capture_output=True)
        self.assertEqual(0, result.returncode)


class WorkspaceSyncTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.source = Path(self.temp.name) / "source"
        self.workspace = Path(self.temp.name) / "runtime"
        (self.source / "src").mkdir(parents=True)
        for name in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat", "docker-compose.portfolio.yml"):
            (self.source / name).write_text(name)
        (self.source / "src/Removed.java").write_text("old source")

    def test_deleted_source_is_removed_but_runtime_build_is_retained(self):
        with patch.object(runtime, "SOURCE", self.source), patch.object(runtime, "WORKSPACE", self.workspace):
            runtime.sync_workspace()
            (self.workspace / "build").mkdir()
            generated = self.workspace / "build/keep.data"
            generated.write_text("runtime state")
            # when: 원본 파일을 삭제한 다음 다시 동기화한다.
            (self.source / "src/Removed.java").unlink()
            runtime.sync_workspace()
            # then: 제거한 소스가 다시 빌드되지 않으며 build는 건드리지 않는다.
            self.assertFalse((self.workspace / "src/Removed.java").exists())
            self.assertEqual("runtime state", generated.read_text())

    def test_perf_tools_and_fixtures_are_synced_without_run_outputs(self):
        # given: 계측 코드와 고정 입력은 실행 소스이며 결과물은 복사 대상이 아니다.
        for folder in ("scripts/perf", "perf/fixtures", "perf/jmeter", "perf/results/private"):
            (self.source / folder).mkdir(parents=True)
        (self.source / "scripts/perf/driver.py").write_text("driver")
        (self.source / "perf/fixtures/sample.csv").write_text("sample")
        (self.source / "perf/jmeter/read.jmx").write_text("scenario")
        (self.source / "perf/results/private/keep.txt").write_text("do not copy")
        with patch.object(runtime, "SOURCE", self.source), patch.object(runtime, "WORKSPACE", self.workspace):
            # when / then
            runtime.sync_workspace()
            self.assertEqual("driver", (self.workspace / "scripts/perf/driver.py").read_text())
            self.assertEqual("sample", (self.workspace / "perf/fixtures/sample.csv").read_text())
            self.assertEqual("scenario", (self.workspace / "perf/jmeter/read.jmx").read_text())
            self.assertFalse((self.workspace / "perf/results/private/keep.txt").exists())

    def test_invalid_owned_path_cannot_delete_outside_workspace(self):
        with patch.object(runtime, "SOURCE", self.source), patch.object(runtime, "WORKSPACE", self.workspace):
            runtime.sync_workspace()
            outside = self.workspace.parent / "outside.txt"
            outside.write_text("keep")
            (self.workspace / ".portfolio-source-files.json").write_text('["../outside.txt"]')
            with self.assertRaises(ValueError):
                runtime.sync_workspace()
            self.assertEqual("keep", outside.read_text())

    def test_mysql_config_copied_from_windows_is_not_world_writable(self):
        folder = self.source / "infra/cdc"
        folder.mkdir(parents=True)
        config = folder / "mysql-cdc.cnf"
        config.write_text("[mysqld]\nserver-id=223344\n")
        config.chmod(0o777)
        with patch.object(runtime, "SOURCE", self.source), patch.object(runtime, "WORKSPACE", self.workspace):
            runtime.sync_workspace()
            # MySQL은 world-writable 설정을 무시하므로 내용 복사만으로는 부족하다.
            self.assertEqual(0, (self.workspace / "infra/cdc/mysql-cdc.cnf").stat().st_mode & 0o022)
