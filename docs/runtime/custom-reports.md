# 맞춤 보고서 요청·실행·관리 재개

이 문서는 R01~R06의 실행 계약을 설명한다. 실제 전달과 통합 복구는 후속 [K01~K03 실행 문서](messaging-recovery.md)에 연결했다. 실제 배포나 실제 AI 모델 품질을 검증했다는 의미는 아니다. 시험 결과와 포트폴리오용 문제·해결·결과는 Git에서 제외한 `docs/results/custom-report.md`, `docs/results/messaging-recovery.md`, `docs/portfolio/포트폴리오 참고용.md`에 보관한다.

## 처리 흐름

1. `ReportRequestService.accept(userId,companyId,key,command)`가 입력을 정규화하고 사용자별 멱등 키를 확인한다. 신규 요청만 GLOBAL→USER 순으로 수용량을 잠그고 최초 검증 snapshot/분기/입력을 고정한다. 요청·슬롯·최초 Outbox는 한 transaction이다.
2. `ReportJobInbox.accept(event)`가 envelope와 고정 원문/hash를 비교한다. Inbox·READY job·ENQUEUED 상태·상태 Outbox를 함께 커밋한다. 중복 이벤트는 실패 작업을 다시 시작하지 않는다. 호출자는 이 메서드가 반환한 뒤 ACK해야 한다.
3. `ReportJobWorker.runOnce()`가 짧은 `SKIP LOCKED` claim으로 최대 4개를 가져온다. source 조회·HTTP·파일 저장은 claim transaction 밖에서 진행한다. 최초 snapshot의 최대 4개 ACTUAL 분기와 NULL을 보내고 원본 로컬 경로는 제거한다.
4. 외부 자동 retry는 사용하지 않는다. 일시 오류는 최초 포함 최대 3회, 1초/5초와 결정적 jitter로 재시도한다. 잘못된 PDF/입력 등 영구 오류는 즉시 FAILED로 끝난다. lease 30초·heartbeat 5초이며 heartbeat는 fence version을 바꾸지 않는다. 한 시도의 제한은 20초다.
5. 파일 key는 `custom-reports/<requestId>/<generation>/<attemptId>.pdf`다. PDF 구조·10MiB·100페이지·SHA-256을 검증하고 완전히 기록한 파일을 새 이름에 연결한다. 기존 이름은 덮어쓰지 않는다. `ReportCompletionService.complete`만 artifact·job/attempt·request·슬롯 반환·상태 Outbox를 함께 확정한다.
6. FAILED 관리 재개는 내부 `ReportRecoveryService`와 Ubuntu local CLI로만 수행한다. 관리자와 사유·고유 명령 ID를 감사한다. 새 슬롯을 얻고 generation/version을 올려 READY로 바꾸되 최초 입력/snapshot과 이전 시도는 유지한다. 같은 명령 재실행은 새 슬롯을 사용하지 않는다.

Outbox는 INSERT만 수행한다. R01~R06의 단계 시험은 직접 Inbox 호출로 전달 경계를 대체했다. 후속 K01~K03은 실제 CDC 요청 topic·commit 뒤 ACK·격리/DLT와 worker를 연결한다. `application-local.yaml`의 worker 기본값은 false이며 관리 앱 launcher만 messaging/worker를 명시 활성화한다. 따라서 직접 컴포넌트 시험과 전체 앱 실행의 조건을 구분한다. UI/브라우저 전체 흐름은 R07이다.

## API

| 요청 | 계약 |
|---|---|
| `POST /api/companies/{companyId}/custom-report-requests` | USER JWT, `Idempotency-Key`, JSON `targetQuarterKey`(생략 가능)/`additionalInstructions`. 커밋 뒤 202/Location/requestId/statusUrl |
| `GET /api/report-requests/{requestId}` | 소유자만 상태/stateVersion/generation/attemptNo/errorCode/retryAt/resultUrl. 내부 경로/입력/stack은 제외 |
| `GET /api/report-requests/{requestId}/file` | 소유자+SUCCEEDED만 PDF, `private, no-store`. 미완료409·타인/부재404·미인증401 |

멱등 키는 ASCII `[A-Za-z0-9._:-]{8,128}`다. 입력은 CRLF를 LF로 바꾸고 양 끝 공백을 제거하며 1~2,000 Unicode code point를 허용한다. 보조 평면 문자는 허용하고 잘못된 surrogate는 거부한다. hash는 요청한 nullable 분기를 포함하므로 기본 분기가 바뀌어도 같은 제출 의도를 재사용할 수 있다. 같은 키로 기업/본문을 바꾸면409다.

정상 수용량은 GLOBAL1000/USER20이며 결정적 경합 시험은5/3이다. 사용자 초과429, 전체 초과 및 새 API의 일시 DB 연결 장애503이다. 기존 일반 보고서 API와 `company_report_versions.pdf_file_id`에 맞춤 PDF를 연결하지 않는다. 게시글 파일 mapping 검사를 유지해 `/api/files` 우회를 차단한다.

## 복구와 파일 정리

`ReportJobRecovery.recover(DB시각)`는 만료된 RUNNING을 회수한다. 늦은 heartbeat/완료/실패는 request/job/attempt ID와 generation/token/version/현재 유효 lease가 모두 맞아야 반영된다. 정상 종료는 새 claim을 차단하고 최대20초 drain 후 중단한다. K03은 모의 HTTP 진행 중 소유 JVM의 강제 종료·lease 재개를 확인하며, 전체 종료 시점별 장애 행렬은 M06에서 확장한다.

`ReportArtifactCleanupService.cleanupOrphans(before)`는 사용자 지정 시각과 DB시각-120초 중 더 이른 시각을 사용한다. canonical UUID 경로만 검사하며 symlink를 따라가지 않는다. 파일마다 별도 짧은 transaction으로 완료와 같은 job 잠금을 얻고 current locking read로 참조/live lease를 재확인한다. 긴 파일 쓰기와 HTTP는 transaction 밖이며 고아 파일 하나의 unlink만 해당 잠금 안에서 수행한다. 파일 쓰기 후 DB 실패는 고아 파일을 남길 수 있지만 결과 확정은 rollback하며 동일 시도의 같은 내용은 안전하게 재사용한다. 파일 시스템과 DB 사이의 전역 transaction을 주장하지 않는다.

claim/heartbeat/실패/복구는 job 잠금 대기 뒤 DB시각을 새로 확인한다. 완료는 마지막 상태 Outbox 쓰기 뒤에도 lease와 시도 제한을 재검사하며 위반하면 파일 메타·슬롯·성공 상태·이벤트를 모두 rollback한다. DB commit/fsync나 OS 호출의 물리적 종료를 정확히20초로 보장하는 의미는 아니다. 그동안 파일 정리와 완료는 같은 job 잠금으로 직렬화한다.

관리 CLI는 다음 형식을 사용한다. 신뢰하는 로컬 운영자가 private DB 자격으로 실행하며 웹 인증을 대신하는 공개 관리 API가 아니다.

```powershell
.\scripts\reliability\recover-report.ps1 -RequestId <UUID> -CommandId <새 UUID> -ActorUserId <관리자 ID> -Reason '원인 제거 후 재개'
```

앱이 실행 중이면 소스 재컴파일을 거부한다. 명령 결과의 requestId/commandId/generation으로 상태와 감사 원장을 대조한다. 재시도할 때는 같은 commandId/사유를 유지하고 새 관리 의도에만 새 commandId를 만든다. SUCCEEDED는 재개할 수 없다.

## 검증 방법과 해석

Windows wrapper가 소스를 Linux 관리 사본으로 동기화한 후 **Ubuntu 안에서** Gradle을 실행한다. native 사본에서의 핵심 명령은 아래와 같다. 전용 local 인프라와 D04 실제 active 자료, A→B 시험에 사용할 D03 materialize/verify 완료 B가 먼저 있어야 한다. 원본 자료와 다른 사용자의 행은 초기화하지 않는다.

```bash
./gradlew realInfraTest --tests '*ReportAcceptanceIntegrationTest' --tests '*ReportInboxIntegrationTest' --tests '*ReportGenerationContractIntegrationTest' --tests '*ReportWorkerLeaseIntegrationTest' --tests '*ReportArtifactIntegrationTest' --tests '*CustomReportApiIntegrationTest' --tests '*ReportManualRecoveryIntegrationTest' --console=plain --no-daemon
./gradlew realInfraTest fastTest --console=plain --no-daemon
```

테스트는 실제 MySQL 제약/잠금/rollback과 실제 모의 HTTP/PDF를 사용한다. R04는 시각/barrier/결과 fake를 사용하고 R05 API의 JWT는 Spring Security 시험 fixture다. K03 별도 앱은 실제 로그인 서명 JWT·HTTP 접수/상태/파일과 Kafka 전달을 연결한다. 브라우저 전체 흐름·실제 AI 정확도·실서비스 성능은 별도 범위다. JDBC SQL 바인딩 로그는 끄되 SQL 계측 callback은 유지한다. 자유 입력 DTO는 API 로그에서 전체 제외한다.

`ReportWorkerProperties`는 `custom-report.worker` 설정을 검증한다. `workers`1~4(기본4), `lease-seconds`30, `heartbeat-seconds`5, `attempt-timeout-millis`20000, `max-attempts`1~3(기본3), `retry-jitter-ms`250, `jitter-seed`17, `recovery-scan-millis`1000이 기본이다. lease는 시도 제한에 heartbeat 두 간격을 더한 시간 이상이어야 한다. 최대4개는 한 프로세스의 executor 상한이다. 외부 DB 장애 중 OS/JDBC 호출 자체의 즉시 종료까지 보장하지 않으며, 유효 lease와 조건부 확정으로 중복 반영을 막는다.
