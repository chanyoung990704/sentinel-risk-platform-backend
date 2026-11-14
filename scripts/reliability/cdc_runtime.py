"""전용 CDC 시험 계정/worker와 장애 주입을 관리한다. 비밀 값은 출력하지 않는다."""
import argparse
import json
import os
import secrets
import socket
import subprocess
import time
import urllib.request
from pathlib import Path
import local_runtime as runtime

def verify_worker_owner():
    identities=runtime.compose(['--profile','cdc','ps','-q','connect'],True).stdout.splitlines()
    if len(identities)!=1:raise ValueError('Connect 서비스 소유권 불일치')
    value=json.loads(subprocess.run(['docker','inspect',identities[0]],env=runtime.environment(),capture_output=True,text=True,check=True).stdout)[0]
    labels=value['Config']['Labels'];ports=value['NetworkSettings']['Ports']
    if labels.get('com.docker.compose.project')!=runtime.PROJECT or labels.get('com.docker.compose.service')!='connect' or value['State']['Running'] is not True or ports.get('8083/tcp')!=[{'HostIp':'127.0.0.1','HostPort':'18083'}]:raise ValueError('Connect 서비스/localhost 포트 소유권 불일치')

def request(path, method="GET", data=None):
    # 복구·구성·probe의 공용 REST 변경 경로도 다른 namespace를 수정하지 않는다.
    if method!='GET':verify_worker_owner()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    req = urllib.request.Request("http://127.0.0.1:18083" + path,
        data=json.dumps(data).encode() if data is not None else None, method=method,
        headers={"Content-Type": "application/json"})
    with opener.open(req, timeout=10) as response:
        return json.loads(response.read() or b"null")


def wait_worker():
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            plugins = request("/connector-plugins")
            if any(plugin["class"] == "io.debezium.connector.mysql.MySqlConnector" for plugin in plugins):
                return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError("Connect worker readiness timeout")


def prepare():
    runtime.sync_workspace()
    runtime.setup()
    secret_dir = runtime.STATE / "secrets"
    reader = secret_dir / "cdc-reader.properties"
    if not reader.exists():
        reader.write_text("password=" + secrets.token_hex(32) + "\n")
        reader.chmod(0o640)
    writer = secret_dir / "mysql-probe-writer"
    if not writer.exists():
        writer.write_text(secrets.token_hex(32))
        writer.chmod(0o600)
    read_password = reader.read_text().strip().split("=", 1)[1]
    write_password = writer.read_text()
    # 생성한 16진수 값만 SQL에 넣고 비밀 값은 stdin으로 전달한다.
    if any(len(value) != 64 or any(c not in "0123456789abcdef" for c in value) for value in (read_password, write_password)):
        raise ValueError("Unexpected local credential format")
    sql = f"""
CREATE DATABASE IF NOT EXISTS sentinel_cdc_probe;
CREATE TABLE IF NOT EXISTS sentinel_cdc_probe.outbox_probe (
 event_id CHAR(36) PRIMARY KEY, run_id VARCHAR(64) NOT NULL, payload JSON NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6));
CREATE USER IF NOT EXISTS 'sentinel_cdc'@'%' IDENTIFIED BY '{read_password}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'sentinel_cdc'@'%';
GRANT SELECT, SHOW VIEW ON sentinel_cdc_probe.* TO 'sentinel_cdc'@'%';
CREATE USER IF NOT EXISTS 'sentinel_probe_writer'@'%' IDENTIFIED BY '{write_password}';
GRANT SELECT, INSERT ON sentinel_cdc_probe.* TO 'sentinel_probe_writer'@'%';
"""
    command = ["docker", "compose", "-p", runtime.PROJECT, "-f", str(runtime.WORKSPACE / "docker-compose.portfolio.yml"),
        "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$(cat /run/secrets/mysql-root)" mysql -u root']
    subprocess.run(command, input=sql, text=True, env=runtime.environment(), capture_output=True, check=True)


def ensure_mysql_configuration():
    query = 'MYSQL_PWD="$(cat /run/secrets/mysql-app)" mysql -u sentinel_local -Nse "SELECT CONCAT(@@server_id,CHAR(124),@@log_bin,CHAR(124),@@binlog_format,CHAR(124),@@binlog_row_image,CHAR(124),@@binlog_expire_logs_seconds)"'
    expected = "223344|1|ROW|FULL|864000"
    if runtime.compose(["exec", "-T", "mysql", "sh", "-c", query], True).stdout.strip() != expected:
        runtime.compose(["up", "-d", "--force-recreate", "--wait", "--wait-timeout", "120", "mysql"])
    if runtime.compose(["exec", "-T", "mysql", "sh", "-c", query], True).stdout.strip() != expected:
        raise RuntimeError("MySQL CDC configuration was not applied")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "up", "connect-stop", "connect-recreate", "broker-stop", "broker-start", "mysql-stop", "mysql-start"])
    args = parser.parse_args()
    if args.action in ("prepare", "up"):
        prepare()
    if args.action == "up":
        ensure_mysql_configuration()
        output = runtime.compose(["--profile", "cdc", "ps", "--format", "json"], True).stdout.replace(" ", "")
        if '"PublishedPort":18083' not in output:
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 18083))
        runtime.compose(["--profile", "cdc", "up", "-d", "--wait", "--wait-timeout", "180", "connect"])
        wait_worker()
    elif args.action == "connect-stop":
        runtime.compose(["--profile", "cdc", "stop", "connect"])
    elif args.action == "connect-recreate":
        runtime.compose(["--profile", "cdc", "up", "-d", "--force-recreate", "--no-deps", "connect"])
        wait_worker()
    elif args.action == "mysql-stop":
        runtime.compose(["stop", "mysql"])
    elif args.action == "mysql-start":
        # DB 복구는 broker의 복구 여부에 의존하지 않는다. 기존 volume을 유지한다.
        runtime.compose(["up", "-d", "--no-deps", "--wait", "--wait-timeout", "120", "mysql"])
    elif args.action == "broker-stop":
        runtime.compose(["stop", "kafka"])
    elif args.action == "broker-start":
        runtime.compose(["up", "-d", "--force-recreate", "--no-deps", "kafka"])
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                runtime.verify()
                break
            except (OSError, subprocess.CalledProcessError):
                time.sleep(1)
        else:
            raise RuntimeError("Broker recovery timeout")
    print(json.dumps({"action": args.action, "status": "OK"}))


if __name__ == "__main__":
    main()
