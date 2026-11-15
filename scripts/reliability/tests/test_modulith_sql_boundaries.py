"""Java 타입 검증으로 잡히지 않는 직접 SQL 경계를 보충한다."""
import re
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

class ModulithSqlBoundaryTest(unittest.TestCase):
    def test_every_known_table_is_accessed_only_by_its_owner(self):
        # given
        policy = json.loads((ROOT/'src/test/resources/architecture/table-ownership.json').read_text(encoding='utf-8-sig'))['tables']
        violations = []
        # when: 실제 SQL 테이블과 정확히 일치하는 이름만 검사한다. JPQL 타입은 Modulith가 검사한다.
        for path in (ROOT/'src/main/java/com/aivle/project').rglob('*.java'):
            owner = path.relative_to(ROOT/'src/main/java/com/aivle/project').parts[0]
            for match in re.finditer(r'\b(?:FROM|JOIN|UPDATE|INTO)\s+`?([a-z_]+)', path.read_text(encoding='utf-8'), re.I):
                table = match[1].lower()
                if table in policy and policy[table] != owner:
                    violations.append((path.relative_to(ROOT).as_posix(), table, policy[table]))
        # then
        self.assertEqual([], violations)

    def test_reporting_does_not_query_identity_or_finance_tables_directly(self):
        # given
        sources = list((ROOT/'src/main/java/com/aivle/project/reporting').rglob('*.java'))
        self.assertTrue(sources, '보고서 모듈의 실제 소스를 검사해야 한다')
        forbidden = {'users', 'companies', 'active_data_snapshots', 'data_snapshots',
                     'company_report_metric_values', 'company_report_versions', 'quarters', 'metrics'}
        violations = []
        # when
        for path in sources:
            text = path.read_text(encoding='utf-8')
            for match in re.finditer(r'\b(?:FROM|JOIN|UPDATE|INTO)\s+`?([a-z_]+)', text, re.I):
                if match[1].lower() in forbidden:
                    violations.append((path.relative_to(ROOT).as_posix(), match[1].lower()))
        # then
        self.assertEqual([], violations, '다른 도메인의 데이터는 소유 모듈 API를 통해 읽는다')

if __name__ == '__main__':
    unittest.main()
