# 수치 수집·성능 비교·장애 실험 프로토콜

연결 문서: [실행 계획](2026-11-03-execution-index.md), [공통 계약](2026-11-03-execution-contracts.md), [측정 구현 작업](2026-11-03-04-measurement.md).

**상태:** 아래 N·동시성·시간은 앞으로 수행할 실험의 입력/관찰 한도다. 실제 성능·복구 결과가 아니다. 측정 전 개선율이나 SLA를 약속하지 않는다.

## 1. 원자료 구조

`perf/results/<runId>/`에 덮어쓰기 없이 보관한다.

- manifest.json: code SHA+dirty-source fingerprint, OS/CPU/RAM/디스크, Java/Gradle/library·image digest, JVM/컨테이너 자원 제한, DB pool/index/SQL 로그·계측 설정, worker/lease/retry/cache/relay 설정, dataset/fixture hash, 부하 seed·시각, mock latency/error seed, 실제/모의 구성 요소, run mode.
- requests.csv: run/시나리오/label, 요청 sequence·멱등 키 hash, requestId, client start/end/elapsed, status, expectedResponse, success, attempt. 토큰·비밀번호·추가 요청 원문은 저장하지 않는다.
- lifecycle.csv: requestId/eventId/jobId, generation/attempt, DB ledger created/received/started/completed/failed timestamps, server stateVersion. clock source와 의미를 함께 기록한다.
- runtime.csv: 1초 간격 CPU/RSS/heap/GC/threads/DB pool active-pending/queries/Redis hit-miss/Kafka group lag/outbox age/job backlog. sampling 실패는 빈 값으로 표시하고 0으로 대체하지 않는다.
- faults.jsonl: 시험 입력, 주입 지점 barrier, 대상 process/container, fault/restart times, expected accepted set hash, 종료/재기동 command exit status, 종료 후 관측.
- reconciliation.json: 기대 수락 requestId 집합, 현재 상태별 건수, 누락/중복 artifact/외부 호출/고아 파일, import key/value checksum 및 설명된 차이.
- summary.json + summary.md: 계산식/표본 수·반복별 분포·제외 사유, before/after 상대/절대 변화, 합격 불변조건과 미완료/관찰 한도 초과.

application log의 상관 ID는 원자료와 연결하되 메트릭 label에는 requestId/userId/runId처럼 무한 증가하는 값을 넣지 않는다. runId 연결은 별도 파일·실험 메타데이터로 관리한다. 코드/입력/설정이 다르면 같은 비교군으로 묶지 않는다.

## 2. 지표 정의와 계산식

| 지표 | 정의/분모 | 수집 위치 |
| --- | --- | --- |
| API 접수 지연 | client POST 시작→202 또는 오류 응답 끝 | JMeter requests.csv |
| 전달·등록 지연 | request DB created_at→inbox/job registered_at | DB+consumer lifecycle. Outbox·relay·broker·소비 대기를 포함하며 순수 Kafka 전송 시간이라고 부르지 않음 |
| queue wait | 최초 job registered_at→최초 attempt started_at | DB journal |
| 생성 완료 지연 | request created_at→최종 committed completion 관측 | DB 최종 상태 timestamp/driver 검증. timestamp가 트랜잭션 내부 기록임을 명시 |
| 사용자 관측 완료 지연 | client 제출 시작→상태 polling에서 완료를 실제 확인 | driver. polling 간격 지연 포함, 위 DB 지표와 분리 |
| 중복 최종 결과 | requestId별 artifact count >1인 수 | DB unique constraint+SQL 대조 |
| 외부 중복 호출 | 동일 requestId의 mock journal call count-1 합계 | mock HTTP journal. DB 중복 결과와 별도 |
| 누락 요청 | 장애 전 커밋이 확인된 expected accepted ID 집합 minus 현재 원장/명시적 처리 기록 | 외부 driver의 장애 전 체크포인트와 종료 후 DB 대조 |
| 재시도/복구 | generation·attempt별 실행 수, 원인, due/start/완료; restore-ready→catch-up 시간 | DB·faults journal |
| 데이터 정합성 | 전체 expected canonical key/value와 staging/최종 DB의 missing/extra/different/null 차이 | source manifest+DB anti-join/full 대조 |
| 성공 처리량 | 성공한 요청 수 / 관찰 구간 초 | client raw samples. 구간 끝은 max(start+elapsed), 시작은 min(start) |
| 업무 완료 처리량 | 관찰 구간 내 SUCCEEDED job 수 / 관찰 구간 초 | DB lifecycle. 202 개수와 혼용하지 않음 |
| HTTP 오류율 | 예상 밖 HTTP/timeout 수 / 모든 본 측정 요청 수 | expected 429/503를 별도 집계하고 원래 전체 거부율도 공개 |
| P50/P95/P99 | 성공 요청 elapsed 정렬 후 nearest-rank ceil(p*N) | labels/run/repeat별; 실패 표본 분포와 수는 따로 보고 |
| cache 효과 | hit/(hit+miss), endpoint SQL/request, P95·성공 TPS·DB pool 대기 | 같은 계측 설정의 cache-off/on 비교 |

UTC를 사용하고 같은 호스트의 컨테이너/JVM clock 차이를 점검한다. 부하 실행 시간은 monotonic clock을 사용한다. 단위 시험용 MutableClock 데이터를 실제 지연 통계에 섞지 않는다. p99는 성공 표본이 2,000개 미만인 read 부하에서는 참고로 표시하고 percentile만으로 결론내리지 않는다.

before B, after A의 지연 감소율=`(B-A)/B*100`, 처리량 증가율=`(A-B)/B*100`. B=0이면 비율을 표시하지 않는다. 개선 전후 절대값·표본·오류율을 함께 제공한다. 3회 반복의 원자료와 반복별 값을 공개하고 중앙값을 대표값으로 사용한다. 유리한 1회만 선택하지 않는다.

## 3. 실험 프리셋

### READ: 실제 데이터 조회

- 대상은 M02에서 정한 한 기존 endpoint. 입력 기업 분포/권한/선택 snapshot/응답 항목 수/seed를 고정한다.
- client 동시성 1→10→50→100, 각 조건 warmup 30s 제외 후 측정 120s, 반복 3회. 오류/메모리/DB 대기 급증 시 높은 단계로 올리지 않고 중단 조건과 도달 부하를 기록한다.
- cache-off와 cache-on을 같은 앱/DB/worker 설정에서 A-B-B-A 순서로 배치해 시간 경향을 점검한다. cache-on의 cold/warm 결과는 분리한다. 초기 warmup/cache reset은 대상 namespace만 조작한다.
- 초기 인증·데이터 준비·상태 sampling SQL·health 조회는 대상 API elapsed/쿼리 수에서 제외하며 제외 규칙은 양쪽에 동일하다.
- SQL 개선 비교는 인덱스/쿼리 한 변수만 바꾸고 동일한 논리 응답 hash를 확인한다. H2는 성능 비교에 사용하지 않는다.

### REPORT: 새 맞춤 보고서 접수와 완료

- 사용자 20명, 서로 다른 Idempotency-Key, 대상 기업 분포와 요청 길이/seed 고정. 데이터 준비/토큰 발급은 측정 밖에서 수행한다.
- 일반 mock latency=200ms, worker=4. 외부 장기 지연 프로필은 2s로 별도 실행한다. 실제 AI 처리시간으로 해석하지 않는다.
- 고정 도착률 1→5→10→20 req/s, 각 warmup 30s + 측정 120s, 반복 3회. 실제 달성률과 schedule lag를 보고한다. client 부하 발생기가 목표 도착률을 못 맞추면 해당 조건을 포화 능력으로 주장하지 않는다.
- 정상 처리량 비교의 admission cap은 GLOBAL/USER=10,000으로 별도 고정해 의도하지 않은 quota 제한을 피한다. local 기본 quota 성능/오류는 별도 SATURATION 시험이다. 각 반복은 독립 DB fixture 또는 run scope 집합과 정리 절차로 격리한다.
- 접수 HTTP와 상태 조회 부하는 label을 분리한다. 본 측정 접수 완료 후 backlog가 해소될 때까지 별도 drain 구간을 기록하고 미완료를 표본에서 버리지 않는다.
- before/after는 같은 durable 구조에서 비교한다. 기존 동기 완료 HTTP와 새 202 접수를 ‘생성 속도 개선’으로 비교하지 않는다. 새 DB 상태 조회의 cache-off 기준선은 K03/R07 이후 M03에서 생성한다.

### IMPORT: 실제 파일 적재

- 현재 원본 hash와 base20253 고정. clean load, 같은 파일 replay, chunk 중단/resume를 각각 3회 수행한다. 기존 data/volume을 지우지 않고 전용 DB/시험 namespace만 초기화한다.
- preflight/stage/materialize/verify 시간과 총시간, rows·canonical cell/s, chunk 성공/재시도/재처리/skip, DB query·memory, 전체 checksum을 기록한다.
- 처리량보다 원본 대비 설명되지 않는 키/값 차이 0, 재시도 논리 중복 0을 우선한다. NULL 포함 위치 수와 숫자 값 수, 격리 행 수의 분모를 구분한다.

### SATURATION: 과부하와 동시성

- GLOBAL=5/USER=3, mock 응답을 barrier로 지연시킨 상태에서 고유 요청 100개를 동시에 보낸다. 수락한 요청을 삭제하지 않고 나머지 429/503를 정책대로 반환하는지 확인한다.
- 동일 키 100개 동시 제출은 별도 시험으로 요청/최초 Outbox/job/최종 artifact가 각각 1개인지 확인한다. quota 때문에 정상 멱등 재응답을 거부하지 않는지 확인한다.
- 상태 cache cold miss 100개 동시 요청에서 응답 동일성·재계산/DB query 증폭·DB pool 대기와 single-flight가 필요할 만큼의 효과를 검증한다. 효과 없으면 도입하지 않는다.

## 4. 장애 매트릭스: 기본값과 합격 조건

각 시나리오 seed별 3회 반복한다. 정상 복구가 예상되는 HTTP/프로세스 시험은 기본 100개 고유 수락 요청으로 수행하고, 특정 경계는 한 대상 요청을 latch/barrier로 정확히 멈춘다. 관찰 한도 기본 180s를 넘기면 미완료로 기록한다. 무작위 kill만으로 실패 지점을 증명하지 않는다.

| ID | 주입 지점 | 기대/측정 |
| --- | --- | --- |
| F01 | request+Outbox commit 직전/직후 API 종료 | 전은 둘 다 없음. 후는 응답 유실 재전송에도 같은 요청 복구 |
| F02 | 선택한 relay 중단 10s, API 재기동 | DB/한도 정상 시 접수·대기 보존, 재개 뒤 정상 작업 전부 완료 |
| F03 | broker 중단 10s 후 재기동 | accepted set 보존, delivery/job backlog 해소; 전송 실패/재개 지연 |
| F04 | Inbox commit 뒤 offset ACK 전 종료 | 실제 Kafka 재전달 관측, job/result 중복 0 |
| F05 | job READY 저장 직후 wakeup 유실 | recovery scan이 회수해 정상 완료 |
| F06 | worker 실행 중 강제 종료·lease 만료 | 새 시도로 복구, 이전 token의 늦은 완료 DB 반영 0 |
| F07 | 외부 서버 처리 후 응답 유실 | attempt 증가/외부 중복 호출 별도 집계, DB 결과 중복 0 |
| F08 | 영구 잘못된 PDF / 일시 5xx / timeout | 영구는 즉시 분류, 일시는 최초 포함 최대 3회; 예상 실패 건수와 일치 |
| F09 | 파일 저장 후 DB rollback·재시작 | 최종 artifact 하나, 고아 추적·유예 후 정리, live 파일 보존 |
| F10 | Redis 중단·상태 key TTL 만료·전체 시험 namespace 소실 | DB fallback과 복구 후 version 상태 재구성, 업무 원장 유실 0 |
| F11 | import 6번째 chunk commit 전/후 종료 | clean 대비 키/값 동일, 성공 chunk 중복/누락 0 |
| F12 | snapshot A 접수 뒤 B 활성화·재시도 | 실제 reader/mock 입력/결과의 source ID·checksum은 A 유지 |
| F13 | 자동 재시도 소진 후 원인 제거·관리 명령 반복 | generation 1 증가, 슬롯 1회 획득, 기존 실패 이력 유지 후 성공 |
| F14 | malformed payload 격리 후 DLT relay 중단 | 원문/격리/Outbox 보존, 복구 후 DLT 관측. 요청 건수와 메시지 격리 건수 혼합 금지 |
| F15 | polling ACK 뒤 delivery 기록 전 종료(해당 모드만) | 같은 event 재발행, 업무 결과 중복 0 |
| F16 | 사용자 B가 A의 요청·파일·legacy URL·cache key 조회 | 원문/파일 유출 0, 계약된 401/404, 다른 사용자 데이터 없음 |

누락 0만으로 정상 복구 통과로 판단하지 않는다. 정상 성공이 예상된 F02~F06 등은 해당 accepted set의 SUCCEEDED 수가 N과 같아야 한다. 영구 실패를 넣은 경우만 예상 FAILED 수를 허용하고, 관찰 한도 내 남은 nonterminal은 별도로 실패/미완료 처리한다. 격리 메시지는 요청 ledger의 최종 상태를 자동 대체하지 않는다. 서로 배타적인 업무 상태만 분모 N 대조에 사용한다.

## 5. 운영 절차와 복원 실험

- runbook 시험은 하나의 격리 환경에서 신규 접수 중단→worker 유예 종료→DB/파일의 정지 시점 checkpoint와 해당 Kafka/Connect 위치 기록→새 시험 namespace 복원을 수행한다.
- DB/file checksum과 snapshot pointer/요청 소유자·결과 연결을 확인한다. connector의 기존 위치를 무조건 재사용하지 않고 복구 지점/새 토픽·그룹·snapshot/replay 범위를 runbook에 따라 고정한다. destructive reset은 해당 fixture 이름/volume만 대상으로 한다.
- 이후 새 요청을 하나 처리해 읽기 복원만이 아니라 실행 재개도 확인한다. RPO는 checkpoint 시점까지이며 그 뒤 모든 쓰기까지 무손실이라고 주장하지 않는다.
- source outbox/inbox/격리 원문을 자동 삭제하지 않는 초기 실험 정책을 사용한다. 장기 보존 최적화·보존 구간 초과 자동 복구·다중 broker quorum은 조건부 확장으로 분리한다.

## 6. 결과 채택/기각 기준

- G1/G2 불변조건이 먼저 통과해야 성능 실험을 채택한다. 시험을 실행하지 못했거나 skip된 경우는 검증된 0건/0오류로 표시하지 않는다.
- 캐시는 같은 응답/오류 의미를 유지하고 DB query 또는 tail latency 개선이 반복별 변동과 함께 확인될 때 채택한다. 개선이 없거나 장애 시 과부하/정합성 비용이 크면 기각하고 SQL 개선만 남길 수 있다.
- 실험 수치는 raw CSV/journal에서 재계산 가능해야 한다. 목표 도착률·성공 HTTP TPS·업무 완료 TPS·클라이언트 관측 완료 시간을 섞지 않는다.
- 포트폴리오는 원본 수치·기간·본인 구현 범위·로컬/모의 환경·실제 운영 미경험을 사실대로 제시한다. 채용 성과/실서비스 트래픽과 연결한 검증 없는 주장은 만들지 않는다.
