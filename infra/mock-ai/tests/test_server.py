import importlib.util
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

spec = importlib.util.spec_from_file_location("mock_ai", Path(__file__).parents[1] / "server.py")
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class MockAiServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.http = server.make_server("127.0.0.1", 0, Path(self.temp.name) / "journal.jsonl")
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.http.server_port}"

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join()
        self.temp.cleanup()

    def request(self, path, body=None):
        request = urllib.request.Request(self.base + path,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json"})
        try:
            response = urllib.request.urlopen(request, timeout=3)
        except urllib.error.HTTPError as response:
            error_response = response
            self.addCleanup(error_response.close)
            return error_response
        self.addCleanup(response.close)
        return response

    def test_health_and_seeded_valid_pdf(self):
        # given: 같은 seed와 입력은 같은 유효 PDF를 생성해야 한다.
        self.assertEqual(200, self.request("/health").status)
        self.assertEqual(200, self.request("/control", {"seed": "sample"}).status)
        with self.request("/test-ai/custom-reports", {"additionalInstructions": "private text"}) as response:
            self.assertEqual(200, response.status)
            self.assertEqual("application/pdf", response.headers.get_content_type())
            first = response.read()
        second = self.request("/test-ai/custom-reports", {"additionalInstructions": "private text"}).read()
        self.assertEqual(first, second)
        self.assertTrue(first.startswith(b"%PDF-1.4"))
        offset = int(first.split(b"startxref\n")[1].splitlines()[0])
        self.assertTrue(first[offset:].startswith(b"xref\n"))
        self.request("/control", {"seed": "other"})
        self.assertNotEqual(first, self.request("/test-ai/custom-reports", {"additionalInstructions": "private text"}).read())

    def test_failure_retry_and_journal_without_raw_input(self):
        # when: 최초 두 번만 오류를 주입하면 세 번째 요청은 정상으로 복귀한다.
        self.request("/control", {"seed": "retry", "fail_first": 2, "error_status": 503})
        statuses = [self.request("/test-ai/custom-reports", {"secret": "never-journal-this"}).status for _ in range(3)]
        self.assertEqual([503, 503, 200], statuses)
        journal = self.request("/journal").read()
        self.assertNotIn(b"never-journal-this", journal)
        entries = json.loads(journal)
        self.assertEqual([503, 503, 200], [e["status"] for e in entries])
        self.assertEqual([1, 2, 3], [e["sequence"] for e in entries])
        self.assertEqual(3, len(Path(self.temp.name, "journal.jsonl").read_text().splitlines()))

    def test_invalid_control_does_not_change_state(self):
        self.assertEqual(400, self.request("/control", {"delay_ms": -1}).status)
        self.assertEqual(400, self.request("/control", {"error_status": 200}).status)
        self.assertEqual(400, self.request("/control", {"fail_first": True}).status)
        self.assertEqual(200, self.request("/test-ai/custom-reports", {}).status)

    def test_legacy_pdf_route_and_delay(self):
        import time
        self.request("/control", {"delay_ms": 80})
        start = time.monotonic()
        response = self.request("/api/v1/analysis/005930/report")
        self.assertEqual(200, response.status)
        self.assertGreaterEqual(time.monotonic() - start, 0.075)
        self.assertTrue(response.read().startswith(b"%PDF"))
        self.assertEqual(404, self.request("/unknown").status)

    def test_custom_contract_records_observed_source_without_raw_instructions(self):
        import hashlib
        # given: 실제 값 전달을 별도로 재계산하며 입력 원문은 journal에 쓰지 않는다.
        self.assertEqual(200,self.request('/control/custom-reports',{'response_mode':'valid'}).status)
        cell={'key':{'stockCode':'000020','quarterKey':20253,'metricCode':'ROA','valueType':'ACTUAL'},'value':'1.2345','provenance':[]}
        checksum=hashlib.sha256(b'000020|20253|ROA|ACTUAL|1.2345\n').hexdigest()
        body={'schemaVersion':1,'requestId':'fixture-id','source':{'snapshotId':'snapshot-a','companyId':1,'targetQuarterKey':20253,'cells':[cell],'checksum':checksum},'input':{'additionalInstructions':'never-journal-custom-text','inputHash':'a'*64}}
        # when / then
        self.assertEqual(200,self.request('/test-ai/custom-reports',body).status)
        raw=self.request('/journal').read();entry=json.loads(raw)[0]
        self.assertEqual(checksum,entry['sourceChecksum']);self.assertEqual('snapshot-a',entry['sourceSnapshot']);self.assertNotIn(b'never-journal-custom-text',raw)

    def test_custom_failure_modes_and_source_mismatch_are_bounded(self):
        # given / when / then
        self.assertEqual(400,self.request('/control/custom-reports',{'response_mode':'unknown'}).status)
        self.assertEqual(400,self.request('/test-ai/custom-reports',{'schemaVersion':1,'source':{}}).status)

    def test_processed_custom_response_can_be_lost_once_and_journal_keeps_both_calls(self):
        import hashlib,http.client,urllib.error
        # given: 서버 처리는 완료하되 첫 응답만 전송하지 않는다.
        self.assertEqual(200,self.request('/control/custom-reports',{'drop_first':1}).status)
        cell={'key':{'stockCode':'000020','quarterKey':20253,'metricCode':'ROA','valueType':'ACTUAL'},'value':'1.2345','provenance':[]}
        checksum=hashlib.sha256(b'000020|20253|ROA|ACTUAL|1.2345\n').hexdigest()
        body={'schemaVersion':1,'requestId':'lost-response-owned','source':{'snapshotId':'snapshot-a','companyId':1,'targetQuarterKey':20253,'cells':[cell],'checksum':checksum},'input':{'additionalInstructions':'never-journal-lost-raw','inputHash':'a'*64}}
        # when / then
        with self.assertRaises((http.client.RemoteDisconnected,urllib.error.URLError)):
            self.request('/test-ai/custom-reports',body)
        self.assertEqual(200,self.request('/test-ai/custom-reports',body).status)
        journal=self.request('/journal').read();entries=json.loads(journal)
        self.assertEqual([True,False],[entry['responseDropped'] for entry in entries])
        self.assertEqual([200,200],[entry['status'] for entry in entries]);self.assertNotIn(b'never-journal-lost-raw',journal)
        self.assertEqual(400,self.request('/control/custom-reports',{'drop_first':-1}).status)

    def test_custom_response_gate_proves_received_call_before_response_without_raw_input(self):
        import hashlib,time
        # given: 소유 요청이 모의 서버에 진입했음을 응답 전 별도로 관찰한다.
        self.assertEqual(200,self.request('/control/custom-reports',{'hold_response':True}).status)
        cell={'key':{'stockCode':'000020','quarterKey':20253,'metricCode':'ROA','valueType':'ACTUAL'},'value':'1.2345','provenance':[]};checksum=hashlib.sha256(b'000020|20253|ROA|ACTUAL|1.2345\n').hexdigest()
        body={'schemaVersion':1,'requestId':'owned-gate','source':{'snapshotId':'snapshot-a','companyId':1,'targetQuarterKey':20253,'cells':[cell],'checksum':checksum},'input':{'additionalInstructions':'never-include-gate-raw','inputHash':'a'*64}};statuses=[]
        worker=threading.Thread(target=lambda:statuses.append(self.request('/test-ai/custom-reports',body).status));worker.start()
        try:
            # when
            end=time.monotonic()+2;inflight=[]
            while not inflight and time.monotonic()<end:inflight=json.loads(self.request('/inflight/custom-reports').read());time.sleep(.01)
            # then
            self.assertEqual(1,len(inflight));self.assertEqual('owned-gate',inflight[0]['requestId']);self.assertNotIn('never-include-gate-raw',json.dumps(inflight));self.assertTrue(worker.is_alive());self.assertEqual([],json.loads(self.request('/journal').read()))
        finally:
            self.request('/control/custom-reports',{'hold_response':False});worker.join(3)
        self.assertFalse(worker.is_alive());self.assertEqual([200],statuses);self.assertEqual([],json.loads(self.request('/inflight/custom-reports').read()));self.assertEqual(inflight[0]['sequence'],json.loads(self.request('/journal').read())[0]['sequence'])
        self.assertEqual(400,self.request('/control/custom-reports',{'hold_response':1}).status)


if __name__ == "__main__":
    unittest.main()
