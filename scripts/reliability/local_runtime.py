"""WSL Ubuntu 전용 복원 환경 관리. 기존 Compose와 비밀 파일은 변경하지 않는다."""
import argparse
import base64
import hashlib
import json
import os
import re
import secrets
import shutil
import signal
import socket
import subprocess
import time
import urllib.request
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[2]
def namespace_config(name):
    if name not in ('portfolio','operations','restore'):
        raise ValueError('허용된 실행 namespace 확인 필요')
    root=Path.home()/'.local/share/sentinel'
    return dict(project='sentinel-'+name,state=root/name,
        workspace=root/'workspaces'/('e02-runtime' if name=='portfolio' else name+'-runtime'))

NAMESPACE = os.environ.get('SENTINEL_RUNTIME_NAMESPACE','portfolio')
_locations=namespace_config(NAMESPACE)
WORKSPACE = _locations['workspace']
STATE = _locations['state']
PROJECT = _locations['project']
PORTS = (13306, 16379, 19092, 18081)


def sync_workspace():
    if SOURCE == WORKSPACE:
        return
    WORKSPACE.mkdir(parents=True, exist_ok=True)
    roots = ("src", "gradle", "scripts/reliability", "scripts/perf", "perf/fixtures", "perf/jmeter", "infra")
    root_files = ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat", "docker-compose.portfolio.yml")
    files = [p for name in roots
        for p in (SOURCE / name).rglob("*") if p.is_file() and "__pycache__" not in p.parts]
    files += [SOURCE / name for name in root_files]
    current = {source.relative_to(SOURCE).as_posix() for source in files}
    ownership = WORKSPACE / ".portfolio-source-files.json"
    previous = json.loads(ownership.read_text()) if ownership.exists() else []
    if not isinstance(previous, list) or any(not isinstance(name, str) for name in previous):
        raise ValueError("Invalid managed source manifest")

    def managed_path(name):
        relative = Path(name)
        allowed = name in root_files or any(name.startswith(root + "/") for root in roots)
        if relative.is_absolute() or ".." in relative.parts or not allowed:
            raise ValueError("Invalid managed source path")
        candidate = WORKSPACE / relative
        candidate.resolve().relative_to(WORKSPACE.resolve())
        cursor = candidate
        while cursor != WORKSPACE:
            if cursor.is_symlink():
                raise ValueError("Symlink traversal in execution workspace")
            cursor = cursor.parent
        return candidate

    # 이전에 동기화한 입력만 제거하며 build/실행 상태 파일은 보존한다.
    stale = [managed_path(name) for name in set(previous) - current]
    for source in files:
        managed_path(source.relative_to(SOURCE).as_posix())
    for source in files:
        target = WORKSPACE / source.relative_to(SOURCE)
        if not target.exists() or hashlib.sha256(source.read_bytes()).digest() != hashlib.sha256(target.read_bytes()).digest():
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        if target.suffix == ".cnf":
            target.chmod(0o644)
    for target in stale:
        if target.is_file():
            target.unlink()
    temporary = WORKSPACE / ".portfolio-source-files.tmp"
    temporary.write_text(json.dumps(sorted(current), indent=2))
    temporary.replace(ownership)
    (WORKSPACE / "gradlew").chmod(0o755)


def setup():
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    secret_dir = STATE / "secrets"
    secret_dir.mkdir(exist_ok=True, mode=0o700)
    (secret_dir / "jwt").mkdir(exist_ok=True, mode=0o700)
    for name in ("mysql-root", "mysql-app"):
        path = secret_dir / name
        if not path.exists():
            path.write_text(secrets.token_hex(32))
            path.chmod(0o600)
    private = secret_dir / "jwt/private.pem"
    public = secret_dir / "jwt/public.pem"
    if not private.exists():
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        private.chmod(0o600)
    if not public.exists():
        subprocess.run(["openssl", "pkey", "-in", str(private), "-pubout", "-out", str(public)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        public.chmod(0o600)
    properties = secret_dir / "application-secrets.properties"
    if not properties.exists():
        pepper = base64.b64encode(secrets.token_bytes(32)).decode()
        properties.write_text("spring.datasource.password=" + (secret_dir / "mysql-app").read_text() +
            "\napp.security.token-hash.pepper-base64=" + pepper + "\n")
        properties.chmod(0o600)
    (STATE / "uploads").mkdir(exist_ok=True)
    journal = STATE / "mock-journal"
    journal.mkdir(exist_ok=True)
    # 컨테이너의 비특권 UID가 journal만 기록할 수 있게 한다.
    journal.chmod(0o777)


def environment():
    # OS 실행에 필요한 항목만 상속한다. 앱 설정/JVM 주입 옵션은 로컬 구성에 섞지 않는다.
    allowed = {"PATH", "HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "LC_CTYPE", "TMPDIR", "JAVA_HOME"}
    env = {name: value for name, value in os.environ.items() if name in allowed}
    env["DOCKER_HOST"] = "unix:///var/run/docker.sock"
    env['SENTINEL_RUNTIME_NAMESPACE']=NAMESPACE
    images = json.loads((WORKSPACE / "infra/portfolio-images.lock.json").read_text())
    if set(images) != {"mysql", "redis", "kafka", "python"} or any(
        not re.fullmatch(r"[a-z0-9/._-]+@sha256:[0-9a-f]{64}", value) for value in images.values()
    ):
        raise ValueError("Every runtime image must have an immutable SHA-256 digest")
    for name, ref in images.items():
        env["PORTFOLIO_" + name.upper() + "_IMAGE"] = ref
    cdc_lock = WORKSPACE / "infra/cdc/images.lock.json"
    if cdc_lock.exists():
        cdc_images = json.loads(cdc_lock.read_text())
        if set(cdc_images) != {"connect"} or not re.fullmatch(r"[a-z0-9/._-]+@sha256:[0-9a-f]{64}", cdc_images["connect"]):
            raise ValueError("CDC image must have an immutable SHA-256 digest")
        env["PORTFOLIO_CONNECT_IMAGE"] = cdc_images["connect"]
    env["PORTFOLIO_SECRET_GID"] = str(os.getgid())
    env.update(PORTFOLIO_SECRET_DIR=str(STATE / "secrets"), PORTFOLIO_JOURNAL_DIR=str(STATE / "mock-journal"),
        PORTFOLIO_DATA_DIR=str(STATE), SPRING_PROFILES_ACTIVE="local",
        SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
        SPRING_DATASOURCE_USERNAME="sentinel_local", SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.cj.jdbc.Driver",
        REDIS_HOST="127.0.0.1", REDIS_PORT="16379", SPRING_DATA_REDIS_HOST="127.0.0.1", SPRING_DATA_REDIS_PORT="16379",
        AI_SERVER_URL="http://127.0.0.1:18081", SPRING_KAFKA_BOOTSTRAP_SERVERS="127.0.0.1:19092",
        SERVER_ADDRESS="127.0.0.1", SERVER_PORT="18080", TURNSTILE_VERIFY_URL="http://127.0.0.1:18081/turnstile-disabled",
        DART_CORPSYNC_SCHEDULE_ENABLED="false", METRICAVERAGE_SCHEDULE_ENABLED="false")
    return env


def compose(args, capture=False):
    return subprocess.run(["docker", "compose", "-p", PROJECT, "-f", str(WORKSPACE / "docker-compose.portfolio.yml")] + args,
        env=environment(), check=True, capture_output=capture, text=True)


def application_environment():
    # 앱만 durable pipeline을 활성화한다. Gradle/관리 CLI의 환경에는 주입하지 않는다.
    env = environment()
    env.update(CUSTOM_REPORT_MESSAGING_ENABLED="true", CUSTOM_REPORT_WORKER_ENABLED="true",
        CUSTOM_REPORT_MESSAGING_AUTO_STARTUP="true")
    return env


def verify():
    # 비밀번호는 컨테이너 내부 파일에서만 읽으며 출력하거나 인수로 전달하지 않는다.
    sql = 'MYSQL_PWD="$(cat /run/secrets/mysql-app)" mysql -u sentinel_local -D sentinel_portfolio -Nse "SELECT 1"'
    if compose(["exec", "-T", "mysql", "sh", "-c", sql], True).stdout.strip() != "1":
        raise RuntimeError("MySQL probe failed")
    if compose(["exec", "-T", "redis", "redis-cli", "ping"], True).stdout.strip() != "PONG":
        raise RuntimeError("Redis probe failed")
    compose(["exec", "-T", "kafka", "/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "kafka:9092", "--list"], True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open("http://127.0.0.1:18081/health", timeout=3) as response:
        if json.load(response)["status"] != "UP":
            raise RuntimeError("Mock AI probe failed")
    return {"mysql": "UP", "redis": "UP", "kafka": "UP", "mockAi": "UP"}


def owned_process():
    pid_path = STATE / "app.pid"
    if not pid_path.exists():
        return None
    pid = int(pid_path.read_text())
    command = Path(f"/proc/{pid}/cmdline")
    if not command.exists():
        pid_path.unlink()
        return None
    if str(WORKSPACE / "build/libs").encode() not in command.read_bytes():
        raise RuntimeError("PID ownership mismatch; refusing to signal process")
    return pid


def stop_app():
    pid = owned_process()
    if pid is None:
        return
    os.kill(pid, signal.SIGTERM)
    for _ in range(300):
        status = Path(f"/proc/{pid}/stat")
        if not status.exists() or status.read_text().split()[2] == "Z":
            (STATE / "app.pid").unlink(missing_ok=True)
            return
        time.sleep(0.1)
    raise RuntimeError("Application did not stop within 30s; process retained for inspection")


def start_app():
    if owned_process() is not None:
        raise RuntimeError("Application is already running")
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 18080))
    subprocess.run(["bash", "./gradlew", "bootJar", "--no-daemon", "--console=plain"], cwd=WORKSPACE, env=environment(), check=True)
    jars = [p for p in (WORKSPACE / "build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
    if len(jars) != 1:
        raise RuntimeError("Expected exactly one bootJar")
    with (STATE / "app.log").open("a") as output:
        process = subprocess.Popen(["java", "-Xms128m", "-Xmx512m", "-jar", str(jars[0])], cwd=WORKSPACE,
            env=application_environment(), stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
    (STATE / "app.pid").write_text(str(process.pid))
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError("Application exited; inspect local app.log")
        try:
            # liveness는 외부 broker의 가용성과 분리한다.
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open("http://127.0.0.1:18080/actuator/health/liveness", timeout=2) as response:
                if json.load(response)["status"] == "UP":
                    return {"application": "UP", "pid": process.pid}
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError("Application readiness timeout; inspect local app.log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["setup", "up", "verify", "start-app", "stop-app", "down"])
    args = parser.parse_args()
    if os.name != "posix":
        raise SystemExit("Run inside WSL Ubuntu or use local.ps1")
    sync_workspace()
    setup()
    if args.action == "up":
        owned_ports = compose(["ps", "--format", "json"], True).stdout
        for port in PORTS:
            if f'"PublishedPort":{port}' in owned_ports.replace(" ", ""):
                continue
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", port))
        compose(["up", "-d", "--build", "--wait", "--wait-timeout", "180"])
        print(json.dumps(verify()))
    elif args.action == "verify":
        print(json.dumps(verify()))
    elif args.action == "start-app":
        print(json.dumps(start_app()))
    elif args.action == "stop-app":
        stop_app()
        print("Application stopped")
    elif args.action == "down":
        stop_app()
        compose(["--profile", "cdc", "down"])
    else:
        print("Local secret files prepared; no values printed")


if __name__ == "__main__":
    main()
