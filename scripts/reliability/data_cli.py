"""Windows 편집본을 동기화한 뒤 Ubuntu의 별도 JVM으로 적재 명령을 실행한다."""
import os
import re
import subprocess
import sys
from pathlib import Path
import local_runtime as runtime


def linux_path(value):
    if re.match(r"^[A-Za-z]:[\\/]", value):
        return subprocess.run(["wslpath", "-u", value], check=True, capture_output=True, text=True).stdout.strip()
    return value


def main():
    if os.name != "posix":
        raise SystemExit("Ubuntu에서 실행하거나 PowerShell wrapper를 사용하세요")
    args = sys.argv[1:]
    if not args or args[0] not in ("import", "resume", "verify"):
        raise SystemExit("import/resume/verify 명령이 필요합니다")
    if args[0] == "import" and len(args) == 5:
        args[1] = linux_path(args[1]); args[2] = linux_path(args[2])
    elif args[0] in ("resume", "verify") and len(args) in (3, 4):
        args[2] = linux_path(args[2])
        if len(args) == 4: args[3] = linux_path(args[3])
    else:
        raise SystemExit("명령 인수 개수가 맞지 않습니다")
    runtime.sync_workspace()
    subprocess.run(["bash", "./gradlew", "prepareImportCli", "--console=plain", "--no-daemon"],
                   cwd=runtime.WORKSPACE, env=runtime.environment(), check=True)
    classpath = (runtime.WORKSPACE / "build/portfolio-import-classpath.txt").read_text().strip()
    os.chdir(runtime.WORKSPACE)
    os.execvpe("java", ["java", "-Xms128m", "-Xmx1g", "-cp", classpath,
                       "com.aivle.project.finance.internal.dataimport.cli.ImportCommand"] + args, runtime.environment())


if __name__ == "__main__":
    main()
