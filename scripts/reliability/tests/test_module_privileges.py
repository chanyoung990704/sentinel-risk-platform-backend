"""원본 DB 보호와 제한 계정의 SELECT-only 준비를 검증한다."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import module_privileges as subject


class ModulePrivilegeTests(unittest.TestCase):
    def test_original_namespace_rejects_before_any_files_or_sql(self):
        # given
        with tempfile.TemporaryDirectory() as directory, patch.object(subject.runtime, 'NAMESPACE', 'portfolio'), \
                patch.object(subject.runtime, 'STATE', Path(directory)), \
                patch.object(subject.ops, 'sql') as sql:
            # when / then
            with self.assertRaises(ValueError):
                subject.prepare()
            sql.assert_not_called()
            self.assertFalse((Path(directory)/'module-accounts').exists())

    def test_only_owned_tables_receive_select_grants_and_credentials_stay_private(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            policy = root/'src/test/resources/architecture'
            policy.mkdir(parents=True)
            (policy/'table-ownership.json').write_text(json.dumps({'tables': {'companies': 'finance', 'users': 'identity'}}))
            statements = []
            def sql(statement, write=False):
                statements.append((statement, write))
                return 'companies\nusers\n' if statement == 'SHOW TABLES' else ''
            with patch.object(subject.runtime, 'SOURCE', root), patch.object(subject.runtime, 'STATE', root), \
                    patch.object(subject.ops, 'require_experiment'), patch.object(subject.ops, 'verify_topology'), \
                    patch.object(subject.ops, 'sql', side_effect=sql):
                # when
                result = subject.prepare()
                # then
                grants = [statement for statement, _ in statements if statement.startswith('GRANT')]
                self.assertEqual(["GRANT SELECT ON sentinel_portfolio.`companies` TO 'sentinel_module_finance'@'%'"], grants)
                credential = root/'module-accounts/finance-password'
                self.assertEqual(0o600, credential.stat().st_mode & 0o777)
                self.assertNotIn(credential.read_text(), json.dumps(result))
                before = len(statements)
                with self.assertRaises(RuntimeError):
                    subject.prepare()
                self.assertFalse(any(write for _, write in statements[before:]))


if __name__ == '__main__': unittest.main()
