# 04 계측·성능·캐시·장애 실험 실행 계획

> **실행자:** `superpowers:executing-plans`로 진행한다. M01/M02는 새 맞춤 기능 전에도 실행하고 M03은 K03/R07 통과 후 실행한다.

**Goal:** 같은 조건의 before/after와 강제 장애 후 DB 대조에서 재현 가능한 수치를 얻고 대표 3사례의 증거를 완성한다.

**Architecture:** 독립 계측기 검증, 실제 MySQL 기준선, durable 요청 흐름 기준선, 한 조회 캐시 실험, 운영/장애 시험, raw 기반 결과 생성 순서다.

**Tech Stack:** 기존 Micrometer/Actuator/P6Spy/JMeter, PowerShell/Python 표준 CSV·JSON 처리, 실제 인프라와 모의 HTTP.

**Spec / Global Constraints:** [설계](../specs/2026-11-02-portfolio-reliability-design.md), [공통 계약](2026-11-03-execution-contracts.md), [측정 프로토콜](2026-11-03-measurement-protocol.md), [실행 순서](2026-11-03-execution-index.md).

**Review Focus:** 성공 202와 업무 완료의 혼합, 실패 응답을 제거한 과도한 성능 주장, last start timestamp 기반 TPS 계산 오류, 한 가지 변경이 아닌 before/after, 누락 0이지만 영원히 대기하는 작업.

## M01. 측정기와 원자료 계산기 검증

**의존:** D04,E01

**Files:** 생성 J/perf/PortfolioMetrics.java, QueryCountProbe.java; scripts/perf/Start-PortfolioRun.ps1, Invoke-ReadBenchmark.ps1, Invoke-ReportBenchmark.ps1, Summarize-PortfolioRun.py; T/perf/PortfolioMetricsTest.java; scripts/perf/tests/test_summary.py; perf/fixtures/summary-samples.csv; RES/application-portfolio-perf.yaml. 기존 compare.sh는 보존하고 새 실험에서 새 계산기를 사용한다.

**Interfaces:** run manifest 생성, label별 요청 CSV, lifecycle/runtime/fault journal, summarize(runDir)->summary.json/md. metric label은 endpoint/상태 등 제한된 집합만 사용한다.

- [x] **1 — 실패 시험/실험 작성:** 알려진 CSV로 성공 percentile, 빠른 실패 응답, warmup 제외, quoted CSV, 빈 표본/0분모/불완전 측정, 완료/접수 분리와 TPS 구간을 시험한다. start0/elapsed100 및 start100/elapsed100인 2개 성공 요청이면 관찰 구간200ms/TPS10인지 고정한다.

  기대 assertion: `assert summary.duration_ms == 200; assert summary.success_tps == 10; assert percentile([10,20,30,40,50],0.95) == 50`
- [x] **2 — red/기준선 확인:** `python -m unittest discover -s scripts/perf/tests -p "test_*.py"`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [x] **3 — 최소 구현/측정:** 기존 awk의 고정 열 위치·마지막 시작 시각 기반 구간을 재사용하지 않고 헤더 기반 csv parser로 계산한다. SQL query count는 현재 P6Spy callback/검증 가능한 datasource 계측에 연결하고 health/준비/수집 쿼리를 분리한다. 계측을 before/after 모두 같게 켜고 민감정보를 raw에 남기지 않는다. 실행 스크립트는 실제 config/소스/data fingerprint를 수집하며 없는 값을 0으로 채우지 않는다.
- [x] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 계산기 golden tests와 .\gradlew.bat fastTest --tests "*PortfolioMetricsTest"가 통과해야 한다. 이후 새 workflow에서 emit하는 lifecycle을 M03에서 확장한다.
- [x] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

완료 2026-11-05: 앱/Gradle/부하/Python은 Ubuntu에서 실행했고 본문의 Windows Gradle 예시는 `bash ./gradlew`로 대응했다. 최종 도구29+환경19 통과, 실제 JDBC callback/HTTP 범위·CSV 계산 검증. 독립 검토 Important2건을 RED→GREEN 보강하고 실제 SELECT 전용 권한/가입 복구를 확인했다. M03 업무 lifecycle은 미구현으로 분리한다.

## M02. 기존 조회의 MySQL 기준선·한 SQL 개선

**의존:** M01,D04

**Files:** 생성 perf/jmeter/portfolio-read.jmx, scripts/perf/Collect-Explain.ps1, T/perf/ReadResultParityIntegrationTest.java; 수정 병목으로 확인된 단 하나의 기존 repository/query 및 필요 시 V31__portfolio_read_indexes.sql 및 H2 대응(인덱스 변경이 없으면 파일 생성하지 않음); 생성 docs/results/mysql-baseline.md(측정 후).

**Interfaces:** Invoke-ReadBenchmark.ps1 -RunId <id> -Concurrency 1,10,50,100 -WarmupSeconds 30 -DurationSeconds 120 -Repeat 3 -CacheMode off.

- [x] **1 — 실패 시험/실험 작성:** 고정 실제 snapshot·권한·기업 분포로 검색/상세/목록/대시보드 중 한 경로를 선택한다. before에서 EXPLAIN ANALYZE/요청당 SQL·pool wait·tail latency를 수집하고 개선 후보를 결정한다. 아직 없는 맞춤 상태 API는 이 기준선에 포함하지 않는다.

  기대 assertion: `assertThat(after.responseCanonicalHash()).isEqualTo(before.responseCanonicalHash())`
- [x] **2 — red/기준선 확인:** `.\gradlew.bat realInfraTest --tests "*ReadResultParityIntegrationTest" --console=plain`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [x] **3 — 최소 구현/측정:** 전후 한 변수만 변경한다. 예: 확인된 인덱스/조건·정렬/DTO 조회 중 실제 병목을 해결하는 하나를 선택한다. 실제 SQL·바인딩된 공개 시험 파라미터·행수·실행계획을 보관하고 인덱스 이름만으로 성과를 설명하지 않는다. 성능이 나빠지거나 의미가 바뀌면 변경을 채택하지 않는다.
- [x] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 논리 응답 일치가 먼저 통과해야 한다. 그 뒤 READ 프로토콜의 3회 전후 실험에서 절대값·분포·오류·query 수와 개선/기각 근거를 산출한다.
- [x] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

완료 2026-11-05: 실제149976개/20기업 개요 한 쿼리의 MAX→NOT EXISTS를 채택했다. c1/c10 전후각3회(warm30s/main120s), c50양쪽한계1회/가드중단·100미실행. c10 P95중앙27.90%감소/TPS중앙30.95%증가, 본측정105364건성공·SQL평균11동일·업무hash/DB전체지문유지. Java518중503pass/기존15skip, 원자료14요약재계산일치. JMeter부재로Python발생기이며JMX는미실행템플릿이다. 결과 (로컬 자료·Git 제외: `../../results/mysql-baseline.md`), 원장 (로컬 자료·Git 제외: `../../../perf/results/20261105-m01-m02/progress.md`). 독립검토후준비/SQL도구만보강했으며앱jar/발생·계측AST동일성을별도검증했다. 인덱스/스키마 변경은 없으므로 V31은 생성하지 않았다.

## M03. 새 요청 흐름의 cache-off 기준선

**의존:** K03,R07,M01

**Files:** 생성 perf/jmeter/portfolio-report.jmx, perf/jmeter/portfolio-status.jmx, T/perf/ReportMetricConsistencyIntegrationTest.java; 수정 PortfolioMetrics.java 및 Invoke-ReportBenchmark.ps1(lifecycle 연동).

**Interfaces:** Invoke-ReportBenchmark.ps1 -RunId <id> -ArrivalRates 1,5,10,20 -MockLatencyMs 200 -CacheMode off -Repeat 3. 2s 지연은 별도 run family.

- [x] **1 — 실패 시험/실험 작성:** 각 request의 수락→등록→실행→완료 timestamp와 client 관측을 매칭한다. 모델 시계값과 DB commit 내부 기록 시각의 의미는 프로토콜대로 구분한다. 하나의 202 뒤 실패/대기가 발생한 fixture에서 HTTP 성공과 업무 성공이 서로 다른 수치로 계산되는지 시험한다.

  기대 assertion: `assertThat(metrics.acceptedCount()).isEqualTo(100); assertThat(metrics.succeededCount()).isEqualTo(expectedCompleted); assertThat(metrics.remainingCount()).isEqualTo(100-expectedTerminal)`
- [x] **2 — red/기준선 확인:** `.\gradlew.bat realInfraTest --tests "*ReportMetricConsistencyIntegrationTest" --console=plain`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [x] **3 — 최소 구현/측정:** 새 DB 상태 조회와 같은 durable 흐름에서 baseline을 수집한다. 사용자20·warm30s/main120s·도착률 프리셋·cap10,000·worker4를 manifest에 고정하고 mock 요청 수를 따로 센다. 완료되지 않은 요청을 버리지 않고 drain 구간/실제 달성 도착률을 기록한다. 모의 200ms/2s가 실제 AI 지연이라는 해석을 막는다.
- [x] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 메트릭과 DB ledger가 맞아야 하고 accepted TPS·완료 TPS·각 지연이 분리돼야 한다. 기준선 원자료를 저장한 뒤에만 M04를 시작한다.
- [x] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

## M04. 한 조회의 cache-aside·상태 projector

**의존:** M03,M02

**Files:** 생성 J/company/reportrequest/cache/ReportStatusCache.java, ReportStatusCacheProjector.java, RES/redis/report-status-version.lua; T/company/reportrequest/ReportStatusCacheIntegrationTest.java, T/reliability/StatusCacheFailureTest.java. 수정 ReportRequestQueryService.java, 새 consumer factory/metric. 기존 AiReportRequestStatusService는 일반 경로 호환용으로 유지한다.

**Interfaces:** getOwnedStatus(userId,requestId); putIfNewer(owner,requestId,stateVersion,status,ttl); status topic 별도 consumer group. Redis 의존 실패는 요청/job 그룹과 분리한다.

- [x] **1 — 실패 시험/실험 작성:** 캐시 미스/TTL·Redis outage·동시 cold miss, 상태 v5 후 v4 도착, 타인 키 조회, DB 최종 완료 후 Redis 갱신 실패와 projector 재개를 시험한다. 기존 query가 선택되면 동등한 snapshot/owner/버전 계약 시험을 적용한다.

  기대 assertion: `assertThat(cached.stateVersion()).isEqualTo(5); assertThat(otherUserResponse).isEqualTo(404); assertThat(dbFallback.requestId()).isEqualTo(requestId)`
- [x] **2 — red/기준선 확인:** `.\gradlew.bat realInfraTest --tests "*ReportStatusCacheIntegrationTest" --console=plain`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [x] **3 — 최소 구현/측정:** M02/M03에서 효과가 있을 경로 한 개를 선택한다. 상태 조회가 대상이면 TTL2s·소유자 키·version Lua를 구현하고 모든 상태 event를 DB 기준으로 재구성한다. 일시 Redis 실패는 짧은 timeout과 DB fallback, projector는 ACK 전에 retry/정지 후 재개한다. stale payload의 raw 상태를 덮지 않는다. single-flight는 DB 증폭 측정에서 필요할 때만 구현한다. Redis write-behind는 여기서 추가하지 않는다.
- [x] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 정합성/권한 시험과 reliabilityTest --tests "*StatusCacheFailureTest"가 먼저 통과해야 한다. 그 뒤 cache-off/on A-B-B-A 실험으로 P95·성공 TPS·hit/miss·DB 쿼리·pool wait·오류를 비교해 채택/기각한다.
- [x] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

완료 2026-11-12: V2 기준선15회4756건모두완료/고부하6가드단축·상태READ ABBA12회663484시작/오류·응답불일치0을독립대조했다. cache-on P95중앙36.122→32.393ms/TPS446.379→483.413이나SQL1→1.0975·반복편차/운영비용때문에기본off를유지한다. 실제cacheRedis중단3seed·cold100/SQL200·권한/현재버전시험통과. 독립Important3를한번RED→GREEN으로보강했고최종Java591통과/기존15skip(이후Java입력동일)·Python71·브라우저13/build통과,정상계측AST동일성확인. JMeter대신Python발생기이며V1은탐색만보존한다. 시험9622PDF는복사보관후사용자명시승인으로계정20/보고서·PDF9622/소유확인격리714를정리했고미확인격리828은보존했다. 상세결과 (로컬 자료·Git 제외: `../../results/browser-and-cache.md`). M05이후미착수.

## M05. 기동·종료·과부하·정지시점 복원 runbook

**의존:** R07,K03,M04

**Files:** 생성 scripts/reliability/Capture-Checkpoint.ps1, Restore-Checkpoint.ps1, Test-Runbook.ps1; T/reliability/OperationalRecoveryTest.java; docs/runbooks/portfolio-local.md. 수정 local.ps1·readiness·ReportWorkerLifecycle.java(실험상 필요한 최소 보정만).

**Interfaces:** 정지한 시험 namespace의 DB dump+파일 manifest+checkpoint.json(Kafka/Connect 위치/복구 범위), 새 fixture namespace restore, post-restore 1건 smoke.

- [ ] **1 — 실패 시험/실험 작성:** 정상 종료의 claim 중지, 강제 종료 후 회수, DB unavailable/개별·전체 quota, broker down 중 API 재기동, 시험 checkpoint 복원 후 본인 파일·체크섬과 새 요청 완료를 확인한다.

  기대 assertion: `assertThat(restored.artifactChecksums()).isEqualTo(checkpoint.artifactChecksums()); assertThat(postRestoreRequest.state()).isEqualTo(SUCCEEDED)`
- [ ] **2 — red/기준선 확인:** `.\gradlew.bat reliabilityTest --tests "*OperationalRecoveryTest" --console=plain`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [ ] **3 — 최소 구현/측정:** PS wrapper에서 프로젝트·DB·volume 절대 대상을 검사하고 기존 PC 자원을 건드리지 않는다. checkpoint는 신규 접수와 worker를 중지한 시점으로 한정한다. restore 시 독립 토픽/그룹/커넥터 범위를 명시해 기존 live offset과 혼합하지 않는다. 단계별 command exit status·복구한계·실패 시 되돌릴 경로를 runbook에 기록한다.
- [ ] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 Test-Runbook.ps1이 지정된 fixture만 조작하고 복원 후 실행까지 확인해야 한다. 무중단/무손실 재해복구로 표현하지 않는다.
- [ ] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

## M06. 대표 장애 매트릭스와 종합 대조

**의존:** M05,D04,K03

**Files:** 생성 scripts/reliability/Invoke-FaultMatrix.ps1, Reconcile-Run.py, scripts/reliability/tests/test_reconcile.py; T/reliability/PortfolioFaultMatrixTest.java; perf/fixtures/fault-matrix.json. 기존 작은 단위 장애 시험은 유지한다.

**Interfaces:** Invoke-FaultMatrix.ps1 -RunId <id> -Cases F01,...,F16 -Requests 100 -Repeat 3. 선택하지 않은 relay 전용 case는 N/A와 이유를 반환한다.

- [ ] **1 — 실패 시험/실험 작성:** 누락된 request·중복 artifact·계속 대기하는 job·예상 실패를 넣은 golden reconciliation fixture에서 검사기가 각각 탐지하는지 먼저 시험한다. 업무 상태와 격리 메시지 수를 같은 분모로 더하지 않는다.

  기대 assertion: `assert reconcile(missing).missing == 1; assert reconcile(stuck).all_expected_success is False; assert reconcile(valid).unexpected_diff == 0`
- [ ] **2 — red/기준선 확인:** `python -m unittest discover -s scripts/reliability/tests -p "test_*.py"`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [ ] **3 — 최소 구현/측정:** 프로토콜 F01~F16을 barrier+실제 process/container 중단으로 3회씩 실행한다. expected accepted ID는 장애 직전 DB commit을 외부 driver가 확인해 체크포인트로 고정한다. 정상 복구 시나리오는 180s 관찰 한도 내 N건 SUCCEEDED를 요구하고 영구 오류는 예상 FAILED 건수를 정확히 대조한다. 외부 중복 호출·DB 중복 결과·고아 파일·복구 시간을 따로 출력한다.
- [ ] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 검사기와 reliabilityTest --tests "*PortfolioFaultMatrixTest"가 통과한 뒤 실제 PS matrix를 실행한다. 원자료 없는 case나 한도 초과를 통과로 집계하지 않는다.
- [ ] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.

## M07. 검증 원자료에서 포트폴리오 결과 작성

**의존:** M06 및 모든 필수 gate

**Files:** 생성 docs/results/reliability.md, data-quality.md, performance.md; docs/adr/0002-kafka-selection.md, 0003-redis-scope.md; scripts/perf/Verify-Evidence.ps1. 수정 README.MD 및 구현 범위에 맞춘 API 문서. 포트폴리오 PDF 수정은 원본 확인 후 별도 요청 범위에서 한다.

**Interfaces:** Verify-Evidence.ps1 -RunId <id> → raw/manifest/summary 링크·수치 계산 일치·필수 case coverage 검증.

- [ ] **1 — 실패 시험/실험 작성:** raw 누락/다른 코드 hash/분모 혼합/측정하지 않은 improvement를 넣은 결과 fixture를 검사기가 실패시키는지 시험한다. 기존 disabled/미실행/조건부 N/A 시험을 누락시키지 않는다.

  기대 assertion: `assert evidence_report.has_unbacked_claims is False; assert evidence_report.required_case_coverage == 1.0`
- [ ] **2 — red/기준선 확인:** `powershell.exe -NoProfile -File scripts/perf/Verify-Evidence.ps1 -RunId <completed-run-id>`. 계산기/기능 시험은 실패 원인을 고정하고, 개선 작업은 원자료가 있는 before를 먼저 저장한다.
- [ ] **3 — 최소 구현/측정:** 요청 복구·적재 정합성·한 조회 성능을 대표 사례로 작성한다. 원래 실패/대안/선택/변경/시험/수치/한계를 연결하고 실제 운영·실제 AI 품질을 주장하지 않는다. Kafka의 로그 재생/분리와 운영 비용, CDC 판정, Redis 기각/채택 근거를 ADR로 남긴다. 라이브 실행 demo와 runbook이 원자료를 가리키게 한다.
- [ ] **4 — 검증/비교:** 같은 시험과 프로토콜의 실행 명령으로 숫자와 링크를 재계산/검사하고 verifiedCheck의 통과·실패·skip/N/A 요약을 첨부한다. 대표 사례별 증거가 없으면 완료가 아니다.
- [ ] **5 — 증거/검토:** manifest·raw·summary·불변조건·관찰 한계/skip 이유를 저장하고 변경 범위와 다음 작업을 기록한다.
