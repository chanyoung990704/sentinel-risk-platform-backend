"""성공/실패와 준비 구간을 구분하는 헤더 기반 원자료 계산기."""
import csv
import json
import math
from pathlib import Path


def percentile(values, fraction):
    if not 0 < fraction <= 1:
        raise ValueError("percentile 범위 오류")
    if not values:
        return None
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * fraction) - 1]


def finite(value, name):
    result = float(value)
    if not math.isfinite(result):
        raise ValueError(f"유한한 {name} 필요")
    return result


def latency(values):
    return {"p50": percentile(values, .5), "p95": percentile(values, .95),
            "p99": percentile(values, .99), "mean": sum(values) / len(values) if values else None}


def summarize(run_dir):
    root = Path(run_dir)
    manifest = json.loads((root / "manifest.json").read_text())
    groups = {}; total_rows = 0; warmup = 0
    with (root / "requests.csv").open(newline="") as file:
        reader = csv.DictReader(file, strict=True)
        required = {"label", "phase", "start_ms", "elapsed_ms", "status", "expected_status", "success", "sql_count"}
        if not reader.fieldnames or not required.issubset(reader.fieldnames) or len(reader.fieldnames) != len(set(reader.fieldnames)):
            raise ValueError("CSV 헤더 누락/중복")
        for row in reader:
            total_rows += 1
            if None in row or any(row.get(name) is None for name in required):
                raise ValueError("CSV 열 수 불일치")
            if row["phase"] == "warmup":
                warmup += 1; continue
            if row["phase"] != "measure":
                raise ValueError("알 수 없는 측정 구간")
            start = finite(row["start_ms"], "시작 시각"); elapsed = finite(row["elapsed_ms"], "응답 시간")
            status = int(row["status"]); expected = int(row["expected_status"])
            if elapsed < 0 or row["success"] not in ("true", "false"):
                raise ValueError("응답 시간/성공 표시 오류")
            success = row["success"] == "true"
            if success and (not 200 <= status < 300 or status != expected):
                raise ValueError("실패 HTTP를 성공으로 표시")
            label = row["label"]
            if not label:
                raise ValueError("label 없음")
            group = groups.setdefault(label, dict(requests=0, successes=[], failures=[], starts=start,
                ends=start + elapsed, http_errors=0, unexpected=0, rejections=0, accepted=0, sql=[], hashes={}, hash_mismatches=0))
            group["requests"] += 1; group["starts"] = min(group["starts"], start); group["ends"] = max(group["ends"], start + elapsed)
            group["successes" if success else "failures"].append(elapsed)
            group["http_errors"] += not 200 <= status < 300
            group["unexpected"] += status != expected or (200 <= status < 300 and not success)
            group["rejections"] += status == expected and status in (429, 503)
            group["accepted"] += status == 202 and success
            if row["sql_count"] != "":
                count = int(row["sql_count"])
                if count < 0: raise ValueError("음수 SQL 수")
                group["sql"].append(count)
            if success and row.get("request_key") and row.get("response_hash"):
                previous = group["hashes"].setdefault(row["request_key"], row["response_hash"])
                group["hash_mismatches"] += previous != row["response_hash"]
    labels = {}
    for name, group in groups.items():
        duration = group["ends"] - group["starts"]
        if duration <= 0: raise ValueError("관찰 구간은 0보다 커야 함")
        count = group["requests"]; sql = group["sql"]
        labels[name] = dict(requests=count, successful=len(group["successes"]), failed=len(group["failures"]),
            durationMs=duration, successTps=len(group["successes"]) / (duration / 1000),
            successLatencyMs=latency(group["successes"]), failureLatencyMs=latency(group["failures"]),
            httpErrorPercent=100 * group["http_errors"] / count, unexpectedResponses=group["unexpected"],
            expectedRejections=group["rejections"], accepted202=group["accepted"], sqlSampleCount=len(sql),
            sqlCoveragePercent=100 * len(sql) / count, sqlPerRequest=sum(sql) / count if len(sql) == count else None,
            p99ReferenceOnly=len(group["successes"]) < 2000, responseHashes=group["hashes"],
            responseHashMismatches=group["hash_mismatches"])
    marker_path = root / "runner-finished.json"
    marker = json.loads(marker_path.read_text()) if marker_path.exists() else None
    complete = bool(labels) and bool(marker and marker.get("completed") is True and marker.get("requestRows") == total_rows)
    lifecycle = None
    if (root / "lifecycle.csv").exists():
        latest = {}; observations = 0
        with (root / "lifecycle.csv").open(newline="") as file:
            for row in csv.DictReader(file, strict=True):
                time = finite(row["timestamp_ms"], "lifecycle 시각")
                old = latest.get(row["request_id"])
                if old is None or old[0] <= time: latest[row["request_id"]] = (time, row["state"])
                observations += 1
        lifecycle = dict(succeeded=sum(state == "SUCCEEDED" for _, state in latest.values()),
            failed=sum(state == "FAILED" for _, state in latest.values()),
            nonterminal=sum(state not in ("SUCCEEDED", "FAILED") for _, state in latest.values()), observations=observations)
    return dict(schemaVersion=1, runId=manifest["runId"], mode=manifest["mode"], measurementComplete=complete,
                requestRows=total_rows, warmupRows=warmup, labels=labels, lifecycle=lifecycle,
                runtimeAvailable=(root / "runtime.csv").exists(), fixture=manifest.get("fixture", False))


def write_summary(run_dir):
    root = Path(run_dir); result = summarize(root)
    for filename in ("summary.json", "summary.md"):
        if (root / filename).exists(): raise FileExistsError("기존 요약 보존: " + filename)
    text = ["# 측정 요약", "", f"- 실행: {result['runId']}", f"- 측정 완료: {result['measurementComplete']}",
            f"- 준비 표본: {result['warmupRows']}", "", "| 대상 | 성공/전체 | P95(ms) | 성공TPS | HTTP오류(%) | SQL/요청 |", "| --- | --- | --- | --- | --- | --- |"]
    for label, values in result['labels'].items():
        show = lambda value: "미수집" if value is None else f"{value:.4f}"
        text.append(f"| {label} | {values['successful']}/{values['requests']} | {show(values['successLatencyMs']['p95'])} | {show(values['successTps'])} | {show(values['httpErrorPercent'])} | {show(values['sqlPerRequest'])} |")
    text += ["", "P99 성공표본2000개 미만은 참고값. 실패 응답과 202 접수는 업무 완료와 구분한다."]
    (root / "summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    (root / "summary.md").write_text("\n".join(text) + "\n")
    return result
