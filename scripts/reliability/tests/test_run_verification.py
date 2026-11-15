"""앱용 DB 설정이 H2 회귀 시험에 섞이지 않는지 확인한다."""
import contextlib
import io
import json
import os
import tempfile
import time
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import run_verification


class VerificationEnvironmentTest(unittest.TestCase):
    def test_test_profile_owns_datasource_while_namespace_endpoints_survive(self):
        # given
        configured={'SPRING_PROFILES_ACTIVE':'local','SPRING_DATASOURCE_URL':'local-mysql',
                    'SPRING_DATASOURCE_USERNAME':'local-user','SPRING_DATASOURCE_DRIVER_CLASS_NAME':'mysql-driver',
                    'PORTFOLIO_SECRET_DIR':'/private/restore/secrets','PORTFOLIO_DATA_DIR':'/private/restore',
                    'SENTINEL_RUNTIME_NAMESPACE':'restore','REDIS_PORT':'16379','AI_SERVER_URL':'http://127.0.0.1:18081'}
        with patch.object(run_verification.runtime,'environment',return_value=configured):
            # when
            actual=run_verification.test_environment()
        # then
        self.assertFalse(any(key.startswith('SPRING_DATASOURCE_') for key in actual))
        self.assertNotIn('SPRING_PROFILES_ACTIVE',actual)
        for key in ('PORTFOLIO_SECRET_DIR','PORTFOLIO_DATA_DIR','SENTINEL_RUNTIME_NAMESPACE','REDIS_PORT','AI_SERVER_URL'):
            self.assertEqual(configured[key],actual[key])
        self.assertEqual('local-mysql',configured['SPRING_DATASOURCE_URL'])


class AggregateVerificationTest(unittest.TestCase):
    SCOPES=('modulithTest','fastTest','realInfraTest','cdcTest','reliabilityTest')

    def execute_fixture(self, missing=None, empty=False, stale=None, skips=False):
        with tempfile.TemporaryDirectory() as directory, contextlib.ExitStack() as stack:
            root=Path(directory);source=root/'source';workspace=root/'native';state=root/'state';out=root/'results'
            out.mkdir()
            stack.enter_context(patch.object(run_verification.runtime,'SOURCE',source))
            stack.enter_context(patch.object(run_verification.runtime,'WORKSPACE',workspace))
            stack.enter_context(patch.object(run_verification.runtime,'STATE',state))
            stack.enter_context(patch.object(run_verification.ops,'RESULTS',out))
            for owner,name in ((run_verification.ops,'require_experiment'),(run_verification.ops,'verify_topology'),
                               (run_verification.ops,'require_idle'),(run_verification.runtime,'sync_workspace'),
                               (run_verification.runtime,'setup')):
                stack.enter_context(patch.object(owner,name))
            stack.enter_context(patch.object(run_verification,'test_environment',return_value={}))
            stack.enter_context(patch.object(run_verification,'fingerprint',return_value={'src/main/App.java':'same'}))
            if skips:
                historic=source/'perf/results/20261112-r07-m03-m04/precompare-full-fastTest-xml'
                historic.mkdir(parents=True)
                historic.joinpath('TEST-legacy.xml').write_text('<testsuite>'+''.join(
                    f'<testcase classname="before.LegacyTest" name="disabled{i}"><skipped/></testcase>' for i in range(15))+'</testsuite>')

            def gradle_fixture(command, **kwargs):
                # given: Gradle 의존 task가 만드는 실제 형식의 XML. DB/컨테이너/Java는 실행하지 않는다.
                for scope in self.SCOPES:
                    target=workspace/'build/test-results'/scope
                    if scope==missing:
                        if empty:target.mkdir(parents=True)
                        continue
                    target.mkdir(parents=True)
                    disabled=''.join(f'<testcase classname="fixture.LegacyTest" name="disabled{i}"><skipped/></testcase>'
                                     for i in range(15)) if skips and scope=='fastTest' else ''
                    count=15 if disabled else 0
                    path=target/('TEST-'+scope+'.xml')
                    path.write_text(f'<testsuite name="fixture.{scope}" tests="{1+count}" failures="0" errors="0" skipped="{count}">'
                                    f'<testcase classname="fixture.{scope}" name="executes"/>'+disabled+'</testsuite>')
                    if scope==stale:os.utime(path,(time.time()-3600,time.time()-3600))
                strict=run_verification.verify(workspace/'build/test-results',self.SCOPES,since=time.time()-60,deny_skips=True)
                report=workspace/'build/reports/portfolio-verification.json';report.parent.mkdir(parents=True)
                report.write_text(json.dumps(strict))
                code=0 if strict['status']=='PASS' else 2
                if code:kwargs['stdout'].write(':verifiedCheck FAILED\n')
                return SimpleNamespace(returncode=code)

            stack.enter_context(patch.object(run_verification.subprocess,'run',side_effect=gradle_fixture))
            # when
            with contextlib.redirect_stdout(io.StringIO()):
                code=run_verification.run('aggregate',['bootJar','verifiedCheck'])
            return code,json.loads((out/'aggregate/command.json').read_text())

    def test_aggregate_collects_all_five_dependency_scopes(self):
        code,result=self.execute_fixture()
        # then
        self.assertEqual(0,code)
        self.assertEqual(set(self.SCOPES),{task['task'] for task in result['summary']['tasks']})
        self.assertEqual(5,len(result['tests']))
        self.assertEqual('PASS',result['summary']['status'])
        self.assertTrue(result['strictGatePassed'])

    def test_missing_or_empty_scope_stays_required(self):
        for empty in (False,True):
            with self.subTest(empty=empty):
                code,result=self.execute_fixture(missing='cdcTest',empty=empty)
                # then
                self.assertEqual(2,code)
                self.assertIsNotNone(result['summary'])
                self.assertEqual('INCOMPLETE',result['summary']['status'])
                scope=next(t for t in result['summary']['tasks'] if t['task']=='cdcTest')
                self.assertIn('missing or empty report directory',scope['problems'])
                self.assertFalse(result['strictGatePassed'])

    def test_stale_dependency_xml_cannot_pass(self):
        code,result=self.execute_fixture(stale='fastTest')
        # then
        self.assertEqual(2,code)
        self.assertIsNotNone(result['summary'])
        self.assertEqual('INCOMPLETE',result['summary']['status'])
        scope=next(t for t in result['summary']['tasks'] if t['task']=='fastTest')
        self.assertTrue(any('stale report' in p for p in scope['problems']))

    def test_inherited_skips_keep_strict_incomplete_and_original_exit(self):
        code,result=self.execute_fixture(skips=True)
        # then
        self.assertEqual(2,code)
        self.assertEqual(2,result['exitCode'])
        self.assertTrue(result['acceptedWithKnownSkips'])
        self.assertFalse(result['strictGatePassed'])
        self.assertEqual(15,result['summary']['totals']['skipped'])
