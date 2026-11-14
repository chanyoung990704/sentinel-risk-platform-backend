"""CDC 실험 증거가 모두 충족된 경우에만 발행기 선택을 기록한다."""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path


def decide(summary, observations):
    reasons = []
    totals = summary.get("totals", {})
    if summary.get("status") != "PASS" or totals.get("tests", 0) < 2 or totals.get("passed") != totals.get("tests") or any(
        totals.get(key, 1) != 0 for key in ("failures", "errors", "skipped")
    ):
        reasons.append("Required CDC tests did not execute and pass without skips")
    for key in ("completed", "snapshotReceived", "connectorResume", "brokerResume", "offsetsPreserved", "historyPreserved", "internalTopicsCompacted", "eventIdsPreserved"):
        if observations.get(key) is not True:
            reasons.append("Missing or failed witness: " + key)
    for key, expected in (("committedExpected", 21), ("committedObserved", 21), ("rollbackExpected", 10), ("rollbackObserved", 0), ("observedUniqueCount", 21), ("databaseCommittedRows", 21)):
        if type(observations.get(key)) is not int or observations[key] != expected:
            reasons.append("Unexpected event count: " + key)
    return {"relayMode": "undecided" if reasons else "cdc", "rejectedReasons": reasons}


def main():
    import local_runtime as runtime
    import verify_test_results as verifier
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", args.run_id):
        parser.error("run-id must be 1..64 ASCII letters/digits/underscore/hyphen")
    runtime.sync_workspace()
    output = runtime.SOURCE / "perf/results" / args.run_id
    output.mkdir(parents=True, exist_ok=True)
    if (output / "cdc-decision.json").exists() or (output / "observations.json").exists():
        raise RuntimeError("Run evidence already exists; use a new run-id")
    commands = []
    def run(name, command, env=None):
        start = time.time()
        with (output / (name + ".log")).open("w") as stream:
            process = subprocess.run(command, cwd=runtime.WORKSPACE, env=env, stdout=stream, stderr=subprocess.STDOUT)
        commands.append({"name": name, "command": command, "startedAtEpoch": start, "endedAtEpoch": time.time(), "exitCode": process.returncode})
        (output / "commands.json").write_text(json.dumps(commands, indent=2))
        return process.returncode

    preparation = run("worker-up", ["python3", "scripts/reliability/cdc_runtime.py", "up"], runtime.environment())
    summary, observations = {}, {}
    test_exit = None
    if preparation == 0:
        env = runtime.environment() | {"PORTFOLIO_CDC_RUN_ID": args.run_id, "PORTFOLIO_CDC_RUN_DIR": str(output)}
        started = time.time()
        test_exit = run("cdc-test", ["bash", "./gradlew", "cdcTest", "--tests", "*CdcProbeIntegrationTest", "--no-daemon", "--console=plain"], env)
        summary = verifier.verify(runtime.WORKSPACE / "build/test-results", ["cdcTest"], since=started, deny_skips=True)
        (output / "test-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2))
        if (output / "observations.json").exists():
            observations = json.loads((output / "observations.json").read_text())
    decision = decide(summary, observations)
    if preparation != 0 or test_exit != 0:
        decision["relayMode"] = "undecided"
        decision["rejectedReasons"].append("Worker setup or Gradle command failed; polling is not automatically selected")
    decision.update(runId=args.run_id, tests=summary, observations=observations,
        lockedImages=json.loads((runtime.WORKSPACE / "infra/cdc/images.lock.json").read_text()),
        prerequisites={"serverId": 223344, "rowImage": "FULL", "format": "ROW", "binlogRetentionSeconds": 864000,
                       "verification": "mysqlPrerequisites JUnit assertion; valid only when complete test result passes"},
        scope="Probe only; business Outbox/Inbox/recovery not yet implemented")
    target = runtime.STATE / "relay-mode.json"
    existing_mode = json.loads(target.read_text()).get("relayMode") if target.exists() else None
    decision["previousRelayMode"] = existing_mode
    if decision["relayMode"] == "cdc" and existing_mode not in (None, "cdc"):
        decision["relayMode"] = "undecided"
        decision["rejectedReasons"].append("Existing relay selection differs; explicit migration decision required")
    if decision["relayMode"] == "cdc":
        temporary = target.with_suffix(".tmp")
        temporary.write_text(json.dumps({"relayMode": "cdc", "runId": args.run_id, "evidence": str(output / "cdc-decision.json")}, indent=2))
        temporary.replace(target)
    (output / "cdc-decision.json").write_text(json.dumps(decision, ensure_ascii=False, indent=2))
    print(json.dumps({"relayMode": decision["relayMode"], "rejectedReasons": decision["rejectedReasons"], "evidence": str(output)}))
    return 0 if decision["relayMode"] == "cdc" else 1


if __name__ == "__main__":
    raise SystemExit(main())
