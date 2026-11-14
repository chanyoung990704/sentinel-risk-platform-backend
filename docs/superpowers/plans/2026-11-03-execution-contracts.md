# 공통 실행 계약: 스키마·인터페이스·시험 기본값

상위 [실행 계획](2026-11-03-execution-index.md)의 공통 결정이다. 이 값들은 구현/시험을 시작하기 위한 제안 기본값이며 측정 결과나 운영 SLA가 아니다. tuning 시 run manifest에 변경값을 기록한다.

## 경로와 실행 task

- `J`=`B/src/main/java/com/aivle/project`, `T`=`B/src/test/java/com/aivle/project`, `RES`=`B/src/main/resources`, `TR`=`B/src/test/resources`.
- E01에서 만드는 Gradle task: `fastTest`(외부 구성 요소 없는 회귀), `realInfraTest`(실제 MySQL/Redis/Kafka 기능), `cdcTest`(선택한 커넥터 포함), `reliabilityTest`(프로세스/구성 요소 장애). 모두 기존 src/test를 사용하고 결과는 `build/test-results/<task>/`로 분리한다.
- 새 시험의 태그는 `realinfra`, `cdc`, `reliability`다. fastTest는 이 태그와 기존 `integration` 태그를 제외하며 Kafka admin/listener를 비활성화한다. realInfraTest는 `realinfra` 또는 기존 `integration`을 포함하되 `cdc/reliability`는 제외한다. 다른 task는 해당 태그만 포함한다. 시험마다 실제 DB 종류/구성 요소를 결과 manifest에 기록한다. 태그만으로 MySQL 사용을 주장하지 않는다.
- 새 Test task는 기존과 동일한 testClassesDirs/runtimeClasspath·Mockito agent·시험 보안 설정을 공유하고 fork 수/메모리 설정을 manifest에 기록한다. `T/support/PortfolioIntegrationSupport.java`의 DynamicPropertySource가 실제 MySQL URL/user/password뿐 아니라 driver와 Flyway locations까지 MySQL migration으로 덮는지 확인한다. Kafka/Redis/모의 HTTP도 fixture 주소로 고정한다. H2 profile 이름만 보고 실제 MySQL로 실행됐다고 간주하지 않는다.
- 기존 `test`와 비활성화 이유는 보존한다. `verifiedCheck`는 fast/realinfra/reliability 및 CDC 채택 시 cdcTest를 실행하고 누락/skip을 요약한다. 폴링 채택 시 cdcTest는 명시적 N/A로 표기한다.
- 모든 새 시험에서 내부 HTTP는 loopback/시험 컨테이너만 사용하고 실제 외부 AI·SMTP·DART를 호출하지 않는다.

## API와 입력 계약

- `POST /api/companies/{companyId}/custom-report-requests`: JWT 인증, `Idempotency-Key` 헤더 필수. body=`targetQuarterKey`(생략 시 선택한 snapshot의 baseQuarterKey로 고정; 새 API는 해당 분기 분석 보고서 기준이며 기존 일반 보고서의 분기 기본값은 변경하지 않음), `additionalInstructions`(1~2,000 Unicode code point, CRLF→LF 후 바깥 공백 제거). 회사 권한/존재를 확인한다. snapshot은 서버가 현재 활성 VERIFIED snapshot에서 선택한다.
- key는 ASCII `[A-Za-z0-9._:-]{8,128}`. 키 원문 대신 SHA-256을 저장하고 `(user_id,idempotency_key_hash)`를 유일하게 만든다. body hash는 정규화한 클라이언트 입력의 canonical JSON으로 계산한다. 최초 해석된 분기/snapshot은 별도로 고정하고 재전송 때 현재 활성 버전으로 다시 해석하지 않는다.
- 최초 및 같은 키/같은 입력의 재요청: 202와 같은 requestId/상태 조회 URL. 같은 키/다른 입력 409, 잘못된 입력 400, 비인증 401, 다른 사용자 요청 조회 404, 개별 quota 429, DB/전체 수용량 부족 503. 새 요청으로 다른 내용을 보내려면 새 키를 사용한다.
- `GET /api/report-requests/{requestId}`: 소유자 확인 후 상태·stateVersion·attempt·lastErrorCode·retryAt·완료 시 결과 URL 반환. 내부 경로/stack trace는 반환하지 않는다.
- `GET /api/report-requests/{requestId}/file`: 소유자와 SUCCEEDED 확인 후 PDF 스트리밍. 미완료 409, 타인/미존재 404, 인증 없음 401. `Cache-Control: private, no-store`.
- 기존 일반 보고서 API는 유지한다. 맞춤 산출물을 기존 `company_report_versions.pdf_file_id`에 연결하지 않는다. `report_artifacts`로 요청과 files row를 연결해 기업/분기의 공용 최신 PDF 경로에서 노출되지 않게 한다. 기존 `/api/files/{id}`와 `/download-url`에서도 맞춤 파일이 우회 노출되지 않는지 시험한다. 현재 일반 파일 서비스의 게시글 매핑 검사는 보존한다.
- CORS allowed headers에 `Idempotency-Key`, `X-Run-Id`를 추가한다. X-Run-Id는 계측 프로필에서만 허용 목록의 run 식별에 사용하고 인증을 대신하지 않는다.

## 스키마 변경 순서와 고유 제약

| migration | 핵심 테이블/변경 | 반드시 필요한 제약 |
| --- | --- | --- |
| V28__create_import_journal | data_snapshots, data_snapshot_sources, import_runs, import_chunks, import_stage_values, import_errors | fingerprint+parser/정밀도 정책의 논리 데이터셋 키, run/chunk 번호, snapshot/종목/분기/지표/valueType 고유 키 |
| V29__scope_report_versions_to_snapshot | company_report_versions.data_snapshot_id(nullable), snapshot_report_versions, active_data_snapshots | snapshot/기업/분기당 한 reportVersion; family당 active pointer 하나 |
| V30__scope_metric_averages_to_snapshot | metric_averages.data_snapshot_id(nullable) | 현재 snapshot과 출처가 같은 평균만 조회, 이전 늦은 집계는 숨김 |
| V31__portfolio_read_indexes | M02에서 인덱스 개선을 선택했을 때만 추가 | 미사용이면 파일을 만들지 않고 번호는 이후 재사용하지 않음 |
| V32__create_custom_report_requests_and_outbox | report_requests, report_admission, outbox_events | user/keyHash; UUID requestId/eventId; admission scopeKey; outbox payload 불변 |
| V33__create_report_jobs_and_artifacts | inbox_events, report_jobs, report_attempts, report_artifacts, report_recovery_commands | consumer/eventId; requestId당 job/최종 artifact 하나; job/generation/attempt; recoveryCommandId |
| V34__create_polling_outbox_delivery | outbox_delivery(폴링 채택 시만) | eventId당 delivery; claim token/lease/version |
| V35__create_message_quarantine | quarantined_messages | source topic/partition/offset, quarantine id, original event/request 참조(검증된 경우만) |

MySQL/H2 migration을 각각 추가한다. UUID는 Java UUID와 DB CHAR(36), 사용자/회사/분기/files는 현재 Long PK를 따른다. fingerprint와 hash는 CHAR(64) ASCII binary 비교로 고정한다. 시간은 DB·JVM·JSON의 UTC로 통일하고 화면만 KST로 표시한다.

### 공통 타입의 소유 작업

- R01: `company/reportrequest/model/CustomReportCommand(Integer targetQuarterKey,String additionalInstructions)`, `AcceptedReportRequest(UUID requestId,ReportRequestStatus status,String statusUrl)`, `ReportRequestStatus`; `messaging/outbox/ReportEvent`와 `ReportEventType`(REQUEST_ACCEPTED, STATUS_CHANGED, MESSAGE_QUARANTINED). wire payload는 schemaVersion=1의 JSON object이며 request/company/quarter/snapshot/input hash를 명시적으로 매핑한다.
- R02: `company/reportjob/model/JobLease(UUID jobId,UUID requestId,UUID leaseToken,long version,int generation,int attemptNo,Instant leaseUntil,UUID attemptId)`, `messaging/inbox/InboxAcceptance`(ACCEPTED,DUPLICATE). jobId/attemptId는 UUID CHAR(36)이며 시도 파일 key도 lease에 고정한다.
- R03: `ReportSourceData(UUID snapshotId,Long companyId,int targetQuarterKey,List<CanonicalMetricCell> cells,String checksum)`, `CustomReportInput(String additionalInstructions,String inputHash)`, `GeneratedReport(byte[] bytes,String sha256)`를 `company/reportjob/model/`에 둔다. source cells는 snapshot 내 대상 분기까지 존재하는 최대 4분기의 ACTUAL이며 명시한 분기에 실제값이 전혀 없으면 400으로 접수를 거부한다.
- R04: `company/reportjob/port/ReportResultPort.storeAndComplete(JobLease,GeneratedReport)->CompletionDecision`와 `CompletionDecision`(COMPLETED,STALE,DUPLICATE)을 먼저 정의한다. R04 시험은 이 port의 검증된 fake를 쓰므로 R05 구현 전에도 컴파일/검증 가능하다.
- R05: 위 port를 `ReportResultService`가 구현한다. 내부에서 파일 I/O 후 `complete(JobLease,StoredReport)`를 별도 transaction bean으로 호출한다. `StoredReport`는 request/attempt/key/size/checksum/contentType을 보유한다. 외부 HTTP/파일 I/O가 DB transaction에 들어가지 않는지 integration 시험으로 확인한다.

### 고정할 실제 입력 파일

- 기업 마스터 기본 입력: `A/python/listed_company_codes.csv`. 종목 코드가 있는 기업을 기본 적재 집합으로 사용한다. `all_company_codes.csv`는 참조/대조 자료이며 기본 측정에 비상장 전량을 임의로 추가하지 않는다.
- 기업 업종: `A/기업명-업종코드.xlsx`.
- 실제 지표: `A/2025Q3_13지표_4분기추이_no_industry.xlsx`.
- 업종 상대 지표: `A/2025Q3_5지표_업종상대값_4분기추이.xlsx`.
- 대응 CSV는 XLSX canonical 값 대조에 사용한다. source hash와 행수는 D01에서 새로 계산한다. 원본 .env/키/개인 자료는 fixture나 결과물로 복사하지 않는다.

### 실제 데이터와 snapshot 조회

- 한 논리 데이터셋은 기업·업종 매핑/13지표/5상대지표 원본 hash, baseQuarterKey=20253, parserVersion, decimalPolicyVersion을 묶어 식별한다. 하나의 import 실행 ID와 논리 snapshot ID는 구분해 재실행 이력을 남긴다.
- canonical stockCode는 숫자 셀의 정수 표현을 6자리 패딩하거나 문자열의 0/영문자를 보존해 `[0-9A-Z]{6}`로 검증한다. 빈 문자열/`null`은 격리하고 기업명으로 임의 추정하지 않는다. corp_code 8자리와 혼용하지 않는다.
- 지표 값은 퍼센트 포인트 단위다. 명시적 `%` 표시/Excel percent format과 일반 숫자 형식을 구별한다. NULL은 NULL이며 0과 다르다. DECIMAL(20,4), HALF_UP로 한 번 정규화하고 overflow/NaN/무한대/수식의 무검증 캐시 값은 실패/격리한다. CSV 인코딩은 manifest의 UTF-8 BOM 또는 MS949 명시값을 사용한다.
- chunk는 파싱 정규화 후 500개 canonical cell(시험에서는 10개)로 고정된 순서를 사용한다. stage 반영+checkpoint를 같은 트랜잭션으로 커밋한다. 일시 DB 오류만 최대 3회 재시도하고 잘못된 값은 격리한다.
- materialization은 snapshot/기업/분기 단위로 한 번 생성한 reportVersion에 13+5 지표를 합쳐 넣는다. 기존 `uk_metric_value_type(report_version_id,metric_id,quarter_id,value_type)`를 활용하며 같은 키 다른 값은 조용히 덮지 않고 충돌로 처리한다.
- snapshot은 PREPARING→STAGING→STAGED→VERIFIED→활성 pointer 참조 상태로 진행한다. D02는 STAGED까지만 확정하며, 준비 중단은 동일 입력 재접수로 계획을 보완하고 start 정상 반환 뒤에는 DB payload와 checkpoint로 재개한다. 격리 정책 버전도 논리 데이터셋 식별에 포함하며 같은 snapshot의 chunkSize는 고정한다. 활성 전환은 DB 단일 커밋. 미완료/실패 snapshot은 현재 조회와 최신 분기 계산에서 제외한다. 새로운 범위를 먼저 조회 코드에 적용한 뒤 materialization을 수행한다.
- active snapshot이 없을 때만 기존 snapshot=NULL 데이터의 기존 조회를 보존한다. active가 생기면 현재 재무 조회는 해당 snapshot만 사용한다. predicted/general PDF의 기존 데이터는 별도 범주로 유지하고 합성 점수는 출처를 표시한다.
- `ReportSourceReader.load(UUID snapshotId, Long companyId, int targetQuarterKey)`는 명시된 snapshot과 대상 분기까지의 실제 지표만 읽는다. active pointer를 내부에서 다시 조회하지 않는다. 요청이 참조하는 과거 snapshot은 삭제하지 않는다.

### 요청·Outbox·Inbox·job

- `ReportRequestService.accept(Long userId, Long companyId, String key, CustomReportCommand command) -> AcceptedReportRequest`: 회사 경로를 명시적으로 전달한다. 멱등 키 조회→입력 hash 비교→신규인 경우 active snapshot/분기 고정→admission 슬롯 획득→request+outbox 커밋. DB 고유 제약 경합의 재조회는 별도 트랜잭션에서 수행한다.
- `report_admission`: GLOBAL 및 USER:<id> 카운터를 일정한 순서로 잠근다. 수락 트랜잭션 안에서 둘 다 증가하고 최초 최종 결과/최종 실패 처리에서 request.admissionSlotHeld를 true→false로 바꾸는 동일 transaction에서 한 번만 반환한다. 관리 재개는 새 슬롯 획득 후 진행하며 동일 recoveryCommand 반복은 재획득하지 않는다.
- wire envelope=`eventId, eventType, schemaVersion=1, requestId, occurredAt, payload`. payload는 고정된 회사·분기·snapshotId·입력 hash·추가 요청을 포함한다. `REQUEST_ACCEPTED`와 최초 상태는 구분하며 상태 event는 stateVersion과 현재 상태를 포함한다. 워커는 DB 스냅샷과 일치하는지 확인한다. eventId와 requestId를 로그에 남기되 추가 요청 원문은 로그에 쓰지 않는다.
- topics=`sentinel.custom-report.request.v1`, `sentinel.custom-report.status.v1`, `sentinel.custom-report.dlt.v1`. partition key=requestId. 기본 3 partitions/단일 broker RF=1. 다중 broker HA로 해석하지 않는다.
- Outbox 공통 columns=`id,aggregatetype,aggregateid,type,destination,payload,created_at`. id=eventId, aggregateid=requestId. destination을 위 토픽 allow-list로 제한한다. CDC는 Event Router의 route.by.field=destination, route.topic.replacement=${routedByValue}; 폴링도 같은 row와 wire bytes 계약을 사용한다. CDC에서는 INSERT만 발행하고 outbox row를 delivery 상태로 UPDATE하지 않는다.
- `ReportJobInbox.accept(ReportEvent event) -> ACCEPTED|DUPLICATE`: inbox unique insert+job READY 생성+request ENQUEUED/status event를 동일 DB 트랜잭션으로 완료한다. 콜백 호출이 커밋을 의미하지 않으므로 transaction 프록시 반환 뒤에만 Kafka ACK한다.
- job 상태 READY/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED. request 상태 ACCEPTED/ENQUEUED/PROCESSING/RETRY_WAIT/SUCCEEDED/FAILED. 격리는 메시지 단위 journal이며 임의 payload만 믿고 실제 요청을 FAILED로 바꾸지 않는다.
- `claimReady(int limit, String owner, Instant now) -> List<JobLease>`: DB 시각·SKIP LOCKED/조건부 UPDATE와 fencing version으로 경합 제어. 외부 호출 중 DB 트랜잭션/행 잠금을 유지하지 않는다. leaseToken+generation+version이 같은 시도만 heartbeat/완료/실패 처리 가능하다. 여기서 JobLease.version은 claim/관리 재개 때만 증가하는 실행 fence다. heartbeat는 leaseUntil만 갱신하며 fence를 증가시키지 않는다. 별도 row optimistic version과 혼용하지 않는다.
- `complete(JobLease lease, StoredReport report)`: job/request 최종화+artifact 연결+admission 반환+상태 Outbox를 같은 트랜잭션으로 커밋한다. requestId당 최종 artifact는 하나다. 늦은 시도의 파일은 고아 후보로 남긴다.
- `recover(Long actorUserId, UUID requestId, UUID recoveryCommandId, String reason)`: DB의 ACTIVE/ROLE_ADMIN과 FAILED 확인 후 기존 job의 generation/version 증가, 재시도 예산 초기화, 새 슬롯 획득, READY 전환과 감사 이력 저장을 한 DB 트랜잭션에 수행한다. 완료된 결과/원문/snapshot은 변경하지 않는다.

## 시험용 초기 기본값

| 항목 | local 기본값 | 결정적 시험 override |
| --- | --- | --- |
| worker 수 | 4 | 경합 2, 단일 순서 1 |
| HTTP connect/response timeout | 2s / 10s | timeout 시나리오 200ms |
| attempt 최대 수행 시간 | 20s | 지연/취소 시험에서 명시 |
| job lease / heartbeat | 30s / 5s | MutableClock으로 3s / 500ms 상당의 시각 진행 |
| READY/재시도 복구 scan | 1s | 테스트에서 직접 recover(now) 호출 |
| 최대 attempt | 최초 포함 3회 | 3회, backoff 1s/5s+jitter(seed 고정) |
| admission GLOBAL / USER | 1,000 / 20 | 한도 경합 5 / 3, 부하 시험은 manifest로 별도 고정 |
| 폴링 릴레이(선택 시) | 500ms, batch 100, lease 30s | 시각/ACK barrier로 제어 |
| 상태 cache TTL | 2s | 가짜 시각/실제 Redis TTL 모두 확인 |
| PDF 최대 크기 | 10MiB | 0byte/초과/잘린 PDF 입력 |
| 일반 부하의 모의 AI 지연 | 200ms | 별도 장시간 2s, 오류/timeout 프로필 |

CLI/PS wrapper는 DB/명령행에 비밀을 출력하지 않는다. 로컬 health는 apiReady(DB+수용 한도), workerReady(DB+실행 가능 상태), liveness를 별도로 관측한다. Kafka 장애는 접수 API 재기동을 막는 강제 depends_on 조건으로 쓰지 않는다.

## 파일·재시도·조회 계약

- `ReportGenerationPort.generate(ReportSourceData source, CustomReportInput input, UUID requestId) -> GeneratedReport`: 새 모의 HTTP POST 계약을 사용한다. 기존 회사 코드 GET API에 추가 내용/분기를 전달할 수 있다고 가정하지 않는다. 실제 원격 adapter는 구현 후속이다.
- 요청별 파일 key=`custom-reports/<requestId>/<generation>/<attemptId>.pdf`. 전체 경로를 사용자 응답에 반환하지 않는다. 파일 바이트 크기·PDF 유효성·SHA-256을 검증한 뒤 연결한다. 실패 시도/lease 만료 파일은 유예 후 참조 여부를 확인해 정리한다.
- HTTP port의 자동 Retry는 끄고 워커 원장만 실행 재시도를 담당한다. 외부 처리 후 응답 유실은 외부 중복 호출 가능성을 남기되 DB 결과 중복과 구분해 측정한다.
- 상태 cache key=`custom-report:status:v1:<userId>:<requestId>`. cache-off/Redis 장애/미스는 소유권 확인 DB 조회로 fallback한다. 값에 owner/stateVersion 포함, 늦은 이벤트는 Lua version 비교로 거부한다. 모든 상태 변경 DB 트랜잭션에서 status Outbox 이벤트를 기록한다.
- 상태 projector는 Redis/DB의 일시 장애에 원본 메시지를 ACK하고 버리지 않는다. 별도 소비자 그룹에서 재시도/정지 후 복구하고 TTL/DB fallback으로 조회를 유지한다. 잘못된 메시지는 K02의 durable 격리 경로를 사용한다. request/job 소비자의 진행과 분리한다.

## 공식 문서와 버전 검증

- [Spring Kafka 3.3 listener commit/ACK](https://docs.spring.io/spring-kafka/reference/3.3/kafka/receiving-messages/message-listener-container.html)
- [Spring Kafka 3.3 오류 처리](https://docs.spring.io/spring-kafka/reference/3.3/kafka/annotation-error-handling.html)
- [Debezium Outbox 라우팅](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)

로컬 캐시에 spring-kafka 3.3.11이 존재함을 확인했지만 실제 해석된 dependency 버전은 E01의 dependencyInsight 결과로 고정한다. Spring Kafka 4.x 전용 API를 그대로 가져오지 않는다. 위 reference의 패치 버전과 실제 버전 차이도 착수 시 확인한다.
