# 02 맞춤 보고서 요청·워커·결과 구현 계획

> **실행자:** `superpowers:executing-plans`를 적용하고 각 checkbox는 실제 시험 결과를 확인한 뒤 완료한다.

**Goal:** 추가 요청 입력을 보존해 작업을 재실행할 수 있고 본인 결과만 다운로드할 수 있는 최소 보고서 흐름을 완성한다.

**Architecture:** 요청/Outbox 원자 저장, Inbox/job 원자 등록, 짧은 claim과 외부 HTTP, 요청별 파일과 최종 DB 커밋, 명시적인 실패 작업 재개를 사용한다.

**Tech Stack:** 기존 Spring Security/JPA/JDBC·Flyway·WebClient·파일 저장소, JUnit/Testcontainers/MockWebServer, 기존 React/Vite.

**Spec / Global Constraints:** [설계](../specs/2026-11-02-portfolio-reliability-design.md), [공통 계약](2026-11-03-execution-contracts.md), [전체 의존 순서](2026-11-03-execution-index.md). 새 DTO/상태/고유 제약과 수용량 기본값은 공통 계약을 따른다.

**Review Focus:** 커밋 후 응답 유실, A→B 활성화 중 재시도, 만료된 worker lease, 고아 파일, 일반 파일 API를 통한 소유권 우회.

## R01. 요청·Outbox·수용량의 원자 접수

**의존:** D04,E02

**Files:** 생성 V32__create_custom_report_requests_and_outbox.sql 및 H2 대응; J/company/reportrequest/entity/ReportRequestEntity.java, ReportAdmissionEntity.java; J/company/reportrequest/service/ReportRequestService.java, ReportAdmissionService.java; J/company/reportrequest/repository/ReportRequestRepository.java; J/company/reportrequest/model/CustomReportCommand.java, AcceptedReportRequest.java, ReportRequestStatus.java; J/messaging/outbox/OutboxEventEntity.java, OutboxWriter.java, ReportEvent.java, ReportEventType.java; T/company/reportrequest/ReportAcceptanceIntegrationTest.java.

**Interfaces:** accept(userId,companyId,key,CustomReportCommand)->AcceptedReportRequest; OutboxWriter.append(ReportEvent) 참여 트랜잭션 필수; canonical body/key hash는 공통 계약. companyId는 API 경로에서 명시 전달한다.

- [x] **1 — 실패 시험 작성:** 두 사용자·같은 키/다른 키, 100개 동시 동일 키, 같은 키의 다른 본문, snapshot이 바뀐 뒤 응답 유실 재전송, Outbox insert 실패, GLOBAL=5/USER=3 초과 경합과 CRLF 정규화, 2,000/2,001 code point(보조 평면 문자 포함)의 길이 경계를 시험한다.

  기대 assertion: `assertThat(requestRowsForKey).hasSize(1); assertThat(initialOutboxForRequest).hasSize(1); assertThat(replayed.requestId()).isEqualTo(first.requestId())`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*ReportAcceptanceIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 멱등 기존 요청을 먼저 읽고, 신규 접수에만 고정 순서 admission counter 잠금과 슬롯을 적용한다. 같은 DB transaction에서 snapshot/분기/입력 고정, request, 최초 outbox를 커밋한다. 고유 제약 경합은 실패한 transaction 밖에서 새 조회로 수렴한다. DB 커밋 이후 202를 반환하도록 controller/service 경계를 둔다.
- [x] **4 — green/회귀:** 같은 명령으로 동시 경합과 강제 rollback의 카운트/상태/응답이 계약을 만족하는지 확인한다. 정합성은 실제 MySQL에서 판단한다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R02. Inbox와 실행 job의 영속 수신

**의존:** R01

**Files:** 생성 V33__create_report_jobs_and_artifacts.sql 및 H2 대응; J/messaging/inbox/InboxEventEntity.java, ReportJobInbox.java, InboxAcceptance.java; J/company/reportjob/model/JobLease.java; J/company/reportjob/entity/ReportJobEntity.java, ReportAttemptEntity.java, ReportArtifactEntity.java, ReportRecoveryCommandEntity.java; J/company/reportjob/repository/ReportJobRepository.java; T/company/reportjob/ReportInboxIntegrationTest.java.

**Interfaces:** accept(ReportEvent)->InboxAcceptance(ACCEPTED|DUPLICATE); DB 반환 이후 호출자가 ACK 가능. job.requestId unique, inbox(consumer,eventId) unique.

- [x] **1 — 실패 시험 작성:** 같은 event 100회/동시 20회, 다른 eventId가 같은 requestId를 가리키는 경우, inbox insert 성공 후 job insert 실패, DB 커밋 후 실행 신호 누락을 시험한다. raw 메시지 검증/offset 처리는 K02/K03에서 연결한다.

  기대 assertion: `assertThat(inboxForEvent).hasSize(1); assertThat(jobsForRequest).hasSize(1); assertThat(readyJob.persisted()).isTrue()`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*ReportInboxIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 입력 envelope와 DB 요청의 고정 값/hash를 비교하고 inbox+job READY+request ENQUEUED+status Outbox를 같은 transaction으로 커밋한다. duplicate 이벤트는 job을 다시 생성하거나 FAILED job을 재개하지 않는다. 이 단계는 직접 inbox 호출로 DB 계약부터 검증한다.
- [x] **4 — green/회귀:** 같은 명령으로 partial commit 0과 durable READY 회수를 확인한다. 수신 완료와 보고서 생성 완료를 혼동하지 않는다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R03. 고정 데이터 조회와 모의 HTTP 생성 계약

**의존:** R02,D03

**Files:** 생성 J/company/reportjob/port/ReportGenerationPort.java, ReportSourceReader.java; J/company/reportjob/model/ReportSourceData.java, CustomReportInput.java, GeneratedReport.java; J/company/reportjob/adapter/HttpMockReportGenerationAdapter.java; T/company/reportjob/ReportGenerationContractIntegrationTest.java. 수정 infra/mock-ai/server.py, RES/application-local.yaml.

**Interfaces:** load(snapshotId,companyId,targetQuarterKey)->ReportSourceData; generate(source,input,requestId)->GeneratedReport. 새 POST 모의 계약, 원격 Azure GET 재사용 금지.

- [x] **1 — 실패 시험 작성:** A로 접수→B 활성화→최초 실행/재시도에서 AI journal의 sourceSnapshot/값 checksum이 A인지 확인한다. 추가 요청 2개가 서로 다른 input hash로 전달되는지, timeout/5xx/비PDF/잘린 PDF/10MiB 초과를 시험한다.

  기대 assertion: `assertThat(mockCalls).allSatisfy(c -> assertThat(c.snapshotId()).isEqualTo(A)); assertThat(pdfIsValid(generated.bytes())).isTrue()`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*ReportGenerationContractIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 실제 값과 입력을 고정 DTO로 보내고 전체 응답 시간·크기 제한을 둔다. Content-Type만으로 PDF 정상이라고 판단하지 않고 선택한 parser로 유효성 검사한다. 새 adapter에는 자동 재시도를 적용하지 않고 오류를 일시/영구로 분류한다. 필요한 PDF 검사 라이브러리는 버전을 고정하고 범위 제한을 기록한다.
- [x] **4 — green/회귀:** 같은 명령으로 입력 보존과 잘못된 응답 분류를 확인한다. 모의 PDF임을 명시하고 실제 모델 품질 성과로 쓰지 않는다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R04. worker claim·lease·재시도·종료 복구

**의존:** R02,R03

**Files:** 생성 J/company/reportjob/service/ReportJobWorker.java, ReportJobClaimService.java, ReportJobRecovery.java, ReportAttemptService.java, ReportWorkerLifecycle.java; J/company/reportjob/config/ReportWorkerProperties.java; J/company/reportjob/port/ReportResultPort.java; J/company/reportjob/model/CompletionDecision.java; T/company/reportjob/ReportWorkerLeaseIntegrationTest.java; TR/reliability/worker-fault-cases.json.

**Interfaces:** claimReady(limit,owner,now)->List<JobLease>; execute(JobLease); heartbeat(JobLease); recover(now); failOrRetry(lease,reason). 모두 generation/token/version CAS.

- [x] **1 — 실패 시험 작성:** 두 worker 동일 job 경합, claim 직후 종료, heartbeat 중단과 lease 만료, 옛 worker의 늦은 완료, 최초 포함 3회 소진, transient 성공·permanent 실패, 정상 종료의 새 claim 차단을 MutableClock/barrier로 시험한다.

  기대 assertion: `assertThat(successfulClaimsForOneVersion).hasSize(1); assertThat(staleCompletion.updatedRows()).isZero(); assertThat(attemptsPerGeneration).isLessThanOrEqualTo(3)`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*ReportWorkerLeaseIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** DB 시각으로 짧게 claim하고 HTTP/파일 I/O 동안 transaction을 유지하지 않는다. READY/기한 도래 RETRY_WAIT/만료 RUNNING을 회수하고, 고정된 executor 최대 4개를 넘는 작업은 DB에 남긴다. jitter는 injectable seed를 사용한다. 정상 종료는 새 claim을 중단하고 유예 이후 lease 복구로 넘긴다. 완료 저장은 이 작업에서 먼저 정의한 ReportResultPort를 통해 연결하고 검증 fake로 실행한다. 실제 파일/DB 완료는 R05가 해당 port를 구현한다.
- [x] **4 — green/회귀:** 같은 명령으로 불필요한 sleep 대신 시각/신호 제어를 사용하고, 별도 프로세스 kill은 M06에서 실제 재검증한다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R05. 요청별 파일과 결과의 단일 확정·본인 조회

**의존:** R04

**Files:** 생성 J/company/reportjob/service/ReportResultService.java, ReportArtifactCleanupService.java; J/company/reportrequest/controller/CustomReportController.java, ReportRequestQueryController.java; J/company/reportrequest/service/ReportRequestQueryService.java; J/company/reportjob/adapter/LocalReportArtifactStore.java; J/company/reportjob/model/StoredReport.java; T/company/reportrequest/CustomReportApiIntegrationTest.java, T/company/reportjob/ReportArtifactIntegrationTest.java. 수정 J/common/config/SecurityConfig.java(새 경로·CORS 헤더), RES/application-local.yaml.

**Interfaces:** storeAttempt(lease,GeneratedReport)->StoredReport; complete(lease,storedReport); getOwned(userId,requestId); openOwnedFile(userId,requestId); cleanupOrphans(before).

- [x] **1 — 실패 시험 작성:** 파일 쓰기 후 DB 실패, DB 완료 후 응답 유실, 동시에 두 completion, 고아 파일 정리와 아직 사용 중인 파일 보존, 타인/미인증·기존 /api/files·일반 회사 PDF 경로로 우회를 시험한다.

  기대 assertion: `assertThat(finalArtifactsForRequest).hasSize(1); assertThat(otherUserFileResponse.status()).isEqualTo(404); assertThat(activeReferencedFile.exists()).isTrue()`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*CustomReportApiIntegrationTest" --tests "*ReportArtifactIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 요청/시도별 파일을 검증·저장한 뒤 CAS 완료/DB artifact 연결/admission 반환/status Outbox를 한 transaction으로 확정한다. 일반 회사 보고서 버전에 맞춤 PDF를 연결하지 않는다. 기존 게시글 파일 mapping 정책은 유지한다. 고아 정리는 유예와 DB 참조/lease 확인 후 수행한다. 새 API에서 소유권·상태·no-store·오류 응답을 적용하고 body 로그를 마스킹한다.
- [x] **4 — green/회귀:** 같은 명령으로 유효 PDF 스트리밍과 단일 결과/슬롯 반환, 접근 차단을 확인하고 기존 인증/파일/일반 보고서의 영향 범위 회귀를 실행한다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R06. 최종 실패의 명시적 관리 재개

**의존:** R05

**Files:** 생성 J/company/reportjob/service/ReportRecoveryService.java, J/company/reportjob/cli/ReportRecoveryCommand.java, scripts/reliability/recover-report.ps1; T/company/reportjob/ReportManualRecoveryIntegrationTest.java. 감사 이력은 V33의 recovery_commands 사용.

**Interfaces:** recover(actorUserId,requestId,recoveryCommandId,reason)->RecoveryResult. DB에서 ACTIVE/ROLE_ADMIN을 확인하는 관리자 전용 local CLI와 내부 service다. 공개 관리 화면/일반 사용자 재개 API는 만들지 않는다.

- [x] **1 — 실패 시험 작성:** FAILED에 같은 event를 재전달해도 job이 재개되지 않음, 같은 관리 명령 10회/동시 10회, quota 부족, SUCCEEDED 재개 거부, 이전 generation의 늦은 완료와 실패 이력 보존을 시험한다.

  기대 assertion: `assertThat(generationAfterRepeatedCommand).isEqualTo(previous+1); assertThat(admissionAcquiredForCommand).isEqualTo(1); assertThat(history).containsAll(previousAttempts)`
- [x] **2 — red 확인:** `.\gradlew.bat realInfraTest --tests "*ReportManualRecoveryIntegrationTest" --console=plain`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 관리 주체/사유를 감사하고 FAILED CAS와 슬롯 획득·generation 증가·재시도 예산 초기화·READY를 한 transaction에 커밋한다. 완료 결과와 원문/snapshot은 바꾸지 않는다. commit 후 wakeup이 누락돼도 recovery scan이 실행한다. job 미등록 DLT의 수정 재전달은 K02의 별도 절차로 처리한다.
- [x] **4 — green/회귀:** 같은 명령으로 원인 제거 후 한 FAILED 요청이 실제로 완료되는지 확인하고 원래 시도와 관리 재개 시도를 구분해 기록한다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

## R07. 최소 화면·실제 인증·다운로드 전체 흐름

**의존:** K03,R05,R06

**Files:** 생성 F/src/components/companyDetail/CustomReportRequestPanel.tsx, F/tests/custom-report.spec.ts, F/playwright.config.ts; 수정 F/src/pages/companies/CompanyDetail.tsx, F/src/api/client.ts, F/src/api/companies.ts, F/package.json(dev 시험 의존성), F/package-lock.json. F/AGENTS.md와 context를 먼저 읽는다.

**Interfaces:** 추가 요청 입력→한 제출 의도의 UUID key 유지→202 requestId 상태 조회→완료 PDF 다운로드. API helper에 요청별 추가 헤더를 지원한다.

- [x] **1 — 실패 시험 작성:** 서로 다른 두 실제 시험 계정으로 정상 흐름·더블클릭·응답 유실 후 같은 키 재전송·수정 입력의 새 키·401/404/409/429/503 표시·새로고침 후 추적·타인 PDF 차단을 브라우저에서 시험한다.

  기대 assertion: `await expect(requestIdsForDoubleClick).toHaveLength(1); await expect(downloadedPdf.signature).toEqual("%PDF-"); await expect(otherUser.status).toEqual(404)`
- [x] **2 — red 확인:** `npm.cmd run test:e2e -- --project=chromium custom-report.spec.ts`. 기대 불변조건이 깨지는 실패와 환경 부재를 구분한다.
- [x] **3 — 최소 구현:** 기존 보고서 버튼을 유지하고 최소 추가 입력·진행·실패/재시도 대기·완료 링크만 추가한다. raw HTML로 추가 요청을 렌더링하지 않는다. 수락 요청 key/requestId를 해당 사용자 세션에 보존하고 새 제출 의도에는 새 키를 발급한다. 실제 백엔드/JWT/CORS/Redis를 연결하며 UI만 mock해 전체 연동이라고 하지 않는다.
- [x] **4 — green/회귀:** 같은 명령으로 브라우저에서 입력과 결과를 확인하고 npm.cmd run build 및 작은 화면 overflow/기존 화면 회귀를 확인한다. 공개 배포는 하지 않는다.
- [x] **5 — 증거/검토:** request/event/job/attempt/generation별 결과·카운트·실패 시점과 명령/소스 hash를 run 디렉터리에 남기고 변경 범위를 검토한다.

완료 2026-11-12: 원본프런트를보존하고frontend/에복원,Ubuntu/Chromium실제JWT·CORS·Kafka·모의PDF/본인다운로드·타인404·응답유실같은키/새로고침/새의도/중복클릭1HTTP·390px입력패널을검증했다. 저장/인증정리예외4개RED→GREEN뒤최종13/13/build통과. 초기로그인대기초과1회는원인미확정으로보존하며후속API200/전체통과와구분한다. G2는기존K03강제종료복구근거와함께통과. 외부AI/배포/전체UI재설계는제외. [실행조건](../../runtime/browser-and-measurement.md),결과 (로컬 자료·Git 제외: `../../results/browser-and-cache.md`).
