"""앱용 DB 설정이 H2 회귀 시험에 섞이지 않는지 확인한다."""
import unittest
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
