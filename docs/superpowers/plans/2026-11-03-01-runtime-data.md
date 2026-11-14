# 01 실행 환경·실제 데이터 적재 구현 계획

> **실행자:** `superpowers:executing-plans`로 작업별 실패 재현→구현→검증을 진행한다. 체크박스는 실행 후 증거를 확인했을 때만 완료한다.

**Spec:** [설계](../specs/2026-11-02-portfolio-reliability-design.md) / [실행 순서](2026-11-03-execution-index.md) / [공통 계약](2026-11-03-execution-contracts.md)

**Global Constraints:** 경로 약어, API·고유 키·상태·시간·시험 기본값·후속 범위는 공통 계약을 따른다. 모든 명령은 대상 파일/task를 생성한 뒤 실행한다.

**Goal:** 실행 가능한 로컬 구성과 재현 가능한 데이터 snapshot을 만든다.

**Architecture:** 격리된 MySQL/Redis/Kafka/모의 서버, 원본 manifest와 canonical staging, chunk checkpoint, 보고서 버전의 snapshot 범위를 사용한다.

**Tech Stack:** 기존 Gradle/Boot, Docker Compose/Testcontainers, POI/JDBC/Flyway. 실제 버전은 E01~E03에서 고정한다.

**Review Focus:** 실행 태그로 누락된 실제 인프라 시험, 비결정적 chunk 순서, 부분 적재를 읽는 기존 max-version 쿼리, 서로 가리는 13+5 지표.

## E01. 실행 기준선과 시험 task 분리

**의존:** 없음

**Files:** 수정 B/build.gradle; 기존 T/report/importer/ExcelMetricParserTest.java로 baseline 검증; 생성 scripts/reliability/verify-test-results.ps1, docs/runtime/dependency-lock.md. 생성 기록 위치 perf/results/<runId>/manifest.json.

**Interfaces:** fastTest/realInfraTest/cdcTest/reliabilityTest/verifiedCheck와 verify-test-results.ps1이 suite/pass/fail/skip/reason을 출력한다.

- [x] **1 — 실패 시험 작성:** 기존 suite의 외부 자원 사용·태그·Disabled 이유를 조사한다. 기존 ExcelMetricParserTest로 Ubuntu 실행 위치의 class loading 여부를 확인하고 동일 소스의 격리 실행 사본을 비교한다. 과거 Windows 실패가 현재 Ubuntu에서 재현되지 않으면 재현되지 않았다고 기록한다. task 분류는 실제 결과 XML과 선택/누락 suite 목록으로 확인하며 설정을 그대로 복제하는 단위 시험을 추가하지 않는다.

  기대 assertion: `XML: executedTests > 0, failures == 0; source-copy SHA-256 mismatches == 0; excluded suites/reasons explicitly listed`
- [x] **2 — 실패 확인:** `./gradlew fastTest --tests "*ExcelMetricParserTest" --console=plain`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 기존 test 옵션을 유지한 채 공통 계약대로 task와 결과 디렉터리를 추가한다. `dependencyInsight --dependency spring-kafka --configuration runtimeClasspath`, Java/Gradle 정보, 소스 hash/dirty diff 목록을 저장한다. 한글 경로 문제가 남으면 작업 파일을 보존하는 영문 실행 snapshot으로 재현성을 고정하고 원본과 hash를 대조한다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 동일 소스의 단일 시험이 실제로 실행되고 task 선택/제외 목록이 기록돼야 한다. 전체 초기 회귀는 환경 준비 후 E02에서 재실행한다.
- [x] **5 — 증거/검토:** 기존 fast 회귀 결과와 실행 경로/소스 hash/의존성 출력을 저장한다. 과거 384 통과를 현재 결과로 복사하지 않는다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다.

**E01 실행 결과 (2026-11-03):** Ubuntu 원본 parser 1/1, fast parser 1/1, 전체 fast 399개 중 384 통과·15 skip·실패/error 0. 소스 불일치 0. 6개 Python 결과/지문 검증 시험 통과. CDC/reliability는 빈 결과로 전체 검증기 exit 2. 실제 인프라·앱 기동은 E02 미수행. 세부 조건과 누락 사유는 [실행 기준선](../../runtime/dependency-lock.md), `perf/results/20261103-e01-wsl/manifest.json`에 기록한다. Windows 콘솔 로그 일부는 휘발돼 초기 XML과 이후의 영구 로그를 구분해 보관했다. 커밋·push 없이 격리 작업 사본에 보존한다. 독립 검토의 보고 기능 Minor 2건(polling CDC N/A 자동 표기, 실패 의존 task의 통합 요약 갱신)은 실행 기준선 문서에 보류로 기록했다.

## E02. 실제 인프라와 loopback 모의 AI 실행

**의존:** E01

**Files:** 생성 B/docker-compose.portfolio.yml, RES/application-local.yaml, scripts/reliability/local.ps1, infra/mock-ai/server.py, infra/mock-ai/Dockerfile, infra/mock-ai/requirements.lock, T/infrastructure/PortfolioRuntimeIntegrationTest.java, T/support/PortfolioIntegrationSupport.java; 수정 B/.env.example, B/build.gradle(Testcontainers MySQL/Kafka BOM 관리 모듈).

**Interfaces:** local.ps1 up|verify|start-app|stop-app|down. RuntimeFixture가 MySQL/Redis/Kafka와 테스트용 HTTP base URL을 제공한다. Mock API는 /health, /control, POST /test-ai/custom-reports.

- [x] **1 — 실패 시험 작성:** MySQL V1~V27 기동, Redis TTL, Kafka 실제 send/consume, 모의 서버 HTTP와 SMTP 비활성 상태를 확인하는 시험을 만든다. 3306/6379/9092 기존 PC 서비스를 바꾸지 않도록 새 포트를 사용한다. Kafka 중단 시에도 API JVM이 기동 가능한지 확인한다.

  기대 assertion: `assertThat(runtime.verify()).containsEntry("mysql", "UP").containsEntry("redis", "UP").containsEntry("kafka", "UP")`
- [x] **2 — 실패 확인:** Ubuntu에서 `./gradlew realInfraTest --tests "*PortfolioRuntimeIntegrationTest" --console=plain`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 개발 DB 이름 sentinel_portfolio, Compose project sentinel-portfolio, localhost 전용 새 포트, 독립 named volume을 사용한다. 키/pepper는 로컬 비밀 경로에서 읽고 출력하지 않는다. 이미지 후보는 현재 계열에서 시작해 태그/digest를 잠근다. 모의 서버는 seed별 유효 PDF와 호출 journal, 지연/오류 제어를 지원하고 평상시 외부 네트워크를 쓰지 않는다. 준비 실패 시 실제 인프라 완료를 표시하지 않는다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 실제 I/O와 local.ps1 기동/종료/재기동이 통과해야 한다. 서비스 경로 health는 이후 schema/job 구현 후 재검증한다.
- [x] **5 — 증거/검토:** 구성 요소 버전/digest·호스트 자원·연결 성공·기존 자원 비변경 확인과 초기 회귀 결과를 저장한다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다.

**E02 실행 결과 (2026-11-03):** 실제 인프라 19/19(신규 4개 실제 MySQL/Redis/Kafka/HTTP, 기존 인증 15개 H2+Redis Testcontainer), fast 384 통과·15 skip·실패 0, Python 도구 11/11·mock 4/4. 정상/Kafka 중단 시 API 기동, wrapper up/verify/start-app/stop-app/down 및 재기동 검증. MySQL 이력 27행·Redis marker·mock journal 재생성 후 보존 확인. 독립 검토 Important 2건(환경 격리, 삭제 소스 동기화)을 회귀 시험 RED→GREEN으로 수정했다. 상세는 [환경 문서](../../runtime/local-environment.md)와 `perf/results/20261103-e02-runtime/manifest.json`. E03와 실제 자료 적재는 미수행이다.

## E03. CDC 우선 검증과 발행기 선택 잠금

**의존:** E02

**Files:** 생성 infra/cdc/portfolio-probe.json, infra/cdc/mysql-cdc.cnf, scripts/reliability/probe-cdc.ps1, T/infrastructure/CdcProbeIntegrationTest.java, docs/adr/0001-outbox-relay.md; 수정 docker-compose.portfolio.yml(profile cdc).

**Interfaces:** probe-cdc.ps1 -RunId <id> → cdc-decision.json(relayMode, lockedImages, prerequisites, tests, rejectedReasons).

- [x] **1 — 실패 시험 작성:** 작은 probe Outbox에서 commit 10건/rollback 10건, 커넥터 중단 중 commit 후 재개, 브로커 재시작, 동일 eventId 유지와 offset/history 영속화를 시험한다. 아직 업무 Inbox가 없으므로 이 시험에서 업무 중복 방지를 주장하지 않는다.

  기대 assertion: `assertThat(observedCommittedEventIds).containsAll(committedIds).doesNotContainAnyElementsOf(rolledBackIds)`
- [x] **2 — 실패 확인:** Ubuntu에서 `./gradlew cdcTest --tests "*CdcProbeIntegrationTest" --console=plain`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** ROW/FULL binlog, server-id, 최소 권한, Connect 내부 토픽·history와 버전 호환성을 설정한다. 실제 부재/권한/자원 제약과 잘못된 설정을 구분해 해결 시도한다. 통과하면 relay.mode=cdc, 운영 불가 근거가 확인되면 relay.mode=polling을 기록한다. 임시 네트워크 장애만으로 대안을 고르지 않는다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 선택 결과 하나가 확정돼야 한다. 테스트가 실제 실행되지 않았다면 선택을 보류하고 자원 문제를 해결한다.
- [x] **5 — 증거/검토:** probe 로그·이벤트 ID·버전·커넥터 재개 지점·선택 이유를 저장한다. CDC 불가면 실패 근거와 K01P 실행 조건을 남긴다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다.

**E03 실행 결과 (2026-11-03):** CDC 선택. Debezium3.4.3.Final/Connect4.1.1/Kafka3.7.0/MySQL8.0.46 조합에서 CDC3/3, 복구 guard 단위2/2, Python17/17·mock4/4, 기존 realInfra19/19 통과. 초기snapshot1+정상commit10+worker 중단commit5+broker 중단commit5=21개 고유 eventId 수신, rollback10개 미전달. history/offset 재개·초기snapshot 미반복 확인. 추가 중복은 이 유한 실행에서0건 관찰. 독립 검토의 잠금 충돌·부분 정지 실패 복구 지적을 RED→GREEN으로 수정했다. [ADR](../../adr/0001-outbox-relay.md), `perf/results/20261103-cdc-04/cdc-decision.json`, `perf/results/20261103-e03-cdc/manifest.json`에 기록했다. 업무 Outbox/Inbox·EventRouter와 D01 이후 실제 자료 검증은 아직 미수행이다.

## D01. 원본 manifest·canonical parser·사전 검증

**의존:** E01; 전체 파일 검증은 E02 이후

**Files:** 생성 J/dataimport/model/ImportDatasetCommand.java, CanonicalMetricCell.java, ImportManifest.java; J/dataimport/service/ImportPreflightService.java, MetricDecimalPolicy.java; T/dataimport/ImportPreflightTest.java; 수정 J/report/importer/ExcelMetricParser.java, J/report/util/ExcelIndustryMetricParser.java, J/company/util/ExcelCompanyIndustryParser.java(필요한 정규화만).

**Interfaces:** preflight(ImportDatasetCommand) -> ImportManifest+canonical sorted cells+errors. 키=(stockCode,quarterKey,metricCode,valueType), provenance=(fileHash,sheet,row,col).

- [x] **1 — 실패 시험 작성:** 원본을 읽는 표본과 작은 생성 fixture를 사용한다. 000020/영문 포함 종목 코드/빈 코드·null/0/NULL/20253-3=20244/퍼센트 형식/중복 같은 값·다른 값/scale 반올림·overflow를 시험한다. CSV와 XLSX는 canonical scale 기준으로 비교한다.

  기대 assertion: `assertThat(normalize("0008Z0")).isEqualTo("0008Z0"); assertThat(offset(20253,-3)).isEqualTo(20244); assertThat(decimal(null)).isNull()`
- [x] **2 — 실패 확인:** Ubuntu의 `bash ./gradlew fastTest --tests "*ImportPreflightTest" --console=plain --no-daemon`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 기업·업종·13+5 파일을 하나의 fingerprint로 묶고 원본은 변경하지 않는다. parser/정밀도 정책 버전을 manifest에 넣는다. 서로 다른 값의 동일 키는 오류로 격리하고 같은 값 중복은 출처와 함께 보고한다. DL에너지의 코드 누락을 임의 매핑하지 않는다. 완전한 원본 검사는 file hash가 일치하는 경우만 이전 프로파일과 비교한다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 모든 경계 입력이 계약대로 분류돼야 하며 숫자 0을 NULL로 바꾸지 않아야 한다.
- [x] **5 — 증거/검토:** preflight.json, source-manifest.json, invalid-rows.csv, canonical 값 checksum을 저장한다. 값 없는 위치와 잘못된 행을 구분한다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다.

**D01 실행 결과 (2026-11-04):** 신규36/36, 전체fast437개 중422통과·기존15skip·실패/error0. 실제6파일/유효2083기업의149976위치(숫자141320/NULL8656/숫자0은1383)를 파싱했다. CSV↔XLSX 및 독립 Python CSV/Decimal 전체 키·값 대조에서 missing/extra/different 각각0, 원본hash변경0. DL에너지 한 기업의 코드 누락5위치와 nullable 업종명13경고를 기록했고 전체 원본 ready=false를 유지한다. 독립 검토2Important·1Minor(구조 검증 목적에 따라 Important로 재평가) 및 작성자 발견 nullable 업종명 문제를 Ubuntu 실패 재현 후 수정했다. 기존 업로드 API를 바꾸지 않고 별도 strict reader/service/CLI를 추가했다. 사용/정책 (로컬 자료·Git 제외: `../../data/preflight.md`), 포트폴리오 참고용 (로컬 자료·Git 제외: `../../portfolio/포트폴리오%20참고용.md`), `perf/results/20261104-d01-preflight/manifest.json`. D02 적재/DB대조·성능 측정 미수행.

## D02. 적재 원장·staging·chunk 재시작

**의존:** D01,E02

**Files:** 생성 RES/db/migration/V28__create_import_journal.sql 및 migration-h2 대응; J/dataimport/entity/{DataSnapshotEntity,ImportRunEntity,ImportChunkEntity,ImportStageValueEntity,ImportErrorEntity}.java; J/dataimport/repository/ImportJournalRepository.java; J/dataimport/service/ImportRunService.java, ImportChunkService.java; T/dataimport/ImportResumeIntegrationTest.java.

**Interfaces:** start(command) -> ImportRunId; stageNext(runId) -> ChunkResult; resume(runId); status(runId) -> persisted counts. stageNext는 별도 Spring bean의 짧은 트랜잭션이다.

- [x] **1 — 실패 시험 작성:** 100개 canonical cell/10개 chunk fixture에서 6번째 chunk 커밋 직전·직후 종료를 각각 주입한다. 같은 논리 파일 재업로드, 두 실행자의 동일 chunk 경합, 일시 DB 오류 후 재시도를 시험한다. 진행 기록을 메모리 변수로만 복구하지 않는다.

  기대 assertion: `assertThat(afterResume.canonicalChecksum()).isEqualTo(cleanRun.canonicalChecksum()); assertThat(committedChunkNumbers).doesNotHaveDuplicates()`
- [x] **2 — 실패 확인:** Ubuntu에서 `bash ./gradlew realInfraTest --tests "*ImportResumeIntegrationTest" --console=plain --no-daemon`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 고정 정렬·chunk 번호·unique key를 사용하고 stage 데이터+checkpoint를 같은 트랜잭션에 커밋한다. 실패한 트랜잭션을 계속 쓰지 않고 바깥 재시도에서 새 트랜잭션을 연다. run 이력과 논리 snapshot을 구분해 재업로드가 새 업무 데이터를 만들지 않게 한다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 clean과 resume의 키/값이 동일하고 커밋 성공분이 중복 적재되지 않아야 한다.
- [x] **5 — 증거/검토:** 중단 지점·커밋 chunk·재처리 chunk·중복/결측/DB 오류·재시도 횟수와 clean/restart checksum을 저장한다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다.

**D02 실행 결과 (2026-11-04):** V28 MySQL/H2 및 JDBC 원장/준비 payload/staging/checkpoint·재시도·명시 resume 구현. 합성100개/10chunk에서6번째 commit전/후 실제 JVM halt91/92 뒤50/60행 확인, 새 JVM에 runId만 넘겨 각100행/10checkpoint 재개. 독립 MySQL CLI 전체 값 대조 missing/extra/different0. D02 MySQL15개와 격리정책4개 통과(최종전체범위에 포함), 전체realInfra34/34(기존인증H2+Redis15/환경4 포함), 전체fast441개 중426pass·기존15skip·실패/error0. 실제1205재시도·4시도소진, 동시접수/적재, 동일runFAILED경합, 감사불가의 RecoveryRequired, 오류목록누락 정책우회 회귀 포함. 독립 Important3를 RED→GREEN으로 보강했고 추가 재검토 없음. DB 감사 불가 시 다른 JVM 후속 차단을 보장하지 않음. 준비중단은 예외 주입/같은입력 재접수이며 STAGED는VERIFIED/ACTIVE아님. 전체실제149976개적재·성능측정·D03/D04 미수행. 정책/명령 (로컬 자료·Git 제외: `../../data/import-journal.md`), 참고용 (로컬 자료·Git 제외: `../../portfolio/포트폴리오%20참고용.md`), `perf/results/20261104-d02-import/manifest.json`.

## D03. 보고서 버전 materialization·활성 snapshot 조회

**의존:** D02

**Files:** 생성 V29__scope_report_versions_to_snapshot.sql, V30__scope_metric_averages_to_snapshot.sql 및 H2 대응; J/dataimport/service/SnapshotMaterializationService.java, SnapshotVerificationService.java, SnapshotActivationService.java; J/dataimport/repository/SnapshotReportRepository.java, SnapshotQueryScope.java; 수정 J/report/entity/CompanyReportVersionsEntity.java, J/report/repository/CompanyReportMetricValuesRepository.java, CompanyReportMetricValuesRepositoryImpl.java, CompanyReportVersionsRepository.java, J/report/service/CompanyReportMetricImportService.java 및 직접 실제값/최신분기·평균·위험 summary를 읽는 호출부; 생성 T/dataimport/SnapshotVisibilityIntegrationTest.java, SnapshotActivationCrashProbe.java. V30 평균 출처 격리에 따라 미적용 후속 migration 예약은 V31~V35로 이동했다.

**Interfaces:** materialize(snapshotId, companyId, quarterKey); verify(snapshotId); activate(snapshotId); resolveActiveOrLegacy(family); load(snapshotId,companyId,quarterRange).

- [x] **1 — 실패 시험 작성:** A 활성 상태에서 B의 일부 기업/13지표만 materialize하고 현재 개요·최신 분기·관심기업/대시보드를 조회한다. B 검증 실패/활성화 직전 종료, 13+5 입력 순서 반전, A를 명시해 읽는 재시도를 시험한다.

  기대 assertion: `assertThat(currentOverview()).isEqualTo(snapshotAOverview); assertThat(explicitRead(A)).isEqualTo(snapshotAOverview); assertThat(activatedB.metricCodes()).containsAll(expected18Codes)`
- [x] **2 — 실패 확인:** Ubuntu에서 `bash ./gradlew realInfraTest --tests '*SnapshotVisibilityIntegrationTest' --console=plain --no-daemon`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 먼저 current read와 max-quarter/version 쿼리에 snapshot 범위를 적용해 staging 노출을 막는다. snapshot/기업/분기 bridge에 고정 reportVersion을 생성하고 양쪽 지표를 합쳐 넣는다. ACTIVE pointer만 원자적으로 바꾸고 이전 snapshot을 남긴다. 새 Spring service 진입점 importSnapshot을 이 경로로 보내고 CLI는 D04가 사용하며 기존 업로드/일반 보고서/PREDICTED/PDF 경로는 회귀 시험으로 보존한다. status가 VERIFIED가 아니거나 격리 검증이 실패한 데이터셋은 활성화하지 않는다. 평균 source tag·현재 위험 summary 범위·명시 과거 위험 버전 읽기도 구분한다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 부분 데이터 미노출·전환 원자성·legacy fallback·기존 경로 회귀가 통과해야 한다.
- [x] **5 — 증거/검토:** 전환 전/중/후 응답을 만드는 service·repository 결과(개요 JSON 직렬화 포함), version bridge, 18개 코드·4분기 키와 latest-quarter 대조를 저장한다. 변경 파일과 영향 범위를 검토하고 작업 로그에 다음 의존 작업을 기록한다. HTTP/브라우저 E2E 전체를 시험한 것으로 표현하지 않는다.

**D03 실행 결과 (2026-11-04):** V29 보고서 고정 bridge/포인터와 V30 평균 출처(MySQL/H2), 그룹 materialize·전체 대조·VERIFIED·원자 활성화·과거 명시 읽기·신규 Spring service 진입점을 구현했다. 합성2기업×18코드×4분기144개 기준 D03 실제MySQL14/14, 최종전체 realInfra48/48(D02 15+D03 14+환경4+인증H2/Redis15), fast441개 중426pass·기존15skip·실패/error0. child JVM COMMIT전 halt94에서 종전 pointer/generation 보존 후 재시도 성공, 독립MySQL CLI A/B 각144개·8bridge 대조 missing/extra/different0, 편집본/Linux 코드불일치0. Important3(평균 출처/위험 summary/과거 위험 버전)는 RED→GREEN으로 보강했으며 PDF finder Minor1은 보류하고 추가 재검토는 하지 않았다. 처음 전체 fast의 heap 포화·지속GC는 소유worker 중단143으로 보존하고 context cache16/fork1/heap512MiB로 전체 재실행해 성공했다. 평균은 출처 불일치 시NULL이며 과거 평균 이력 보존은 하지 않는다. 전체실제149976개/원천사실/성능·CDC/reliability 전체gate는 미수행. 사용/한계 (로컬 자료·Git 제외: `../../data/snapshot-activation.md`), 참고용 (로컬 자료·Git 제외: `../../portfolio/포트폴리오%20참고용.md`), `perf/results/20261104-d03-snapshot/manifest.json`(final-regression-02). 다음 D04.

## D04. 전체 실제 데이터 대조와 원천 정확성 보고

**의존:** D03

**Files:** 생성 J/dataimport/service/ImportVerificationService.java, ImportMasterService.java; J/dataimport/repository/ImportReconciliationRepository.java, ImportMasterRepository.java; J/dataimport/model/ImportVerificationResult.java; J/dataimport/cli/ImportCommand.java; scripts/reliability/data_cli.py, import-data.ps1, verify-import.ps1; T/dataimport/ImportReconciliationIntegrationTest.java; docs/data-quality-results.md(로컬 참고용 Git 제외). 수정 ImportPreflightCommand.java의 dataset helper, build.gradle의 prepareImportCli, SnapshotReportRepository.java의 실제 전체 데이터 출처 조인.

**Interfaces:** verify(snapshotId[,rawSource]) -> ImportVerificationResult(expected/actual key counts,nulls,differences,quarantine,checksum). DB_PAYLOAD와 RAW_FILES를 구분한다. CLI는 local profile의 별도 최소 Spring/JDBC JVM에서 동일 DB를 사용하고 명령만 실행 후 종료한다. 웹·AI·Kafka·메일은 시작하지 않는다.

- [x] **1 — 실패 시험 작성:** 누락 값 1개/중복 1개/잘못된 분기 1개/NULL→0/소수점 정책 위반을 의도적으로 만든 데이터에서 각각 탐지되는지 시험한다. 오류 원인을 제거한 정상 fixture와 실제 파일 full-join/anti-join 대조가 통과하는지 확인한다.

  기대 assertion: `assertThat(verify(corrupted).unexpectedDifferenceCount()).isGreaterThan(0); assertThat(verify(valid).unexpectedDifferenceCount()).isZero()`
- [x] **2 — 실패 확인:** Ubuntu에서 `bash ./gradlew realInfraTest --tests '*ImportReconciliationIntegrationTest' --console=plain --no-daemon`. 기능 부재/기대 불변조건 실패를 확인한다. 인프라가 없어서 실행하지 못한 것은 기능의 red 증거와 구분한다.
- [x] **3 — 최소 구현:** 기업 CSV→업종→13+5 입력을 CLI로 실행하고 DB 전체 canonical 키·값과 원본 checksum을 대조한다. 데이터량은 현재 manifest에서 재계산한다. DART 보관 원문과 재계산 가능한 표본은 별도 표시하고 산식/분모가 없는 값은 사실 정확성 미검증으로 남긴다. 성능 기록에는 stage/materialize/verify 구간을 분리한다.
- [x] **4 — 검증:** 같은 명령을 재실행하고 설명되지 않는 차이 0인 snapshot만 G1 통과다. 원천 사실 정확성의 미검증 범위를 명시한다.
- [x] **5 — 증거/검토:** 전체 적재 run, 정상 재적재, 중단 재개 각각의 건수·NULL·격리·소요시간·retries·키/값 불일치를 보관한다. 실패 attempt의 미기록 구간 시간은 N/A로 남기며 성공 재개의 stage 확인 시간을 전체 신규 insert 시간으로 표시하지 않는다. 변경 파일과 영향 범위를 검토하고 다음 의존 작업을 기록한다.

**D04 실행 결과 (2026-11-04):** G1 파일→DB 정합성 통과. 실제 기업마스터3942/업종연결2083/재무149976개(NULL8656/0값1383),8332고정버전·bridge를 적재했다. 120/300chunk=60000개에서 소유CLI JVM SIGKILL(-9) 후 같은runId를 새JVM에 넘겨 전체stage를 완성했다. 첫resume의검증timeout은ASCII/UTF8mb4출처join이PK첫열만사용하는것을확인해비교값을ASCII/bin으로맞췄고, 수정후같은runId로VERIFIED/활성화했다. 정상재접수는새run/같은snapshot·149976값·8332버전을유지했다. RAW_FILES 재검증과독립Python CSV/OOXML/Decimal versusMySQL 전체대조3회 모두 missing/extra/different/duplicate0·checksum일치,원본6hash변경0·소스동기화불일치0. 신규MySQL17/17,최종전체realInfra65/65(인증H2/Redis15+환경4+D02 15+D03 14+D04 17),fast441중426pass/기존15skip/실패error0,Python17/17·mock4/4. 독립Important1+원본보존상Important로재평가한symlink1을RED→GREEN으로보강했고실제Windows전달역슬래시소실도수정했다. 새classpath의존성추적Minor1은보류/변경시prepareImportCli --rerun-tasks필요,기존D03 Minor1/E01 Minor2유지·재검토없음. 3S의재계산4개는targetwide미포함,삼성2018/2021도대상밖이므로전체원천재무사실은미검증. 첫실패의미기록구간시간/전체cold insert·일반API TPS/전원차단/전체verifiedCheck를완료로표현하지않는다. 결과/명령 (로컬 자료·Git 제외: `../../data-quality-results.md`),참고용 (로컬 자료·Git 제외: `../../portfolio/포트폴리오%20참고용.md`), `perf/results/20261104-d04-reconciliation/manifest.json`. 다음 M01 계측 검증/조회 MySQL 기준선.
