# 맞춤 보고서 CDC 전달과 복구

이 문서는 K01~K03의 실행 계약이다. 실제 MySQL·Kafka·Connect와 모의 AI HTTP를 사용하는 로컬 구성이다. 조건별 실측·실패 원자료는 Git 제외 `docs/results/messaging-recovery.md`, `perf/results/20261109-k01-k03`에 보관한다. 실제 AI 분석 품질, 다중 브로커 가용성, 서비스 SLA를 의미하지 않는다.

## 전달과 확정 경계

`ReportRequestService`가 요청·슬롯·최초 Outbox를 함께 커밋한다. 고정 connector `sentinel-custom-reports-v1`은 `sentinel_portfolio.outbox_events`의 INSERT만 처리한다. `destination`은 request/status/dlt의 세 토픽으로 제한한다. 일반 요청의 key는 requestId, header `id`는 payload.eventId다. JSON expansion과 String/JSON converter를 사용하며 기존 Outbox 본문을 수정하지 않는다. V34 폴링 migration은 사용하지 않으며 CDC 장애 중 자동 폴링으로 전환하지 않는다.

새 `CustomReportKafkaConsumer`는 byte[] key/value를 읽고 UTF-8·JSON·스키마·키·event ID·고정 입력 hash를 검증한다. `ReportJobInbox`의 별도 transaction이 Inbox·job·상태·상태 Outbox를 커밋한 뒤 listener thread가 동기 ACK한다. 커밋 후 ACK 실패는 같은 이벤트를 재수신할 수 있고 Inbox와 요청별 job 제약으로 중복 업무 등록을 막는다. DB 장애는 1초 간격으로 재시도하며 offset을 넘겨 조용히 버리지 않는다. 외부 HTTP 작업의 최대 3회 예산과 Kafka 수신 재시도는 구분한다.

Kafka ACK는 AI 완료를 뜻하지 않는다. DB의 READY 작업은 별도 작업자가 claim하고 HTTP·파일 처리 후 최종 확정한다. 외부 AI 요청 자체는 중복될 수 있다. 유효 lease/fence와 artifact 제약이 최종 DB 결과의 중복 반영을 막는다. 상세 실행 계약은 [맞춤 보고서 실행 문서](custom-reports.md)를 따른다.

## 격리와 재처리

영구 불량 메시지는 `quarantined_messages`와 DLT Outbox를 같은 transaction으로 기록한 뒤 원본 offset을 ACK한다. source topic/partition/offset/논리 consumer가 격리의 고유 키다. 동일 위치의 내용이 바뀌면 ACK하지 않는다. 원문은 최대 64KiB prefix, 전체 크기·SHA-256·잘림 여부로 보존하고 키·header도 제한한다. 초과 원문의 전체 복구를 주장하지 않는다. DLT에는 식별자·offset·hash·이유만 넣고 원문이나 자유 입력을 복제하지 않는다. DLT 브로커 장애 중에도 DB 원장/Outbox를 보존하며 자동 삭제하지 않는다.

깨진 JSON의 requestId/eventId는 신뢰할 수 없다. DLT의 key/requestId는 quarantineId 상관키이며 진짜 요청을 임의로 FAILED로 바꾸지 않는다. 수정 재전달은 관리자가 targetRequestId를 명시하고 DB의 고정 입력으로 새 eventId를 생성한다. 원본 참조가 해석 가능한 경우 `originalEventId` header와 감사 원장에 남긴다. 이 참조도 비신뢰 추적값이다. 원문을 SQL로 덮어쓰지 않는다.

ACTIVE 관리자와 명령 ID·사유를 확인한다. 같은 명령은 같은 결과를 반환하고 인수를 바꾸면 거부한다. job 없는 최초 ACCEPTED 요청만 새 이벤트를 만든다. 이미 등록된 job은 `ALREADY_REGISTERED`, FAILED job은 `MANUAL_RECOVERY_REQUIRED`를 반환한다. FAILED 실행을 재개하려면 R06 관리 명령으로 새 generation을 만들어야 한다.

```powershell
.\scripts\reliability\local.ps1 -Action stop-app
.\scripts\reliability\replay-quarantine.ps1 -QuarantineId <UUID> -CommandId <새 UUID> -ActorUserId <관리자 ID> -TargetRequestId <기존 요청 UUID> -Reason '계약 확인 후 재전달'
# FAILED job은 다음 별도 명령을 사용한다.
.\scripts\reliability\recover-report.ps1 -RequestId <UUID> -CommandId <새 UUID> -ActorUserId <관리자 ID> -Reason '장애 원인 제거 후 재개'
.\scripts\reliability\local.ps1 -Action start-app
```

로컬 운영자가 private DB 자격으로 실행하는 명령이다. 공개 웹 관리 API가 아니다. wrapper는 클래스 보호를 위해 실행 중인 관리 앱의 재컴파일을 거부한다. 동작 중인 원장·offset·history를 초기화하지 않는다.

## 기동과 준비 상태

기존 E03에서 CDC 선택을 기록한 환경과 D04 VERIFIED/ACTIVE 재무 snapshot이 필요하다. Ubuntu native 실행 사본에서 `python3 scripts/reliability/cdc_runtime.py up`으로 worker를 올린 뒤 Windows의 `configure-cdc.ps1 -Action configure`로 업무 connector를 구성한다. 고정 범위가 다른 기존 connector는 덮어쓰지 않는다. `-Action status`는 connector/task 상태만 출력한다. 비밀은 FileConfigProvider로 읽고 config/비밀번호/키를 출력하지 않는다.

`local.ps1 -Action start-app`은 앱에만 새 messaging·worker를 활성화한다. 공통 Gradle/CLI 환경과 `application-local.yaml`의 기본 worker-off는 유지한다. 기존 AiJob listener 설정도 유지한다. API 기동에 broker healthy 조건을 걸지 않으므로 DB가 가능하면 중단 중 요청을 Outbox에 기록할 수 있다. broker·Connect가 복구된 뒤 전달·실행이 이어진다.

| endpoint | 의미 |
|---|---|
| `/actuator/health/liveness` | 프로세스 생존 상태 |
| `/actuator/health/apiReady` | DB의 GLOBAL 수용량 기준 접수 여유. 사용자별 한도·인증·입력 유효성은 개별 요청에서 판단 |
| `/actuator/health/workerReady` | DB 접근과 enabled/accepting 작업자가 기존 DB 작업을 실행할 수 있는지. broker 전달 완료나 AI 서버 가용성을 보장하지 않음 |

단일 broker/단일 로컬 DB다. DB 자체 소실, 디스크 손실, binlog 보존 기간을 넘는 중단을 자동 복구한다는 의미는 아니다. CDC 재개 시 기존 offset/history를 먼저 확인하고 임의 삭제·새 snapshot으로 덮어쓰지 않는다. 현재 binlog 보존 설정은 10일이다.

MySQL 중단으로 CDC task가 EOF/FAILED가 된 경우 DB를 먼저 복구한 다음 `configure-cdc.ps1 -Action recover`를 명시 실행한다. 고정 업무 connector 범위를 검증하고 stop→STOPPED/tasks 없음 확인→resume→새 RUNNING을 확인한다. 비동기 재시작 직후 이전 RUNNING을 성공으로 오인하지 않기 위한 경계이며 offset/history를 삭제·변경하지 않는다. 전/정지/재개 checkpoint와 재개 관찰 시간을 출력한다. `deliveryVerified=false`는 상태 전이만 확인했다는 뜻이다. 미전달 요청의 eventId/job 진행으로 실제 전달을 별도 확인한다. 시험에서는 새 canary 요청의 실제 수신도 확인한다. Connect 프로세스 UP과 task RUNNING은 다르며 이 경우의 자동 자가 복구는 구현하지 않았다. 재개는 중복 전달을 일으킬 수 있으므로 Inbox 멱등 처리를 유지한다. [Kafka Connect stop/resume API](https://kafka.apache.org/37/kafka-connect/user-guide/)를 사용한다.

## 요청별 대조와 시험

```powershell
.\scripts\reliability\local.ps1 -Action stop-app
.\scripts\reliability\reconcile-requests.ps1 -RequestIds <UUID1>,<UUID2>
```

명시한 요청 1~1000개의 accepted/nonterminal/succeeded/failed/missingRequests, missingJobs, duplicateArtifacts, missingArtifacts, missingFiles, invalidArtifacts, unlinkedFiles를 읽기만 대조한다. 파일 크기·SHA-256·메타데이터와 참조되지 않은 소유 경로 파일을 확인한다. 원문·경로는 출력하지 않는다. 동시 상태 변경 중에는 관찰 시점이 다를 수 있으므로 종결 상태 또는 작업 중단 후 최종 대조한다. 재무값의 원천 사실 정확성 검증과는 별개다.

Ubuntu native 사본에서 실행한다. 각 task는 단독으로 실행하고 앱 클래스 사용 중 재컴파일하지 않는다.

```bash
./gradlew cdcTest --tests '*OutboxCdcIntegrationTest' --console=plain --no-daemon
./gradlew realInfraTest --tests '*ReportConsumerAckIntegrationTest' --tests '*ReportQuarantineIntegrationTest' --tests '*ReportReconciliationIntegrationTest' --console=plain --no-daemon
./gradlew reliabilityTest --tests '*ReportDeliveryRecoveryTest' --console=plain --no-daemon
```

통합 시험은 실제 로그인 JWT·HTTP 접수·CDC·Kafka·worker·모의 HTTP·로컬 PDF 다운로드를 연결한다. 테스트 소유 group은 시작 전 현재 end 위치에 고정해 이전 fixture를 업무로 재처리하지 않는다. 운영 group의 위치를 건너뛰는 동작은 넣지 않는다. 사용자 시험 상한200/글로벌1000/worker4를 사용하며 정상 사용자20과 구분한다. 기본 관찰 한도180초는 테스트 timeout이다. 생성 중 강제 종료는 모의 서버의 요청 수신 후 응답 보류 지점을 확인한 뒤 소유 Process만 종료한다. 모의 서버의 `/inflight/custom-reports`는 requestId/sequence만 노출하며 보류는 최대10초다. 외부 호출 횟수는 최종 결과 수와 별도 기록한다. 전체 장애 행렬과 새 부하·캐시 비교는 M 단계의 후속 범위다.
