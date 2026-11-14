"""Gradle JUnit XML 검증. 누락·빈 결과·오래된 결과는 성공으로 간주하지 않는다."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

KEYS = ("tests", "passed", "failures", "errors", "skipped")


def verify(root, tasks, since=None, deny_skips=False):
    summaries = []
    for task in tasks:
        counts = dict.fromkeys(KEYS, 0)
        summary = {"task": task, "counts": counts, "suites": [], "skips": [], "problems": []}
        files = sorted((Path(root) / task).glob("TEST-*.xml"))
        if not files:
            summary["problems"].append("missing or empty report directory")
        for path in files:
            try:
                if since is not None and path.stat().st_mtime < since:
                    raise ValueError("stale report")
                suite = ET.parse(path).getroot()
                if suite.tag != "testsuite":
                    raise ValueError("expected Gradle testsuite root")
                actual = dict.fromkeys(KEYS, 0)
                for case in suite.findall("testcase"):
                    actual["tests"] += 1
                    statuses = [key for key, tag in (("failures", "failure"), ("errors", "error"), ("skipped", "skipped")) if case.find(tag) is not None]
                    if len(statuses) > 1:
                        raise ValueError("conflicting testcase states")
                    state = statuses[0] if statuses else "passed"
                    actual[state] += 1
                    if state == "skipped":
                        node = case.find("skipped")
                        summary["skips"].append({"suite": suite.get("name"), "test": case.get("name"),
                            "reason": node.get("message") or (node.text or "").strip() or "not supplied by JUnit XML; consult source inventory"})
                for key in ("tests", "failures", "errors", "skipped"):
                    if int(suite.get(key, "0")) != actual[key]:
                        raise ValueError("suite counts disagree with testcase states: " + key)
                for key in KEYS:
                    counts[key] += actual[key]
                summary["suites"].append({"name": suite.get("name"), "file": str(path), "counts": actual})
            except (OSError, ET.ParseError, ValueError) as exc:
                summary["problems"].append(f"{path.name}: {exc}")
        if counts["tests"] - counts["skipped"] == 0:
            summary["problems"].append("no executed tests")
        if deny_skips and counts["skipped"]:
            summary["problems"].append("skipped tests forbidden by verification gate")
        summaries.append(summary)
    totals = {key: sum(s["counts"][key] for s in summaries) for key in KEYS}
    status = "PASS_WITH_SKIPS" if totals["skipped"] else "PASS"
    if not summaries or any(s["problems"] for s in summaries):
        status = "INCOMPLETE"
    if totals["failures"] or totals["errors"]:
        status = "FAIL"
    return {"status": status, "totals": totals, "tasks": summaries}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-root", type=Path, default=Path("build/test-results"))
    parser.add_argument("--require", action="append", required=True, dest="tasks")
    parser.add_argument("--since", type=float, help="실행 시작 시각 Unix seconds")
    parser.add_argument("--deny-skips", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(args.results_root, args.tasks, args.since, args.deny_skips)
    data = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(data + "\n", encoding="utf-8")
    print(data)
    return 0 if result["status"].startswith("PASS") else (1 if result["status"] == "FAIL" else 2)


if __name__ == "__main__":
    raise SystemExit(main())
