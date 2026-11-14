# ADR 0002: 맞춤 보고서의 전달 로그와 실행 원장을 분리한다

- 상태: 채택. 공개 API와 로컬 모의 생성 서버에 구현, 원격 AI는 미검증.
- 기존 결정: [CDC 발행 선택](0001-outbox-relay.md).

## 문제와 선택

사용자가 추가 지시문을 입력해 보고서를 요청하면, 요청 응답보다 생성이 오래 걸릴 수 있다. API의 DB commit과 Kafka 전송을 두 번 쓰면 그 사이 장애에서 요청만 저장되거나 메시지만 전달될 수 있다. Kafka가 메시지를 다시 전달할 때 외부 생성이나 파일 기록까지 한 번만 실행된다고 가정해서도 안 된다.

요청·추가 지시문 hash·수락 시점 snapshot·수용 슬롯·Outbox를 한 MySQL 트랜잭션으로 저장한다. 선택한 Debezium CDC가 커밋된 Outbox를 Kafka로 전달한다. 소비자는 eventId별 Inbox와 DB job을 원자적으로 저장한 다음 ACK한다. worker는 Kafka offset 대신 DB READY/lease/retry 원장을 기준으로 실행한다. 파일은 attempt별 경로에 먼저 기록하고 현재 lease를 소유한 시도만 artifact·성공 상태·슬롯 반납을 함께 확정한다.

Kafka를 선택한 근거는 요청 접수와 생성 실행의 분리, 재전달 가능한 로그, 상태 projector 등 소비자 그룹의 독립성이다. 이를 현재 규모에서 단순 DB job이나 인메모리 실행기보다 처리량이 높다는 주장으로 바꾸지 않는다. DB job만으로도 작은 서비스는 구성할 수 있다. 현재 Kafka는 job의 유일한 원장 역할을 하지 않으며 재시도·관리 재개도 DB에 기록한다.

## 대안과 비용

- 인메모리 executor는 간단하지만 프로세스 종료 뒤 복구할 수 있는 수락·작업 원장을 별도로 마련해야 한다.
- DB job/scheduler만 사용하면 운영 요소가 적다. 여러 독립 소비자에게 같은 이벤트를 제공하거나 로그를 재생할 요구가 작다면 우선 검토할 대안이다.
- Kafka/CDC는 broker·Connect·schema history·offset·binlog 보존·관측과 복구 절차의 비용이 추가된다. CDC와 polling을 동시에 켜지 않는다. 이번 선택은 CDC이므로 polling ACK 경계 F15는 N/A이며 구현·검증 완료로 표시하지 않는다.

## 보장 범위와 한계

전달은 at-least-once이며, DB 고유 제약과 Inbox/job/lease가 DB 업무 효과의 중복을 막는다. 외부 서버가 처리한 뒤 응답이 유실되면 재호출할 수 있다. 외부 HTTP까지 exactly-once이거나 다중 broker의 고가용성이 검증됐다는 의미가 아니다. 보고서 요청에는 현재 추가 지시문이 구현됐지만 원격 AI 품질과 과금 중복 방지는 검증하지 않았다.

정지 복원은 DB와 파일을 함께 보존하고, 별도 broker/connector에서 Outbox snapshot을 다시 전달하며 복원된 Inbox/job으로 중복을 방어한다. 과거 DB에 원래 환경의 최신 offset을 붙이지 않는다. 이 절차는 정지 시점 복원이며 지속적인 무손실 재해복구가 아니다.

## 코드와 검증 경로

- 요청/Outbox: `ReportRequestService`, `OutboxWriter`; 전달/Inbox: `CustomReportKafkaConsumer`, `ReportJobInbox`.
- 실행/확정: `ReportJobWorker`, `ReportJobClaimService`, `ReportCompletionService`.
- 실제 환경 시험: `ReportDeliveryRecoveryTest`, `ReportAcceptanceCrashTest`, `ReportConsumerAckIntegrationTest`, `PortfolioFaultMatrixTest`.
- 조건과 한계는 `perf/fixtures/fault-matrix.json`, 실행 원자료는 Git 제외 `perf/results/20261113-m05-m07`에서 별도 판정한다. 시험 코드가 존재한다는 사실만으로 실행 통과를 주장하지 않는다.
