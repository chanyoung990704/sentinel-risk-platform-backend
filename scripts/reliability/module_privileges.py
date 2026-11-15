"""operations/restore/modulith 전용 finance SELECT 제한 계정. 운영 앱 계정은 바꾸지 않는다."""
import json
import os
import secrets
from pathlib import Path
import local_runtime as runtime
import operations_runtime as ops


def prepare():
    ops.require_experiment()
    ops.verify_topology()
    policy_path = runtime.SOURCE/'src/test/resources/architecture/table-ownership.json'
    policy = json.loads(policy_path.read_text(encoding='utf-8-sig'))['tables']
    tables = ops.sql('SHOW TABLES').splitlines()
    directory = runtime.STATE/'module-accounts'
    directory.mkdir(exist_ok=True, mode=0o700)
    file = directory/'finance-password'
    if file.exists():
        raise RuntimeError('기존 제한 계정 자격을 보존합니다. SHOW GRANTS로 확인 후 재사용하세요.')
    password = secrets.token_hex(32)
    descriptor = os.open(file, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'w') as stream:
        stream.write(password)
    account = "'sentinel_module_finance'@'%'"
    # 비밀번호는 stdin으로만 전달하고 명령/출력/증거에는 포함하지 않는다.
    ops.sql('CREATE USER '+account+" IDENTIFIED BY '"+password+"'", write=True)
    granted = []
    for table in tables:
        if policy[table.lower()] == 'finance':
            ops.sql('GRANT SELECT ON sentinel_portfolio.`'+table+'` TO '+account, write=True)
            granted.append(table)
    return dict(namespace=runtime.NAMESPACE, account='sentinel_module_finance',
                grants='SELECT only', tables=granted)


if __name__ == '__main__':
    print(json.dumps(prepare()))
