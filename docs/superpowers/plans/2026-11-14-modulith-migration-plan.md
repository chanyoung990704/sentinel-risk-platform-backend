# SENTINEL Spring Modulith 전환 작업 계획

> **실행자 지침:** 구현 시 `superpowers:executing-plans`를 적용해 작업별로 진행한다. 별도 병렬 작업 요청이 있을 때만 위임한다. 체크박스는 실패 재현→최소 변경→검증 순서로 완료한다.

**Goal:** 기존 신뢰성·조회 동작을 보존하면서 도메인별 API와 데이터 소유권을 Spring Modulith로 강제하고, 추후 독립 서비스 추출의 변경 지점을 한정한다.

**Architecture:** 단일 Gradle 프로젝트·단일 Spring Boot 배포를 유지하며 패키지 모듈을 단계적으로 닫는다. 업무 API/불변 DTO/ID 참조로 경계를 만들고 기존 CDC·Outbox·Inbox·보고서 트랜잭션을 유지한다. 실제 MSA/독립 DB는 B09 후속이다.

**Tech Stack:** Java 21 / Spring Boot 3.5.9 / Gradle 8.14.3 / Spring Modulith 1.4.x 후보 / MySQL·JPA·QueryDSL·Flyway / Redis / Kafka·Debezium. Modulith 패치는 B01에서 호환성과 가용성을 확인한 뒤 고정한다.

**Spec:** [경계·추출 설계 초안](../specs/2026-11-14-modulith-extraction-design.md)

**상태:** 2026-11-14 사용자 승인에 따른 B01~B07 구현·검증과 B08 실행/독립 재계산·원본 재개 감사가 완료됐다. 최종 전체 검토 Important1건은 검증 도구/시험만 보강해focused5/5·전체Python114pass로 해결했다. 최종 패키지 확인 값은 evidence-verification.json에 기록한다. 모듈 책임의 확정 내용은 `docs/architecture/module-ownership.md`, 단계별 실제 실행과 실패/보강 기록은 로컬 원장 `perf/results/20261114-modulith/progress.md`에서 확인한다. B09와 자동 커밋/push/배포는 제외한다. 체크박스는 해당 검증이 끝난 시점에만 완료로 바꾼다.

## 공통 제약

- Java 21 / Spring Boot 3.5.9 / Gradle 8.14.3을 기준으로 하며, 단순 Modulith 도입을 위해 Boot 4로 올리지 않는다.
- 애플리케이션·Gradle·Docker·Python 실행은 WSL Ubuntu에서 한다. 8GB 환경/공유 포트 제약에 따라 실제 인프라 시험은 기존처럼 순차 실행한다.
- 적용한 Flyway 파일·테이블명·PK·이벤트 업무 키·실제 원본·사용자 변경·기존 증거를 보존한다. package 변경과 schema 변경을 한 번에 섞지 않는다.
- 기존 API의 URL·DTO·상태 코드·owner 검사·추가 입력/key/hash 규칙, 기본 cache-off를 유지한다.
- 요청/슬롯/Outbox와 완료/artifact/슬롯 해제의 원자성을 유지한다. 기존 CDC를 Modulith 외부화나 동시 polling으로 이중화하지 않는다.
- 코드 주석/계획/결과는 한국어, 커밋 메시지는 영어다. 테스트는 given/when/then 주석을 사용한다.
- 아래 신규 파일명/메서드는 제안 계약이다. B01에서 기존 서명과 비교해 확정하고 계획을 동기화한다. `...`로 표현된 이동 범위는 해당 단계 시작 시 파일 목록을 고정한다.
- 각 B 단계는 독립된 검증 가능 변경 단위다. 커밋할 때는 개인 로그→공유 context를 동기화하고, 다음 단계의 미완료 코드를 섞지 않는다.

## 중점 검증 조건

| 조건 | 기대 동작 | 담당 단계 |
| --- | --- | --- |
| 패키지명 변경 뒤 과거 JSON/JPQL/CLI/Batch 메타 참조 | 기존 이벤트 역직렬화·쿼리·기동 유지 | B01/B04/B06 |
| 계정 정지·탈퇴/타인 파일 접근·인증 저장소 장애 | 기존 접근 거절·민감 정보 비노출 유지 | B02/B03/B06 |
| 활성 snapshot 전환과 동시 접수·재시도 | 수락한 source 고정, 혼합 값/미검증 값 노출 방지 | B04/B06 |
| 모듈 API 치환 후 페이지·정렬·합계 또는 SQL 수 변화 | 응답 동등성, 건당 연쇄 호출 방지, 성능 회귀 확인 | B04/B05/B08 |
| timeout/응답 유실·중복 이벤트·stale lease·비동기 유실 | 중복 DB 효과 방지, 단일 결과 확정, 기존 복구 경로 유지 | B06/B07 |

## 경로와 실행 규칙

아래 `P`는 `src/main/java/com/aivle/project`, `T`는 `src/test/java/com/aivle/project`다. target 모듈 구조는 `P/<module>/api`, 필요한 `spi/events`, `internal/{application,domain,persistence,adapter}`를 기준으로 한다. 책임에 필요한 하위 폴더만 만든다. 빈 계층을 일괄 생성하지 않는다.

기존 `ProjectApplication.java`는 P 루트에 유지한다. 대상 모듈은 identity/finance/insight/reporting/aiintegration/watchlist/news/community/storage이며, app은 조립, foundation은 최소 공통 기반이다. 기존 common 업무 코드를 foundation으로 통째 이동하지 않는다.

검증 결과는 새 로컬 원장 `perf/results/<실행일>-modulith/`에 단계·소스 SHA/dirty 파일 hash·환경·명령·조건·JUnit XML·SQL·데이터 지문을 저장한다. 과거 측정 디렉터리를 덮어쓰지 않는다. 결과 문서/포트폴리오 참고용은 기존 Git 제외 정책을 유지한다.

계획상 신규 `modulithTest` task는 B01에서 작성한다. 아래 명령은 그 이후 Ubuntu의 격리된 시험 설정에서 실행할 절차이며, 문서 작성 시 실행한 결과가 아니다.

```bash
./gradlew modulithTest
./gradlew fastTest
./gradlew realInfraTest
./gradlew cdcTest
./gradlew reliabilityTest
./gradlew bootJar
```

각 실제 인프라 task에는 기존 runbook의 시험 프로파일·DB·비밀 설정을 적용한다. 이 명령만 실행해 원본 운영용 namespace에 연결하지 않는다. `verifiedCheck`는 기존 증거 수집 절차에 따라 실행하고 비활성 시험까지 구분한다.

## B01 — 경계 목록·호환성·회귀 기준선

**변경/신규 파일:** `build.gradle`, 필요 시 `src/test/resources/application-modulith.properties`, `T/architecture/ApplicationModulesTest.java`, `T/architecture/ModuleContractRulesTest.java`, `docs/architecture/module-ownership.md`.

**산출 계약:** 테이블별 writer/read API/기존 FK/교차 join/삭제 정책/transaction 목록, 현재 API·이벤트 schema 목록, 모듈 이동 파일 목록과 고정 BOM. 현 import 수준 관찰을 완전한 AST/SQL 조사로 대체한다.

- [x] 현재 소스/DB/파일 지문과 기동 토폴로지를 기록한다. 기존 Java 607 pass/15 disabled·Python104는 과거 기준으로 표시하고 착수 시 실제 기준선을 별도 실행한다.
- [x] 순환·내부 접근·Repository/Entity 반환·도메인 역참조를 탐지하는 규칙과 의도적 위반 fixture를 작성해 실패를 확인한다. 현재 위반은 기준 목록으로 남기고 새 위반 증가를 차단한다.
- [x] Boot 3.5와 맞는 Modulith 1.4.x BOM·API/test 의존성을 선택하고 의존성 해석 결과를 기록한다. 이벤트 저장소·Kafka 외부화는 추가하지 않는다.
- [x] `modulithTest`를 전용 태그/필터로 만들어 다른 task와 XML 집계 중복이 없도록 기존 `portfolioTest`/verifiedCheck 도구와 연결한다. 단순 architecture test를 별도 JVM이 불필요한 무거운 인프라 시험으로 만들지 않는다.
- [x] 전환 중에는 명시적 모듈 감지와 위반 기준 목록을 사용할 수 있다. 감지에서 빠진 legacy 패키지를 포함해 전체 소스 의존성도 검사하고, B07까지 감지 범위/예외를 닫을 계획을 기록한다. 초기 전체 `verify()`가 이미 통과한다고 표시하지 않는다.
- [x] JPQL의 완전한 enum 이름, Kafka type header/serializer, JSON payload/DB 저장 타입 이름, CLI mainClass, Python/운영 스크립트, JPA scan, Spring Batch JobRepository 재시작 메타에서 package 이름 의존을 조사한다.

**완료:** 도입 버전/이동 목록/테이블 소유권·현재 위반 기준·실제 회귀 기준선이 재현된다. 새 기능이나 데이터 변경은 없다.

## B02 — 공통 역의존 정리·identity 경계

**이동/수정:** `P/auth`, `P/user` → `P/identity/internal`; `P/common/config/SecurityConfig.java`, `DevSecurityConfig.java`, 인증 관련 common/security → 발급(identity)과 HTTP 조립(app)으로 분류. common 오류/기반 타입은 foundation 또는 소유 모듈로 이동.

**신규:** `P/identity/package-info.java`, `P/identity/api/package-info.java`, `IdentityQueries.java`, `AccountState.java`, `T/identity/IdentityModuleTest.java`, 인증 호환 시험.

**계약:** `IdentityQueries.requireActive(long userId)` 및 ID/상태 기반 DTO. JWT subject/claims와 기존 로그인/refresh DTO는 유지한다. 타 모듈 컨트롤러에 `UserEntity`를 전달하지 않도록 actor ID/권한으로 바꾼다.

- [x] 로그인·refresh rotation/동시 갱신·logout/blacklist·만료·정지·삭제 계정·타인 접근·Redis 장애의 현재 행위를 시험으로 고정한다. 미구현 외부 OAuth/OIDC 기능을 완료로 간주하지 않는다.
- [x] 공통↔auth/user 순환과 공개 API의 Entity 노출이 실패하는 검증을 추가한다.
- [x] 계정과 인증을 같은 identity 경계로 이동하고 서명 개인 키/발급 책임과 업무 자원 검증/HTTP 체인을 분리한다. 기존 메일 정책은 내부 port로 보존한다.
- [x] 다른 모듈은 identity API만 사용하게 바꾸고 신규 계정 조회 쿼리 수와 기존 계정 상태 검사 시점을 확인한다. 단순 JWT 인증 성공만으로 ACTIVE 검사를 제거하지 않는다.
- [x] identity 모듈 독립 부팅 시험과 fastTest/영향받은 실제 인증 시험을 실행한다.

**완료:** identity 밖에서 사용자/refresh Repository·Entity·개인 키에 접근하지 않는다. 실제 독립 로그인 서버/JWKS 제공 구축은 아직 하지 않는다.

## B03 — community와 storage 분리

**이동/수정:** `P/post`, `comment`, `category`, `tag`, `file/service/FileService.java`, `file/entity/PostFilesEntity.java`의 게시물 관련 부분 → community. `FilesEntity`, 범용 저장 service/validator/local/S3 adapter → storage.

**신규:** `P/storage/api/BlobStorage.java`, `FileCatalog.java`, `StoredObject.java`; `P/community/api/CommunityQueries.java`; 두 모듈의 package-info 및 module test.

**계약:** storage는 소유 도메인과 무관한 blob key/크기/미디어/hash·파일 ID를 반환한다. 게시물 owner/read 권한과 post_files 관계는 community가 소유한다. reporting artifact owner와 완료 메타는 reporting에 남긴다.

- [x] 업로드/열람/삭제/다른 사용자 거절/삭제된 게시물/같은 파일 연결/저장 실패의 기존 HTTP 계약을 시험으로 고정한다.
- [x] storage → community/identity Entity/Repository 의존을 금지하는 실패 시험을 만든다.
- [x] 게시물 권한 orchestration을 community로 옮기고 도메인 연관을 ID/API로 치환한다. 기존 API URL과 local/S3 설정은 보존한다.
- [x] blob 저장 성공 후 DB rollback 및 메타만 남은 경우를 시험하고 기존 정리 정책의 범위를 기록한다. 실제 분리 때 필요한 보상 절차를 ADR에 남긴다.
- [x] 영향 모듈 통합 시험·파일/게시물 회귀를 실행한다. 실제 원격 S3 성공을 local adapter 시험으로 대체 주장하지 않는다.

**완료:** file↔post 순환 제거, 게시물 접근 제어 동일, storage는 업무 저장소를 직접 사용하지 않는다.

## B04 — finance 소유권과 불변 source 계약

**이동/수정:** `P/dataimport`, `report`의 재무 원본/버전, `industry`, `quarter`, `metric`, `metricaverage`, `risk`, company 마스터/DART/keymetric·순수 재무 조회 → `P/finance/internal`.

**신규:** `P/finance/api/FinancialQueries.java`, `ReportSourceReader.java`, `SourceRef.java`, `FinancialSnapshot.java`, 공개 metric/value DTO, package-info, `T/finance/FinanceModuleTest.java` 및 source 동등성 시험.

**계약:** `SourceRef(snapshotId, companyId, targetQuarterKey)`와 `FinancialSnapshot(schemaVersion, sourceRef, cells, checksum)`. 필드의 scale/단위/NULL/ACTUAL·예측 의미와 정렬은 기존 CanonicalMetricCell/ReportSourceData에 대응시켜 확정한다. 내부 Entity/적재 parser를 계약으로 노출하지 않는다.

- [x] 적재→STAGED→materialize→VERIFY→active 전환과 run/chunk 재개 시험의 기존 조건을 고정한다. 활성 전환 중 source 고정·미검증 값 비노출·snapshot 보존을 실패 시험으로 추가한다.
- [x] 금융 read/write와 적재의 내부 호출을 한 finance 모듈로 옮긴다. `ImportPreflightService.sha256`을 보고서/AI가 참조하던 기술 결합은 foundation의 순수 hash 기능 또는 소유 adapter로 정리하고 checksum 형식을 유지한다.
- [x] reporting의 requireCompany/active/requireValues/source 조회를 finance API로 위임한다. 초기 로컬 transaction의 격리와 잠금 순서를 보존하고 원격 호출을 삽입하지 않는다.
- [x] 파일 관계는 storage ID/API로 바꾸고 물리 FK와 삭제 정책을 기록한다. 기존 Flyway 내용/데이터는 수정하지 않는다.
- [x] 기존 snapshot 통합/적재 재개 시험과 원본 전체 지문/NULL/zero/버전/active 대조를 실행한다. DB 보존과 원천 사실 정확성을 구분한다.

**완료:** 재무 테이블 writer는 finance, 타 모듈 source 소비는 공개 불변 계약이다. 기존 149,976행 보존 여부를 이번 실행 근거로 확인한다.

## B05 — watchlist·insight·news와 조회 조립

**이동/수정:** 먼저 `company/client/AiServerClient`와 `common/config/AiWebClientConfig`의 해석 HTTP 전송 책임을 aiintegration으로 이동한다. 이후 `P/watchlist`; `P/company/service`의 AI 해석·캐시/`reportanalysis`/`insight` → insight; `company/news` → news; `dashboard` 및 종합 화면 조립 → app. `CompanyWatchlistRepositoryImpl`, `CompanyWatchlistEntity`의 교차 Entity/QueryDSL 접근을 제거한다.

**신규:** `aiintegration/api/AiGateway.java`와 AI 해석 전송 DTO·package-info·module test, `WatchlistQueries.companyIds(actorId, query)` 등 ID 기반 공개 API, finance 배치 조회/필요 read model API, insight/news package-info·module test, `T/architecture/QueryCompositionContractTest.java`.

- [x] B05를 B05a AI 공개 전송 경계→B05b watchlist 배치 조회→B05c insight/news·화면 조립의 검증 단위로 진행한다. B05a에서 기존 AI 해석 응답/timeout/오류의 계약 시험을 고정한 뒤 소유 DTO에 매핑해 transport를 이동한다. 이렇게 만든 AiGateway만 B05c에서 소비한다. reporting의 PDF 전송 확장은 B06에서 한다.
- [x] 기존 조회의 필터/정렬/동점/NULL/전체 건수/페이지 경계·권한 결과를 고정한다. 1개/20개 기업 조건에서 API 호출 수·SQL 수 증가를 관찰하는 시험을 먼저 작성한다.
- [x] watchlist는 등록 관계를 소유하고 finance는 재무 일괄 조회를 제공한다. 기존 cross join이 페이지/집계 의미를 만들던 부분을 호출 순서만 나눠 잘못 구현하지 않는다.
- [x] insight/news 계산을 자체 상태와 finance source/AI 공개 계약으로 분리한다. 재무 수치·예측 테이블을 갱신한다면 finance command API를 호출하고 직접 쓰지 않는다.
- [x] 화면 조립은 app으로 올려 finance→watchlist 또는 finance→insight 역의존을 없앤다. 외부 API DTO/URL과 기존 캐시 key 의미를 보존한다.
- [x] 여러 모듈 정보를 합치는 조회는 배치 API 우선으로 구현한다. 지연 projection이 꼭 필요하면 정합성·재구축·추가 저장 비용과 전달 경로를 별도 결정하고 범위 문서를 갱신한다.
- [x] 모듈 시험/응답 동등성/SQL 수 회귀를 실행한다. 수치가 나빠지면 실제 비용을 기록하고 B08의 전후 비교 전에 해결하거나 변경 범위를 재결정한다.

**완료:** 각 기능은 소유 API만 사용하고 페이지·정렬·권한 의미가 같다. 건당 연쇄 호출을 새 구조의 기본값으로 허용하지 않는다.

## B06 — reporting·AI 연동·전달 경계

**이동/수정:** `company/reportrequest`, `company/reportjob`, 업무 종속 `messaging`, 기존 `company/job` 및 AI 보고서 orchestration → reporting. B05의 aiintegration에 맞춤 PDF HTTP 전송을 추가하고 로컬 mock 전송 코드를 분리한다.

**신규/확장:** `ReportingCommands`, `ReportingQueries`, `aiintegration/api/AiGateway.java`의 PDF 전송 계약과 소유 DTO·오류, reporting 내부 `AiReportGenerationAdapter`, 양쪽 package-info·module test. 내부 `ReportGenerationPort`는 유지한다.

- [x] 기존 key 재접수/다른 입력409/본인 상태·파일/타인404/용량한도/슬롯/현재 source 고정의 계약 시험을 추가 또는 재사용한다.
- [x] 접수 rollback에서 요청/슬롯/Outbox가 함께 되돌아가고, 완료 rollback/stale lease에서 artifact 메타·완료·슬롯 해제가 함께 거절되는 것을 실제 MySQL로 확인한다.
- [x] AI HTTP transport와 도메인 PDF 검증·재시도 정책을 구분해 이동한다. `aiintegration`이 reporting/finance 타입을 역참조하지 않도록 mapping adapter를 reporting 내부에 둔다.
- [x] 로컬 mock 제한을 유지하고 429/503/지연/연결 실패/잘못된 media/PDF 크기·손상/응답 유실을 계약 시험한다. reporting과 HTTP client의 이중 retry를 금지한다.
- [x] Outbox→CDC→Kafka→Inbox+job commit→ACK를 같은 경로로 유지한다. 기존 JSON/schemaVersion/eventId/key/header fixture를 새 코드로 소비하고, 오래된 job/event를 신규 package로 재개한다.
- [x] 신규 경계에서 실제 JVM 중단·재개와 중복 이벤트/수동 복구/파일 확정 시험을 수행한다. 외부 호출 중복과 DB 결과 단일 확정을 각각 집계한다.

**완료:** 요청~관리 재개가 같은 reporting 업무 경계에 있고 AI 부분은 전송 API로 대체 가능하다. 새 모듈의 성공을 실제 원격 모델 성공이나 외부 HTTP exactly-once로 표현하지 않는다.

## B07 — 전체 경계 차단과 문서 자동화

**수정/신규:** 전체 module/package-info, `T/architecture/ApplicationModulesTest.java`, `ModuleContractRulesTest.java`, SQL 소유권 규칙·통합 시험, `docs/architecture/module-ownership.md`, 모듈 문서 생성 test, CI/verifiedCheck의 관련 파일.

- [x] `ApplicationModules.of(ProjectApplication.class).verify()`로 순환·내부 접근·허용 의존을 모두 차단한다. 남은 legacy/unannotated/OPEN 모듈·global allowlist를 검토해 우회 범위를 제거한다.
- [x] 다른 모듈 Entity/Repository/JPA 연관/공개 반환 타입 의존과 foundation 역참조를 검사한다. 검증 규칙이 위반 fixture에서 실제 실패하는지 확인한다.
- [x] SQL/JPQL·QueryDSL 우회는 소유권 목록과 관찰한 SQL로 확인한다. 대표 module test의 MySQL 최소 권한 계정으로 타 소유 테이블 직접 조회/쓰기가 실패하고 공개 API 통한 경로는 소유 실행 설정에서 통과하는 것을 구분한다. 이를 단일 운영 DataSource의 물리 접근 차단 증명으로 과장하지 않는다.
- [x] 모듈별 `@ApplicationModuleTest`는 필요한 외부 공개 API만 대체한다. 모든 시험을 ALL_DEPENDENCIES로 띄워 결합을 숨기지 않는다. 실제 연결 검증은 별도 통합 시험에서 수행한다.
- [x] Documenter로 현재 소스의 의존 그래프와 모듈 계약 문서를 생성한다. 자동 생성 build 산출물은 직접 편집하지 않는다.
- [x] 전환 중 위반 기준/bridge 목록을 최종 0으로 정리한다. 남는 DB FK/같은 DB·JVM/교차 모듈 로컬 transaction은 추출 전 해소할 제약으로 따로 기록한다.

**완료:** 경계 규칙 위반 0 및 신규 위반 CI 차단. diagram과 소유권 문서가 실제 코드와 일치한다.

## B08 — 전체 회귀·성능 비교·포트폴리오 증거

**수정:** README, 관련 ADR/runbook, 로컬 결과 문서와 포트폴리오 참고용. **신규 원자료:** `perf/results/<실행일>-modulith/`의 단계별 로그·JSON·XML·CSV·무결성 목록.

- [x] Ubuntu에서 fast/실제 MySQL·Redis/CDC/장애 복구/Python/영향 UI·bootJar를 순차 수행한다. package/CLI/classpath 변경이 있으므로 과거 XML·과거 성능을 새 코드 결과로 재사용하지 않는다.
- [x] 이전 15 disabled는 별도 이유/영향 목록과 strict INCOMPLETE를 유지한다. 가능하면 영향을 받는 시험을 복구하되 범위/기능과 무관한 skip 제거를 회귀 통과 조건으로 속이지 않는다. 신규 skip은 0이어야 한다.
- [x] 최소 end-to-end는 로그인→조회→추가 입력 보고서 접수→CDC/Inbox→mock 생성→상태→본인 파일/타인 거절→실패 재시도/관리 재개다. 원본 데이터 전체 지문/건수/파일·격리 자료 보존을 확인한다.
- [x] 같은 PC/데이터/worker/캐시/기동 조건과 기존 30초 준비·120초 측정 등 프로토콜을 맞춰 비교 기준을 고정한다. MySQL 조회는 기존 동시성10·각3반복, 보고서 경로는 고정 도착률별 완료 지연/성공 TPS/중복 효과를 구분한다. 2026-11-15 사용자 승인으로 WSL 부담을 줄여 대표1/5요청/초·전후 각3회로 한정하고10/20요청/초·최대처리량 검증은 제외한다. 대상 외 변수와 실패 실행도 보존한다.
- [x] 비교는 P95·성공 TPS·오류율·SQL/요청·내부 API 호출 수·완료 지연·모듈 순환/위반 수를 사용한다. 허용 회귀 한도는 B01의 반복 편차와 목적을 근거로 측정 전에 문서화하며 임의의 개선 수치를 약속하지 않는다.
- [x] 문제/해결/결과 각3줄 사례를 작성한다. 예: 공통 역의존→도메인 API·검증→위반0; source 결합→불변 계약→동등성; 단순 분리의 조회 비용→배치 API→실측 결과. 실패/보류/단일 DB 한계도 함께 쓴다.
- [x] 소스 manifest/환경·조건/원자료/재계산/현재 결과 문서 연결을 검증한다. README에는 재현 가능한 결과만 싣고 계획을 구현 성과로 기재하지 않는다.

**완료:** 검증된 모듈형 모놀리스, 모듈 그래프·소유권·계약/장애 시험, 새 전후 성능 결과, 포트폴리오 근거가 산출된다. B01~B08이 권고하는 다음 구현 묶음의 전체 범위이며 B09는 자동 실행하지 않는다.

## B09 — 실제 서비스 추출 준비/검증: 별도 후속

이 단계는 당장 구현할 범위가 아니다. B08 결과와 추출 목적을 확인한 뒤 독립 계획으로 확정한다.

1. AI 제공자 계약이 생기면 aiintegration을 통해 별도 AI 실행체에 연결한다. mock transport와 remote provider를 구분하고 응답 유실·재시도·idempotency 지원·가용성/비용을 시험한다.
2. reporting 전체를 독립 배포 후보로 검증한다. API/consumer/worker는 한 서비스 소유 데이터를 공유할 수 있지만 finance/users 테이블 직접 접근은 제거한다. worker만 별도 JVM/같은 공유 DB인 중간 상태를 MSA 완료로 부르지 않는다.
3. finance source의 불변 payload/보존 예약, 접수 멱등성·실패 시 해제, 계정 상태 전파 지연, 삭제 정책을 정의한다. 최소 두 저장소 모의 경계에서 source 확보 성공/접수 실패, source timeout, 중복·순서 역전, 장기 재시작을 시험한다.
4. identity 추출은 JWT 공개 키 배포·회전·폐기/정지 지연 한도와 장애 정책을 정한 뒤 진행한다. 각 서비스는 업무 권한을 자체 검증하고 개인 키는 identity만 보유한다. 모든 업무 요청이 중앙 로그인 서버 HTTP에 의존하도록 단순 변경하지 않는다.
5. 이후 필요하면 Gradle `:app`, `:identity`, `:finance`, `:reporting` 등으로 컴파일 경계를 강화한다. 공용 Entity JAR을 만들지 않고 DTO/계약 버전을 관리한다. 독립 DB 이관은 expand/contract·자료 동등성·cutover/rollback·replay/offset 계획을 동반한다.

최소 추출 실험의 산출물은 독립 실행 구성·계약 시험·네트워크 장애 행렬·잔여 DB/인증 결합 목록이다. Kubernetes/서비스 메시/게이트웨이/분산 tracing 도입을 먼저 목표로 잡지 않는다. 실제 독립 배포·DB 소유권·복구 시험을 통과한 범위에 한해 MSA 경험으로 기술한다.

2026-11-15 실행 범위 판정:5/s B첫 반복이사전observedBacklog200가드에걸려후속2회미실행. 실제조회6+보고서10=16회(보고서9정상/1가드)이며시간개선인과판정은조회반복편차로유보한다. 원래범위를완주한것처럼표현하지않고1/s정상전후3회와5/s경계관측을분리했다. 사용자능동범위축소승인/사전가드적용이다.
