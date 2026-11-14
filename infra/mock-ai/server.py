"""외부 통신 없이 결정적인 PDF와 오류/지연을 제공하는 로컬 시험 서버."""
import argparse
import hashlib
import json
import re
import threading
import time
from decimal import Decimal, ROUND_HALF_UP
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


def pdf_bytes(seed, input_hash):
    text = f"SENTINEL local fixture {hashlib.sha256((seed + input_hash).encode()).hexdigest()}"
    stream = f"BT /F1 10 Tf 30 100 Td ({text}) Tj ET".encode("ascii")
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 640 160] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream"]
    result = b"%PDF-1.4\n"
    offsets = [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(result))
        result += f"{index} 0 obj\n".encode() + obj + b"\nendobj\n"
    xref = len(result)
    result += f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode()
    result += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    return result + f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()


def make_server(host, port, journal_path):
    journal_path = Path(journal_path)
    journal_path.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()
    config = {"seed": "e02", "delay_ms": 0, "fail_first": 0, "error_status": 503}
    counter = 0
    custom = {"seed": "r03", "delay_ms": 0, "fail_first": 0, "error_status": 503, "response_mode": "valid", "hold_response": False, "drop_first": 0}
    custom_counter = 0
    response_gate = threading.Event()
    response_gate.set()
    inflight = {}
    @contextmanager
    def active_call(number, observed, is_custom):
        if is_custom:
            with lock:inflight[number]={"requestId":observed["requestId"],"sequence":number}
        try:yield
        finally:
            if is_custom:
                with lock:inflight.pop(number,None)
    # 재시작 후에도 호출 순번과 journal을 유지한다.
    entries = [json.loads(line) for line in journal_path.read_text().splitlines()] if journal_path.exists() else []
    sequence = max((entry["sequence"] for entry in entries), default=0)

    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, data, content_type="application/json"):
            payload = json.dumps(data).encode() if content_type == "application/json" else data
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            try:
                self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass  # timeout 시험에서 클라이언트가 먼저 연결을 종료할 수 있다.

        def body(self):
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 <= length <= 65536:
                raise ValueError("body too large")
            value = json.loads(self.rfile.read(length) or b"{}", parse_float=Decimal)
            if not isinstance(value, dict):
                raise ValueError("object required")
            return value

        def do_GET(self):
            path = urlsplit(self.path).path
            if path == "/health":
                return self.respond(200, {"status": "UP", "mode": "local-fixture"})
            if path == "/journal":
                with lock:
                    snapshot = list(entries)
                return self.respond(200, snapshot)
            if path == "/inflight/custom-reports":
                with lock:snapshot=list(inflight.values())
                return self.respond(200,snapshot)
            if re.fullmatch(r"/api/v1/analysis/[0-9A-Za-z_-]+/report", path):
                return self.generate({"path": path})
            return self.respond(404, {"error": "unknown route"})

        def do_POST(self):
            nonlocal counter, custom_counter
            try:
                body = self.body()
                if self.path == "/control/custom-reports":
                    candidate = {"seed":"r03","delay_ms":0,"fail_first":0,"error_status":503,"response_mode":"valid","hold_response":False,"drop_first":0} | body
                    if set(candidate) != set(custom) or not isinstance(candidate["seed"],str) or not 1 <= len(candidate["seed"]) <= 128:
                        raise ValueError("custom control fields")
                    for key, maximum in (("delay_ms",10000),("fail_first",1000)):
                        if type(candidate[key]) is not int or not 0 <= candidate[key] <= maximum:raise ValueError(key)
                    if candidate["error_status"] not in (429,500,503) or type(candidate["error_status"]) is not int:raise ValueError("status")
                    if candidate["response_mode"] not in ("valid","not_pdf","wrong_type","empty","truncated","oversize"):raise ValueError("mode")
                    if type(candidate["hold_response"]) is not bool:raise ValueError("gate")
                    if type(candidate['drop_first']) is not int or not 0<=candidate['drop_first']<=1000:raise ValueError('drop')
                    with lock:
                        custom.update(candidate);custom_counter=0
                        if candidate["hold_response"]:response_gate.clear()
                        else:response_gate.set()
                    return self.respond(200,candidate)
                if self.path == "/control":
                    candidate = {"seed": "e02", "delay_ms": 0, "fail_first": 0, "error_status": 503} | body
                    if set(candidate) != set(config):
                        raise ValueError("unknown control field")
                    if not isinstance(candidate["seed"], str) or not 1 <= len(candidate["seed"]) <= 128:
                        raise ValueError("seed length")
                    for key, maximum in (("delay_ms", 10000), ("fail_first", 1000)):
                        if type(candidate[key]) is not int or not 0 <= candidate[key] <= maximum:
                            raise ValueError(key)
                    if type(candidate["error_status"]) is not int or candidate["error_status"] not in (429, 500, 503):
                        raise ValueError("error_status")
                    with lock:
                        config.update(candidate)
                        counter = 0
                    return self.respond(200, candidate)
                if self.path == "/test-ai/custom-reports":
                    return self.generate(body)
                return self.respond(404, {"error": "unknown route"})
            except (ValueError, KeyError, TypeError, ArithmeticError):
                return self.respond(400, {"error": "invalid request"})

        def generate(self, body):
            nonlocal counter, sequence, custom_counter
            start = time.monotonic()
            observed = {}
            is_custom = "schemaVersion" in body or "source" in body
            if is_custom:
                source=body["source"];user_input=body["input"];cells=source["cells"]
                if body["schemaVersion"]!=1 or not isinstance(body["requestId"],str) or not body["requestId"] or not 1 <= len(cells) <= 72:
                    raise ValueError("contract")
                if not isinstance(user_input["additionalInstructions"],str) or not 1 <= len(user_input["additionalInstructions"]) <= 2000 or not re.fullmatch(r"[0-9a-f]{64}",user_input["inputHash"]):raise ValueError("input")
                canonical=[]
                for cell in sorted(cells,key=lambda c:(c["key"]["stockCode"],c["key"]["quarterKey"],c["key"]["metricCode"],c["key"]["valueType"])):
                    key=cell["key"];value=cell["value"]
                    if key["valueType"]!="ACTUAL":raise ValueError("actual only")
                    formatted="NULL" if value is None else format(Decimal(str(value)).quantize(Decimal("0.0001"),rounding=ROUND_HALF_UP),"f")
                    canonical.append(f'{key["stockCode"]}|{key["quarterKey"]}|{key["metricCode"]}|{key["valueType"]}|{formatted}\n')
                checksum=hashlib.sha256("".join(canonical).encode()).hexdigest()
                if checksum!=source["checksum"]:raise ValueError("source checksum")
                observed={"requestId":body["requestId"],"sourceSnapshot":source["snapshotId"],"sourceChecksum":checksum,"inputHash":user_input["inputHash"]}
            digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=False,default=str).encode()).hexdigest()
            with lock:
                if is_custom:custom_counter+=1;current=dict(custom);should_fail=custom_counter<=current["fail_first"];drop_response=custom_counter<=current['drop_first']
                else:counter+=1;current=dict(config);should_fail=counter<=current["fail_first"]
                sequence += 1
                number = sequence
            with active_call(number,observed,is_custom):
                # 모의 시험에서만 응답을 최대 10초 보류한다. 원문은 관찰 hook에 넣지 않는다.
                if is_custom and current["hold_response"] and not response_gate.wait(10):should_fail=True
                time.sleep(current["delay_ms"] / 1000)
                status = current["error_status"] if should_fail else 200
                payload = {"error": "injected failure"} if should_fail else pdf_bytes(current["seed"], digest)
                content_type="application/json" if should_fail else "application/pdf"
                if is_custom and not should_fail:
                    mode=current["response_mode"]
                    if mode=="not_pdf":payload=b"not a PDF"
                    elif mode=="wrong_type":content_type="text/plain"
                    elif mode=="empty":payload=b""
                    elif mode=="truncated":payload=payload[:-20]
                    elif mode=="oversize":payload=b"%PDF-"+b"x"*(10*1024*1024+1)
                record = {"sequence": number, "inputSha256": digest, "status": status,
                    "seed": current["seed"], "elapsedMs": round((time.monotonic() - start) * 1000, 3)} | observed
                if is_custom:record['responseDropped']=drop_response
                # 응답 전에 append를 완료해 응답을 받은 호출은 journal에 존재하도록 한다.
                with lock:
                    entries.append(record)
                    with journal_path.open("a") as output:
                        output.write(json.dumps(record) + "\n")
                        output.flush()
                if is_custom and drop_response:
                    self.close_connection=True
                    return
                self.respond(status, payload, content_type)


        def log_message(self, *_):
            pass

    return ThreadingHTTPServer((host, port), Handler)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--journal", type=Path, default=Path("/data/journal.jsonl"))
    args = parser.parse_args()
    make_server(args.host, args.port, args.journal).serve_forever()
