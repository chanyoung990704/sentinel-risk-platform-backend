# SENTINEL 장애 복구·데이터 정합성·성능 보강 작업 계획

> **에이전트 실행자 지침:** 구현 요청을 받은 뒤 `superpowers:executing-plans` 또는 사용자가 선택한 `superpowers:subagent-driven-development`를 적용해 체크박스 단위로 진행한다. 이 문서는 검토용 초안이며 현재 실행 승인을 의미하지 않는다.

**Goal:** 맞춤 보고서 요청의 신뢰성, 실제 재무 데이터의 재시도·정확성, Redis/MySQL 성능과 Kafka 장애 복구를 재현 가능한 근거로 보강한다.

**Architecture:** MySQL의 요청 원장과 Outbox를 같은 트랜잭션에 기록하고 CDC를 우선 검증해 Kafka로 전달한다. 소비자는 Inbox와 durable job을 함께 저장하며, 외부 작업은 짧은 DB 트랜잭션 밖에서 수행한다. Redis는 복구 가능한 조회/집계 계층에 적용한다.

**Tech Stack:** 기존 Java 21·Spring Boot 3.5.9·Spring Batch·Flyway·MySQL·Redis·Spring Kafka, CDC 후보 Debezium/Kafka Connect, JUnit·Mockito·Testcontainers·MockWebServer, 기존 JMeter 플랜. 인프라 버전은 단계 1에서 호환성을 검증해 고정한다.

**Spec:** [설계 초안](../specs/2026-11-02-portfolio-reliability-design.md)

**재검토:** 2026-11-03. 실제 배포를 가정한 완성도와 포트폴리오 증거를 기준으로 필수/조건부 범위, 기본 운영 시험, 측정 순서를 보완했다. 구현은 시작하지 않는다.

## 세분화된 실행 계획

2026-11-03 사용자가 설계 방향에 동의한 뒤 [애플리케이션 구현·측정 실행 계획](2026-11-03-execution-index.md)을 추가했다. 이 문서는 상위 로드맵으로 유지하고, 실제 실행은 새 문서의 작업 ID·인터페이스·시험·측정 계약을 따른다. 아래 migration 파일명은 초기 후보이며 새 공통 실행 계약의 예약 순서를 우선한다. 현재는 계획만 작성했고 구현/설치/DB 변경/새 실험을 시작하지 않았다.

## 범위와 순서

`요구·보장 기준 확정 → 로컬 실행/CDC 가능성 → 실제 데이터 검증·적재 → MySQL 기준선 → 맞춤 요청 원장/Outbox → Inbox/워커 → CDC 또는 폴링 통합 → Redis 측정·적용 → 장애 실험 → 포트폴리오 증거`

- 지금은 계획 수립만 수행한다. 구현·환경 설치·DB 적재·성능 개선 수치를 완료했다고 표현하지 않는다.
- 기존 일반 보고서 기능을 유지하고 맞춤 요청은 새 계약으로 추가한다.
- 연도별 대량 축적·파티셔닝·보관 최적화는 후속 범위다.
- 실제 AI 모델 생성 품질은 제외한다. 모의 서버의 입력 수신부터 유효한 시험 PDF 반환·결과 다운로드까지는 전체 흐름을 시험한다.
- 외부 인프라 없는 테스트 통과와 실제 MySQL/Redis/Kafka 통합 검증을 구분한다. 이전에 확인한 384개 통과 결과는 전자의 기준선이다.
- 기존 사용자 변경(`gradlew` 포함)을 보존한다. 각 단계는 기능 추가 전 실패 재현 시험 → 최소 구현 → 통합 검증 → 결과 기록 순서로 진행한다. 커밋/배포는 별도 실행 요청 범위에 따른다.

## 필수 결과와 조건부 확장

| 구분 | 범위 |
| --- | --- |
| 필수 대표 사례 1 | 맞춤 보고서 한 흐름을 입력부터 본인 다운로드까지 완성하고 Outbox/Inbox·중복·재시작 복구를 입증 |
| 필수 대표 사례 2 | 실제 파일 적재의 중단/재개·재업로드·NULL/분기/정밀도·활성 snapshot 검증 |
| 필수 대표 사례 3 | 기존 조회 한 경로의 병목과 개선 전후 비교. 효과 없는 Redis 구성은 불채택 근거를 남김 |
| 필수 기반 | 로컬 실제 MySQL/Redis/Kafka, CDC 우선 판정과 선택한 발행기, 영향 범위 회귀 시험, 최소 운영 점검과 재현 가능한 결과물 |
| 조건부 확장 | 재생 가능한 Redis 집계의 DB 지연 반영, 다중 브로커 복제 장애 시험, 보존 구간 초과 자동 복구 |
| 후속 범위 | 연도별 대량 데이터, 공개 클라우드 배포·상시 HA, 실제 AI 품질, 독립 알림/감사 서비스, 전체 UI 개편 |

단계 번호는 의존 순서다. 각 단계에서 해당 실패를 먼저 재현하고 구현 후 검증하며, 단계 8에서 처음 장애 시험을 시작하지 않는다. 조건부 확장 미구현을 필수 결과의 미완료로 보지 않는다. CDC 우선 원칙과 선택한 발행기 검증은 필수로 유지한다.

## 우선 검토할 실패 조건

1. 요청 DB 커밋 이후 응답 유실·브로커/커넥터 중단에도 수락한 요청이 사라지지 않아야 한다.
2. Inbox 커밋·offset 커밋·워커 기동 사이에서 종료해도 작업을 회수하고 결과를 중복 확정하지 않아야 한다.
3. 외부 AI 타임아웃·늦은 성공·lease 만료가 겹쳐도 옛 워커가 새 상태/파일을 덮지 않아야 한다.
4. 같은 원본 파일 재업로드·chunk 실패·파서 정책 변경을 구분하고 부분 적재 데이터를 정상 snapshot으로 노출하지 않아야 한다.
5. 권한·개인 추가 요청·데이터 버전이 다른 보고서나 캐시를 서로 재사용하지 않아야 한다.

## 단계 1. 로컬 실행과 CDC 가능성 확인

**변경 후보:** `docker-compose.yml`, `docker-compose.local.yml`, `.env.example`, `src/main/resources/application-local.yaml`(신규 후보), `infra/cdc/`(신규), `docs/local-runtime.md`(신규), `src/test/java/com/aivle/project/infrastructure/`(신규).

**입력 → 산출물:** 현 소스와 복원 점검 보고서 → 반복 실행 가능한 local/test 프로필, 인프라 버전표, CDC 채택/불가 판정 기록.

- [ ] 기존 DB/포트/볼륨을 보존하면서 개발용 MySQL·Redis·Kafka·모의 AI 서버를 별도 구성한다. Docker/WSL2 기반이 사용 가능한지 확인하고 설치나 관리자 권한이 필요한 작업은 실행 요청 단계에서 구체적으로 다룬다.
- [ ] 일반 실행·외부 AI 모의·인프라 통합 테스트·부하 시험 설정을 나눈다. 인증용 로컬 키/pepper와 브로커 광고 주소를 환경에 맞게 구성한다.
- [ ] API 접수·워커 처리·프로세스 생존의 상태 확인 기준을 분리한다. Kafka가 중단된 상태에서 API를 재기동할 수 있는지와, DB 실패를 health/readiness에서 식별하는지 확인한다. 새 접수 기능과 연계한 최종 검증은 단계 6에서 수행한다.
- [ ] 원래 경로의 테스트 클래스 로딩 문제를 최소 재현하고, 재현성을 확보할 실행 경로/설정을 고정한다. 초기 전체 테스트와 통합 테스트를 따로 실행한다.
- [ ] 별도 테스트 DB의 작은 Outbox 테이블로 Debezium CDC를 시험한다. 커밋/롤백, 커넥터/브로커 재시작, 재전달, offset/history 보존을 확인한다.
- [ ] MySQL ROW binlog·FULL row image·권한·binlog 보존, 이미지/Connect/클라이언트 호환성과 자원 사용을 확인한다.
- [ ] 결과를 `CDC 채택` 또는 `확인한 제약으로 폴링 채택`으로 기록한다. 단순 설정 오류나 일시 중단만으로 폴링을 택하지 않는다.

**완료 기준:** 새 개발 환경에서 기동/종료/재기동이 재현되고 핵심 구성 요소 연결이 확인된다. CDC 판정에는 실제 실행 근거와 미해결 조건이 있다.

## 단계 2. 실제 데이터의 검증·재시작 가능한 적재

**변경 후보:** `src/main/java/com/aivle/project/dataimport/`(신규), 기존 `company/batch/`, `company/repository/CompaniesJdbcRepository.java`, `company/util/ExcelCompanyIndustryParser.java`, `report/importer/ExcelMetricParser.java`, `report/util/ExcelIndustryMetricParser.java`, `report/service/CompanyReportMetricImportService.java`, `report/repository/CompanyReportMetricValuesRepositoryImpl.java`, `src/main/resources/db/migration/V28__create_data_import_runs_and_snapshots.sql` 및 H2 대응(신규 후보).

**인터페이스 제안:** `ImportRunService.start(ImportDatasetCommand) -> ImportRunId`, `resume(ImportRunId)`, `verify(ImportRunId) -> ImportVerificationResult`, `activate(ImportRunId)`; 명칭은 구현 착수 시 설계에 맞춰 확정하고 기존 API와 연결한다.

- [ ] 파일 fingerprint·기준 분기·파서/정밀도 정책 버전으로 import 식별 규칙을 확정한다. 13지표·5상대지표·업종 파일의 동일 데이터셋 관계를 명시한다.
- [ ] 실제 파일 기반 검사기를 만든다. 기업 키/분기/단위/NULL/영문 종목 코드/중복/참조 무결성/정밀도 정책과 CSV-XLSX 대조를 기록한다.
- [ ] ‘동일 파일 두 번’, ‘10개 chunk 중 6번째 종료’, ‘deadlock 후 재시도’, ‘잘못된 코드·숫자’, ‘원본 수정 후 새 버전’을 먼저 실패 시험으로 만든다. chunk 크기는 설정화하고 작은 시험 크기에서 중단 지점을 재현한다.
- [ ] 기업 CSV 오프라인 로더와 staging→검증→활성 snapshot 흐름을 구현한다. chunk 데이터와 진행 체크포인트는 같은 DB 트랜잭션에 저장한다. 재시도는 미완료 chunk에 한정하며 DB 고유 키를 최종 방어선으로 둔다.
- [ ] 같은 파일 재업로드가 보고서 버전을 불필요하게 늘리지 않는지 확인한다. 13지표와 5상대지표가 서로 다른 버전에 들어가 최신 조회에서 일부가 사라지지 않는지도 검증한다.
- [ ] 원본→정규화 staging→DB 전체 키·값 대조와 건수/결측/격리 사유 보고서를 생성한다. 활성 snapshot 전환 실패 후 기존 정상 조회가 유지되는지 시험한다.
- [ ] 보관된 DART 원문에서 재계산 가능한 표본과 산식을 대조한다. 산식·원자료가 부족한 지표는 적재 정합성만 확인한 것으로 표시한다.

**완료 기준:** 같은 입력/정책 재실행의 논리 결과 동일, 성공 chunk 중복 반영 0, 설명되지 않는 값 불일치 0, 실패한 적재의 부분 결과 미노출. 경제적 사실 정확성의 검증 범위를 별도로 제시한다.

## 단계 3. MySQL 초기 성능 기준선

**변경 후보:** `perf/jmeter/`, `perf/scripts/`, `docs/perf-baseline.md`(신규), `src/test/java/com/aivle/project/perf/` 및 관련 조회 repository 시험.

**입력 → 산출물:** 단계 2의 검증된 고정 snapshot → 캐시/새 비동기 구조 적용 전 benchmark run과 실행계획·쿼리 수·지연 분포.

- [ ] 기업 검색, 관심기업 목록, 기업 상세, 대시보드 중 현재 존재하는 조회를 기준선 후보로 선정하고 실제 접근/쓰기를 확인한다. 현재 동작하지 않는 경로를 기존 병목으로 기록하지 않는다.
- [ ] 새 DB 요청 원장을 사용하는 상태 조회와 맞춤 접수/완료의 성능 기준선은 단계 4~6의 기능이 완성된 뒤 단계 7 적용 직전에 측정한다. 기존 Redis 상태 조회를 새 DB 경로의 캐시 미적용 기준선으로 대신 사용하지 않는다.
- [ ] 대시보드의 AI 점수 누락은 실제 재무 데이터와 분리된 합성 fixture로 보완하고 출처를 표시한다.
- [ ] SQL 실행계획, 요청당 쿼리 수, DB 연결 대기, P50/P95/P99·TPS·오류율·CPU/메모리를 기록한다. 워밍업/반복/캐시 상태/동시 사용자 수를 고정한다.
- [ ] 조회만 측정하는 부하와 보고서 생성 접수·완료 부하를 분리한다. 성능 수치는 성공/실패 응답을 섞어 해석하지 않는다.
- [ ] 병목과 개선 후보를 근거로 정렬한다. Redis가 이득을 주지 않는 쿼리는 인덱스/조회 구조 보강을 우선한다.

**완료 기준:** 다시 실행 가능한 설정과 원자료가 있고, 이후 비교할 변경 변수가 정해진다. 개선율은 이 단계에서 미리 정하지 않는다.

## 단계 4. 맞춤 요청 원장과 Transactional Outbox

**변경 후보:** `company/reportrequest/`(신규), `messaging/outbox/`(신규), `CompanyAiController.java`, `AiJobDispatchService.java`, `AiJobMessage.java`, `CompanyAiService.java`, `V29__create_report_requests_and_outbox.sql` 및 H2 대응(신규 후보), A의 `front/front/src/api/companies.ts`와 보고서 요청 화면.

**인터페이스 제안:** `POST /api/companies/{companyId}/custom-report-requests`, `GET /api/report-requests/{requestId}`, 요청별 결과 다운로드. `ReportRequestService.accept(userId, idempotencyKey, command) -> AcceptedReportRequest`.

- [ ] 최소 계약을 고정한다: 기업·대상 분기·추가 요청(초안 최대 2,000자), source snapshot/템플릿 버전, 요청 소유자. idempotency 조회를 먼저 수행해 재시도 사이에 ‘최신 분기/데이터’가 바뀌어도 기존 접수를 반환한다.
- [ ] DB rollback, 커밋 직후 프로세스 종료, HTTP 응답 유실 후 재전송, 동일 키 동시 접수, 동일 키 다른 본문, 다른 사용자 결과 접근을 실패 시험으로 만든다.
- [ ] report_requests와 최초 outbox_events를 한 DB 트랜잭션에서 저장한다. `eventId`와 `requestId`, 작업 시도 ID를 구분하고 고유 제약을 건다. 202는 커밋 후 반환한다.
- [ ] 새 요청 경로의 직접 Kafka 전송/로컬 Async fallback을 제거한다. 브로커 장애 시 DB에 수락된 작업은 대기 상태로 보존한다. 기존 일반 보고서 API의 호환성은 별도 시험한다.
- [ ] 최소 입력 화면과 requestId 기반 조회·다운로드 연결을 만든다. 같은 기업/분기라도 추가 요청·소유자·snapshot이 다른 결과를 재사용하지 않는지 검증한다.
- [ ] snapshot A로 접수하고 워커 실행 전에 B를 활성화한 뒤, 최초 실행과 재시도가 실제 조회·AI 입력에서 계속 A를 쓰는지 확인한다. 미완료 요청이 참조하는 A는 삭제하지 않는다.
- [ ] DB 장애 및 적체 상한에서 신규 접수를 성공으로 반환하지 않는지 시험한다. 사용자별 제한은 429, 전체 수용 한도/저장 불가 시 신규 접수는 503을 기본으로 하고 기존 동일 멱등 요청은 재사용한다. 수락한 요청은 과부하 때문에 삭제하지 않는다.

**완료 기준:** 동일 요청 재전송은 동일 requestId, 다른 본문의 같은 키는 409, rollback한 요청의 이벤트 0, 수락된 요청과 최초 Outbox의 불일치 0.

## 단계 5. Inbox·워커·파일 결과의 장애 복구

**변경 후보:** `messaging/inbox/`, `company/reportjob/`(신규), `company/job/AiJobKafkaConsumer.java`, `company/service/CompanyAiService.java`, `CompanyAiReportStoreService.java`, `AiReportRequestStatusService.java`, AI 클라이언트 어댑터, 파일 저장소, `V33__create_report_jobs_and_artifacts.sql` 및 H2 대응(실행 계약의 재배정 번호).

**인터페이스 제안:** `ReportJobInbox.accept(event) -> AcceptanceResult`, `ReportJobWorker.execute(jobId, leaseToken)`, `ReportJobRecovery.recover(now)`, `ReportGenerationPort.generate(immutableRequest, requestId) -> GeneratedReport`.

- [ ] 수신 중복·Inbox 저장 전 종료·저장 후 offset 커밋 전 종료·작업 등록 후 워커 호출 전 종료를 먼저 시험한다.
- [ ] Inbox와 job 생성/요청 ENQUEUED 반영을 한 DB 트랜잭션에 묶는다. 성공한 DB 커밋 이후에만 offset을 커밋한다. 단순 ‘처리 중’ 표시만으로 메시지를 버리지 않는다.
- [ ] READY/RUNNING/RETRY_WAIT/SUCCEEDED/FAILED와 attempt/lease/version을 영속화한다. 워커 복구 스캐너가 실행 신호가 사라진 READY·기한이 지난 재시도·lease가 만료된 작업을 회수한다.
- [ ] 모의 AI 서버는 추가 요청·대상 분기·snapshot을 수신하고 유효한 시험 PDF를 반환한다. 지연, 5xx, 잘린 PDF, 응답 유실, 실제 처리 후 타임아웃을 주입한다. 입력 전달·결과 저장·다운로드를 끝까지 확인한다.
- [ ] DB/HTTP/파일 경계를 분리하고 최종 결과는 requestId당 한 번만 확정한다. 오래된 lease의 완료 커밋을 차단하고 고아 파일 정리·상태 회복을 검증한다.
- [ ] 워커 재시도에 횟수·백오프·jitter·다음 시각을 보존한다. 영구 오류는 즉시 분류하고 재시도 소진은 추적 가능한 최종 실패로 남긴다. 예외를 삼켜 Kafka가 정상 완료로 오인하는 기존 경로를 새 경로와 분리한다.
- [ ] 실제 AI 서버의 멱등성 미지원 한계를 문서화한다. 외부 호출 횟수와 최종 DB 결과 수를 따로 검증하고 ‘호출까지 정확히 한 번’이라고 주장하지 않는다.
- [ ] 동시 실행 수·timeout·재시도 한도를 설정화하고 정상 종료에서 새 claim 중단/진행 작업 완료 또는 회수, 강제 종료에서 lease 복구를 검증한다. 사용자 오류 응답과 상관 ID를 남기고 추가 요청 원문·인증 정보·내부 오류 상세의 노출을 방지한다.
- [ ] FAILED 요청의 동일 이벤트 재전달은 중복 제거되는 것을 먼저 시험한다. 권한이 제한된 관리 명령 `recover(requestId, recoveryCommandId, reason)`으로 기존 job의 generation/version을 올려 재개하고, 동일 명령 반복·동시 재개·옛 워커 완료를 거부/수렴시키는지 확인한다. 실패 이력을 유지하고 원인 제거 후 실제 한 건의 복구를 확인한다. SUCCEEDED 결과와 원래 요청 입력은 변경하지 않는다.

**완료 기준:** 장애 후 회수되지 않는 작업 0, 중복 이벤트의 중복 최종 결과 0, 늦은 워커의 상태 역전 0, 파일·DB 불일치가 복구/정리 대상으로 추적된다.

## 단계 6. CDC Outbox 발행 통합 또는 폴링 대안

**변경 후보:** `infra/cdc/`의 connector 설정, `messaging/outbox/`, Kafka 오류 처리 설정, `docs/outbox-relay.md`(신규), 관련 Testcontainers/장애 시험.

- [ ] 단계 1에서 CDC가 통과했으면 영속 Outbox와 Event Router를 연결한다. `eventId`, aggregate key=requestId, schemaVersion을 소비자 계약과 일치시키고 초기 snapshot/정리 이벤트 처리를 시험한다.
- [ ] CDC 제약이 확정됐다면 폴링 릴레이를 구현한다. 짧은 claim 트랜잭션+lease/fencing, 발행 ACK 확인, 실패 백오프, 배치 크기 제한을 둔다. ACK 직후 상태 저장 전에 종료되는 경우의 재발행을 Inbox 시험으로 수렴시킨다.
- [ ] 릴레이는 한 종류만 활성화되도록 설정 검증을 둔다. CDC 채택이 성공하면 폴링 대안까지 무조건 구현하지 않는다.
- [ ] Kafka/Connect 중단·재시작, 보존된 위치 재개, 중복 전달, DLT 발행 실패, 다중 릴레이 경합(폴링인 경우)을 시험한다. binlog 소실에 대해서는 요청/Outbox 대조와 재발행의 한계·수동 절차를 필수 문서화하고, 자동 복구 구현/실험은 조건부 확장으로 둔다.
- [ ] outbox 잔류 시간, relay 지연, Kafka lag, 처리 실패/DLT 건수, accepted→최종 상태 대조를 수집한다. CDC가 발행한 row에 자동으로 published 표시가 생긴다고 가정하지 않는다.
- [ ] Kafka/CDC 중단 중 API를 다시 기동하고 DB와 적체 한도가 허용하는 신규 요청의 접수·대기 보존, 복구 후 처리를 확인한다. API 수락 가능 상태와 워커 처리 불가 상태가 구분돼 관측되는지 확인한다.

**완료 기준:** 커밋된 요청의 이벤트 전달과 재개가 반복 재현되고, 일시 중단을 이유로 두 발행기를 동시에 켜지 않는다. Outbox 정리는 복구 가능성과 보존 구간을 검증한 뒤 적용한다.

## 단계 7. Redis 적용과 측정 기반 확장

**변경 후보:** `company/service/AiReportRequestStatusService.java`, 기업 상세/대시보드 서비스, `common/cache/`(신규 후보), 결과 이벤트 소비자, Redis 통합 시험.

- [ ] 단계 3의 기존 조회 기준선과 단계 6 완료 후 새 요청 경로 기준선을 구분해 확보한다. 실제 병목이 확인된 한 경로에 cache-aside를 적용한다. 요청 상태 폴링을 첫 검토 후보로 두되 측정 없이 적용을 확정하지 않는다.
- [ ] 사용자·분기·snapshotVersion을 키에 반영한다. TTL·단일 재계산·캐시 채우기 경쟁과 장애 시 DB fallback을 시험한다. 늦은 이벤트는 version 비교로 거부한다.
- [ ] DB 커밋 후 캐시 갱신 실패가 최종 상태 유실을 만들지 않게 결과 Outbox 이벤트로 복구한다. Redis 전체 소실 후 DB에서 상태를 다시 읽는 시험을 한다.
- [ ] Redis에서 먼저 처리한 후 DB 반영할 대상은 재생 가능한 집계/read-model로 제한해 별도 효과 검증을 한다. 단계 3에서 적합한 병목이 없으면 구현하지 않고 기각 근거를 남긴다.
- [ ] 도입한다면 durable source event → Redis 집계 → version/batchId가 있는 DB snapshot 반영 구조로 만들고, Redis 반영 후 종료·DB 반영 후 ACK 유실·중복 이벤트·Redis 소실/재생을 시험한다. 원천 없이 Redis의 INCR/DEL만으로 정확한 누적을 주장하지 않는다.
- [ ] 동일 부하로 기준선과 비교하고 hit/miss, DB 쿼리 감소, tail latency, stale read·정합성 비용을 함께 보고한다.

**완료 기준:** 성능 효과와 허용되는 지연이 실제 측정으로 확인되고, Redis 장애가 영속 요청/원천 유실로 이어지지 않는다. 효과가 없는 구성은 제거한다.

## 단계 8. 동시성·장애 종합 실험

**변경 후보:** `src/test/java/com/aivle/project/reliability/`, `perf/faults/`, `docs/reliability-test-matrix.md`(신규), 관측 지표.

실험 표의 공통 항목과 채택한 발행기 관련 항목은 필수다. 폴링 전용 항목은 폴링 채택 시에만 적용한다. 보존 범위 초과 자동 복구와 다중 브로커 시험은 조건부 확장이며, 미구현 시 보장 한계·수동 대조 절차를 문서에 남긴다.

| 주입할 장애/경합 | 기대 결과 |
| --- | --- |
| DB 커밋 전 종료 | 접수 성공 없음, 요청/최초 Outbox가 함께 롤백 |
| DB 커밋 후 응답 전 종료 | 재접수 시 같은 requestId, 미발행 요청이 복구 후 전달 |
| Kafka/CDC 중단 후 복구 | 수락한 요청 보존, 커밋 이벤트 재전달·처리 |
| 동일 이벤트 반복/소비자 동시 처리 | Inbox/job 중복 생성 없음, 최종 결과 중복 0 |
| Inbox 커밋 후 offset 커밋 전 종료 | 재전달되지만 중복 효과 없음 |
| Inbox/job 커밋 직후 실행 신호 유실 | READY 작업 회수 후 실행 |
| 워커 timeout/lease 만료·늦은 응답 | 재시도 가능, 옛 워커가 최신 결과를 덮지 않음 |
| AI 응답 유실·형식 오류·5xx | 영구/일시 분류, 재시도 한도와 최종 상태 추적 |
| 파일 저장 후 DB 실패 | 고아 파일 추적·정리, 잘못된 완료 상태 없음 |
| Redis 중단/TTL 만료/전체 캐시 삭제 | DB 기준 상태 조회·캐시 재구성 |
| 같은 import 재실행·중간 chunk 종료 | 성공분 유지, 미완료분 재개, 논리 중복 0 |
| 검증 실패·snapshot 활성화 중 종료 | 이전 정상 데이터 조회 유지 |
| 추가 요청/소유자/분기 다른 맞춤 생성 | 입력/파일/캐시가 섞이지 않음 |
| A 버전으로 접수 후 B 활성화·재시도 | 실제 워커 조회·AI 입력·결과가 접수 시 A를 유지 |
| 재시도 소진 후 원인 제거·관리 재개 | 동일 이벤트 중복 제거와 구분해 같은 job을 명시적으로 복구, 중복 재개/늦은 완료 방지 |
| DLT 발행 자체 실패 | 원본을 완료로 버리지 않고 재시도·운영 추적 |
| Kafka 보존/binlog 범위 초과 | 기본은 보장 한계·수동 대조/재발행 절차 명시. 자동 복구 구현/실험은 조건부 확장 |
| 폴링 모드 ACK 후 종료/다중 릴레이 | 재발행돼도 Inbox/최종 결과 중복 없음 |

- [ ] 순서 보장과 전역 순서를 구분한다. 같은 requestId 내 stateVersion/attempt로 역전을 막는다.
- [ ] 재시도·DLT 재처리·재생 보존 기간보다 짧게 Inbox 식별 기록을 지우지 않는다. 결과 requestId 고유 제약은 유지한다.
- [ ] 작은 결정적 통합 시험으로 실패 지점을 먼저 고정한 뒤 동시 요청 부하를 단계적으로 올린다.
- [ ] 매 실험 후 접수·대기·성공·실패·격리 건수를 DB 기준으로 대조하고 복구 시간·중복 외부 호출·DB 오류·lag를 보고한다.
- [ ] 초기 단일 브로커 재시작 시험과 다중 브로커 quorum/replica 시험을 분리한다. 후자는 자원이 허용될 때 별도 프로필로 수행하고 결과 범위를 명시한다.
- [ ] 정상 종료/강제 종료·과부하 접수 제한·DB/Redis/Kafka의 서로 다른 장애를 역할별 health/readiness와 업무 상태로 확인한다. 기존 인증·권한·관심기업·일반 보고서·파일의 영향 범위 회귀 시험을 함께 실행한다.
- [ ] 분리된 시험 환경에서 정지 시점의 DB·결과 파일 snapshot을 복원하고 소유권·파일 연결·체크섬을 확인한다. 해당 복구 시점과 Kafka/CDC 위치·재전달 범위, 새 볼륨/키/경로 설정을 runbook에 남긴다. 무중단 백업/무손실 재해복구는 주장하지 않는다.

**완료 기준:** 공통 항목과 채택한 발행기에 해당하는 필수 장애가 재현 가능한 시험으로 존재한다. 조건부 실험의 수행/미수행을 표시하고, ‘유실 없음’ 등 주장은 해당 장애 모델·확인 건수·복구 범위와 함께 제시한다.

## 단계 9. 포트폴리오 산출물

**변경 후보:** `docs/adr/`, `docs/reliability-results.md`, `docs/data-quality-results.md`, `docs/performance-results.md`, `README.MD`, 포트폴리오 원고(작성 시 별도 파일 확인).

- [ ] Kafka 선택 ADR: 장시간 작업 분리·버퍼링·보존/재생·독립 소비자 확장 필요와 DB 큐/다른 브로커 대비 운영 비용을 설명한다. 현재 구현과 향후 맞춤 보고서 목표를 분리한다.
- [ ] Outbox/Inbox/worker/외부 서버의 보장 경계를 담은 흐름도와 상태 전이도를 만든다.
- [ ] 실제 데이터 적재 검증 보고서: 원본 fingerprint·매핑·정밀도·건수·결측·격리·원천 대조 가능 범위와 재시도 시험 결과를 담는다.
- [ ] 동일 조건의 성능 전후 원자료와 장애 타임라인을 남긴다. 202 접수 속도와 PDF 완료 시간을 분리하고 모의 AI임을 명시한다.
- [ ] ‘문제 → 선택 근거 → 구현 → 장애 실험 → 측정 결과 → 남은 한계’로 서술한다. 실제 검증 전 수치나 운영 경험을 추가하지 않는다.
- [ ] 세 대표 사례마다 변경 전 실패 재현, 변경 후 기대/관측 결과, 실행 명령·버전·데이터 fingerprint·runId·원자료를 연결한다. 테스트는 구현 단계에서 수집한 기록을 재사용하고 결과 문서에 원자료가 없는 수치를 추가하지 않는다.
- [ ] 신규 접수→작업 처리→본인 PDF 다운로드의 시연 절차, 정상/비정상 종료, 초기화/시험 snapshot 복원, 적체·실패 작업 조회와 관리 재개, DLT의 job 유무별 복구 runbook을 함께 제공한다. 공개 배포·실사용자 운영 경험으로 표현하지 않는다.

**완료 기준:** 다른 사람이 문서의 명령/설정/고정 데이터로 대표 실험을 재현하고, 주장과 근거를 연결할 수 있다.

## 실행과 검토 경계

- 각 단계에서 필요한 도구·새 테이블·새 API·합격 시험을 세부 구현 계획으로 좁힌 뒤 진행한다. 이 로드맵은 단계별 커밋과 모든 함수 구현을 미리 확정한 최종 실행 명세는 아니다.
- 제안 중 아직 확정하지 않은 사항은 인프라 버전 조합, 최적 chunk 크기, 캐시 TTL, 재시도 시간 간격, 실제 Redis write-behind 대상이다. 단계 1/3/7의 검증으로 결정한다.
- 설계 초안은 독립 builder 검토를 거쳤고, 계획을 세우기 위해 반드시 추가로 답해야 하는 중요한 공백은 없다는 결과를 받았다. 실제 사용자 요구 변경이나 CDC 불가 근거가 나오면 해당 단계부터 계획을 갱신한다.
