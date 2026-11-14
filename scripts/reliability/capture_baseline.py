"""비밀 파일을 제외한 소스 지문과 시험 분류 근거를 수집한다."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def fingerprint(root):
    root = Path(root)
    paths = [p for base in ("src", "gradle", "scripts/reliability", "scripts/perf", "perf/fixtures", "perf/jmeter") for p in (root / base).rglob("*") if p.is_file()
             and "__pycache__" not in p.parts and p.suffix != ".pyc"]
    paths += [root / name for name in ("build.gradle", "settings.gradle", "gradlew", "gradlew.bat")]
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(paths)}


def capture(root, compare):
    root = Path(root)
    hashes = fingerprint(root)
    other = fingerprint(compare)
    mismatches = sorted(k for k in hashes.keys() | other.keys() if hashes.get(k) != other.get(k))
    inventory = []
    for path in sorted((root / "src/test/java").rglob("*.java")):
        evidence = [{"line": i, "annotation": line.strip()} for i, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1)
                    if re.search(r"@(?:[\w]+\.)*(?:Tag|Disabled|Testcontainers|EnabledIf\w*|DisabledIf\w*)\b", line)]
        inventory.append({"source": path.relative_to(root).as_posix(), "annotations": evidence})
    return {"executionRoot": str(root.resolve()), "comparisonRoot": str(Path(compare).resolve()),
            "sourceSha256": hashes, "sourceMismatchCount": len(mismatches), "sourceMismatches": mismatches,
            "testSourceInventory": inventory,
            "inventoryLimit": "Static annotations including qualified names; nested, inherited and meta-annotations require runtime XML/manual review."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--compare", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = capture(args.root, args.compare)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"source files={len(result['sourceSha256'])}, mismatches={result['sourceMismatchCount']}")
    return 0 if not result["sourceMismatchCount"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
