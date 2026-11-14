"""원본을 기본값으로 유지하고 명시된 두 시험 namespace만 허용한다."""
import sys,unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import local_runtime as runtime

class RuntimeNamespaceTest(unittest.TestCase):
    def test_original_read_path_rejects_multi_statement_and_file_access(self):
        import operations_runtime as ops
        # given / when / then: 검사용 root SELECT 경로로 쓰기/파일 접근을 우회하지 않는다.
        with patch.object(ops.subprocess,'run') as execute,patch.object(runtime,'environment',return_value={}):
            execute.return_value.returncode=0;execute.return_value.stdout='';execute.return_value.stderr=''
            for query in ("SELECT 1; DELETE FROM report_requests","SELECT LOAD_FILE('/run/secrets/mysql-root')","SELECT 1 INTO OUTFILE '/tmp/out'","SELECT 1 /* hidden */"):
                with self.subTest(query=query),self.assertRaises(ValueError):ops.sql(query)
            execute.assert_not_called()

    def test_operations_mutation_refuses_original_and_wrong_project(self):
        # given / when / then
        from operations_runtime import require_experiment
        with patch.object(runtime,'NAMESPACE','portfolio'):
            with self.assertRaises(ValueError):require_experiment()
        with patch.object(runtime,'NAMESPACE','operations'),patch.object(runtime,'PROJECT','sentinel-portfolio'):
            with self.assertRaises(ValueError):require_experiment()

    def test_existing_portfolio_location_is_unchanged(self):
        # given / when
        config=runtime.namespace_config('portfolio')
        # then
        self.assertEqual(config['project'],'sentinel-portfolio')
        self.assertEqual(config['state'],Path.home()/'.local/share/sentinel/portfolio')
        self.assertEqual(config['workspace'],Path.home()/'.local/share/sentinel/workspaces/e02-runtime')

    def test_operations_restore_have_disjoint_owned_paths(self):
        # given / when
        configs=[runtime.namespace_config(name) for name in ('portfolio','operations','restore')]
        # then
        for field in ('project','state','workspace'):self.assertEqual(len({config[field] for config in configs}),3)
        self.assertTrue(str(configs[1]['state']).endswith('/sentinel/operations'))

    def test_unknown_namespace_and_traversal_are_rejected(self):
        # given / when / then
        for name in ('','other','../portfolio','portfolio;down','Operations'):
            with self.subTest(name=name):
                with self.assertRaises(ValueError):runtime.namespace_config(name)
