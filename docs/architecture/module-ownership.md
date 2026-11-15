# 모듈 경계와 데이터 소유권

현재 목표는 단일 Gradle 프로젝트·단일 Spring Boot 배포 안에서 경계를 검증하는 모듈형 모놀리스다. 독립 서비스 배포·독립 DB·분산 트랜잭션을 구현했다는 의미는 아니다. B01~B08 실행 상태와 최종 시험 결과는 별도 결과 문서에서 확인한다.

## 책임과 공개 계약

| 모듈 | 소유 책임 | 주요 공개 계약 |
| --- | --- | --- |
| identity | 계정·권한·로그인·JWT 발급/검증·refresh·계정 상태 | IdentityQueries, CurrentActor, AdminUserQueries |
| finance | 원천 재무·snapshot·적재·기업·분기·지표·기존 예측/PDF·AI 코멘트 | FinancialSources, SourceRef, FinancialSnapshot, CompanyDirectory, FinancialReferenceQueries, FinancialWatchlistReads, CompanyInformationBatches |
| reporting | 맞춤 보고서 접수·슬롯·job·attempt·artifact·관리 재개·Outbox/Inbox | ReportingCommands, ReportingQueries, CustomReportCommand, ReportRequestView |
| aiintegration | AI HTTP 전송·오류 분류·기존 AI 작업 dispatch | AiGateway, NewsGateway, AiJobs, AiReportTransport |
| insight | 사업보고서 해석 저장·뉴스/재무 공개 결과 조립 | CompanyInsights, ReportAnalysisOperations |
| news | 뉴스 분석·기사·뉴스 기반 평판 제공 | NewsOperations, NewsReadModel |
| watchlist | 사용자의 관심기업 관계·관리 | WatchlistOperations, WatchlistMemberships |
| community | 게시물·댓글·분류·첨부 관계·해당 자원의 접근 권한 | CategoryQueries, 요청/응답 DTO |
| storage | 범용 파일 메타·로컬/S3 blob·스트리밍·검증 | FileCatalog, FileMetadata, FileStorageService, FileStreamService |
| app | 여러 모듈을 조립하는 HTTP·자원 경로/CORS/401·403 정책·이벤트 처리·계측·실행 인프라 | 대시보드 및 조립 응답 |
| foundation | 감사 기반 타입·오류·페이지·해시·순수 유틸리티 | 다른 업무 모듈에 의존하지 않음 |

기존 `CompanyAiService`가 다루는 예측/PDF는 `company_report_versions`의 버전 데이터이므로 finance에 남긴다. `company_key_metrics.ai_comment`도 finance가 갱신한다. 새 맞춤 보고서 파이프라인은 reporting이다. 저장 책임을 확인해 분류했으며 AI라는 이름만으로 같은 도메인에 모으지 않았다.

finance의 `ExternalReputationScores`는 뉴스 제공자가 구현하는 outbound SPI다. finance는 뉴스 Repository나 내부 타입을 참조하지 않는다. 공개 API의 서비스 반환값은 DTO/읽기 스냅샷이며 JPA Entity를 반환하지 않는다. 내부 엔티티의 읽기 인터페이스 구현은 기존 팩토리·시험 호환에 한정하며 실제 공개 조회 어댑터는 사본으로 변환한다.

## 데이터 소유권

검증 정책의 원본은 `src/test/resources/architecture/table-ownership.json`이다. 아래 목록은 변경 전 스키마 조사와 대조한 59개 테이블이다. 소유 모듈만 직접 SQL/JPA/QueryDSL을 실행하고 다른 모듈은 공개 API를 사용한다. Spring Batch 메타는 finance의 적재/기업 동기화 실행을 지원하며 Flyway 이력은 app의 DB 부트스트랩 책임이다.

| 소유 모듈 | 테이블 |
| --- | --- |
| app | `flyway_schema_history` |
| community | `categories`, `comments`, `post_files`, `post_likes`, `post_tags`, `post_view_counts`, `posts`, `tags` |
| finance | `active_data_snapshots`, `batch_job_execution`, `batch_job_execution_context`, `batch_job_execution_params`, `batch_job_execution_seq`, `batch_job_instance`, `batch_job_seq`, `batch_step_execution`, `batch_step_execution_context`, `batch_step_execution_seq`, `companies`, `company_key_metrics`, `company_report_metric_values`, `company_report_versions`, `company_reports`, `data_snapshot_sources`, `data_snapshots`, `import_chunks`, `import_errors`, `import_runs`, `import_stage_values`, `industry_codes`, `key_metric_descriptions`, `metric_averages`, `metric_descriptions`, `metrics`, `quarters`, `risk_score_summaries`, `snapshot_report_versions` |
| identity | `email_verifications`, `refresh_tokens`, `roles`, `user_roles`, `users` |
| insight | `report_analyses`, `report_contents` |
| news | `news_analyses`, `news_articles` |
| reporting | `inbox_events`, `outbox_events`, `quarantine_replay_commands`, `quarantined_messages`, `report_admission`, `report_artifacts`, `report_attempts`, `report_jobs`, `report_recovery_commands`, `report_requests` |
| storage | `files` |
| watchlist | `company_watchlists` |

## 남겨 둔 물리 결합

기존 적용 Flyway·테이블·PK·인덱스·FK를 변경하지 않는다. 기존 FK 72개 중 모듈 간 FK 16개는 아래와 같다. 엔티티 관계를 ID로 바꾸어도 DB 제약은 남아 있다. 실제 독립 DB 추출 전에는 참조 무결성·삭제 순서·삭제 이벤트/보존 정책을 별도 설계해야 한다.

| 참조 컬럼 | 대상 키 | 모듈 방향 | 기존 ON DELETE |
| --- | --- | --- | --- |
| `comments.user_id` | `users.id` | community → identity | CASCADE |
| `company_report_versions.pdf_file_id` | `files.id` | finance → storage | SET NULL |
| `company_reports.post_id` | `posts.id` | finance → community | SET NULL |
| `company_watchlists.company_id` | `companies.id` | watchlist → finance | CASCADE |
| `company_watchlists.user_id` | `users.id` | watchlist → identity | CASCADE |
| `news_analyses.company_id` | `companies.id` | news → finance | CASCADE |
| `post_files.file_id` | `files.id` | community → storage | CASCADE |
| `post_likes.user_id` | `users.id` | community → identity | CASCADE |
| `posts.user_id` | `users.id` | community → identity | CASCADE |
| `quarantine_replay_commands.actor_user_id` | `users.id` | reporting → identity | NO ACTION |
| `report_analyses.company_id` | `companies.id` | insight → finance | CASCADE |
| `report_artifacts.file_id` | `files.id` | reporting → storage | NO ACTION |
| `report_recovery_commands.actor_user_id` | `users.id` | reporting → identity | NO ACTION |
| `report_requests.user_id` | `users.id` | reporting → identity | NO ACTION |
| `report_requests.company_id` | `companies.id` | reporting → finance | NO ACTION |
| `report_requests.snapshot_id` | `data_snapshots.id` | reporting → finance | NO ACTION |

## 로컬 트랜잭션과 외부 I/O

- 접수의 request/슬롯/Outbox, 완료의 파일 메타/artifact/상태/슬롯 해제/Outbox는 기존 로컬 DB 트랜잭션에 참여한다. 스토리지의 파일 메타 API도 같은 트랜잭션에 참여하며 독립 HTTP 호출로 바꾸지 않는다.
- 보고서 계정/관리자 자격은 identity API, 활성 source와 불변 재무 값은 finance API를 사용한다. 상태 조회의 계정 상태와 요청 버전은 같은 read transaction에서 확인하고 Redis·파일 읽기는 그 밖에서 한다. 추가 SQL 비용은 새 성능 비교에 포함한다.
- 관심기업 관계는 watchlist가 ID 묶음으로 조회하고 finance가 해당 ID의 재무 값을 배치로 반환한다. 기업별 서비스 호출을 반복하는 방식으로 옮기지 않는다.
- 파일 메타는 storage, 게시물 권한은 community, 맞춤 artifact/시도별 파일 확정 권한은 reporting이 소유한다. `FileCatalogReaders`, `IdentityReaders`, `FinancialSourceReaders`는 웹 앱/키 발급을 띄우지 않는 관리 CLI의 읽기 전용 조립 API다. SQL과 정책 구현은 각 소유 모듈 내부에 있고 별도 JVM 재개·격리 재전달 시험으로 연결을 확인한다.
- 신규 로컬/S3 blob 저장은 활성 DB 트랜잭션의 정상 롤백에서 해당 새 blob만 보상 삭제한다. commit 또는 트랜잭션 외 저장은 유지한다. 프로세스 강제 종료·스토리지 삭제 장애에는 완전한 원자성을 보장하지 못하며 삭제 실패 로그와 고아 파일 점검이 필요하다. reporting의 attempt/lease 기반 정리 규칙은 별도로 유지한다. 모의 S3 시험은 실제 원격 S3 성공 증거가 아니다.
- 기존 인사이트 생성/갱신은 news 갱신과 finance 점수 명령도 같은 로컬 쓰기 트랜잭션에 참여한다. 외부 실패를 catch해도 rollback-only가 남는 기존 경로가 있어, 실제 추출 전에는 외부 호출/저장 단계·부분 성공·오류/재시도 계약을 별도로 정해야 한다. 이를 독립 HTTP로 단순 치환하지 않는다.
- 기존 Debezium CDC가 유일한 durable Outbox relay다. Modulith 이벤트 저장소/외부화나 동시 polling relay를 추가하지 않는다. Inbox의 DB 멱등성이 외부 AI HTTP exactly-once를 보장하지 않는다.

## 경계 검증과 한계

`modulithTest`는 11개 모듈 감지·순환/내부 접근/허용 의존·공개 제네릭 계약 및 STANDALONE 기동을 검증한다. 전체 의존 기동으로 모듈 시험을 대신하지 않는다. 외부 업무 API만 대체하고 finance의 외부 평판 SPI와 Redis 같은 인프라는 시험 경계로 명시한다.

Python 보조 검사는 알려진 테이블의 문자열 SQL 직접 접근을 검사한다. 실제 인프라 시험은 대표 요청에서 바인딩 값을 수집하지 않는 JDBC SQL/호출 소유자 대조와 finance SELECT 제한 계정의 성공/권한 거절을 추가한다. 제한 계정의 별도 실행 설정과 전체 운영 앱의 단일 DataSource는 구분한다. 이 검사는 실행하지 않은 모든 동적 SQL 경로의 물리 격리를 증명하지 않는다.

`./gradlew modulithTest`의 Documenter 출력은 `build/spring-modulith-docs/`에 생성된다. 자동 생성 파일을 수작업으로 수정하지 않는다. `verifiedCheck -PoutboxMode=cdc`는 모듈 범위를 포함하며 기존 비활성 시험 15개를 성공으로 계산하지 않는다.

## 실제 서비스 추출 순서

AI 제공자 계약/멱등성·timeout 정책을 확정한 뒤 aiintegration의 전송 구현을 대체한다. 다음으로 reporting 전체를 추출 후보로 다루며 finance source 확보/보존 예약·계정 상태 전달·별도 저장소 실패/보상·replay를 검증한다. identity는 공개 키 배포/회전/폐기·계정 정지 전파 지연과 각 자원 서버의 업무 권한 정책을 확정한 뒤 분리한다. 패키지/포트 경계가 만들어진 사실을 MSA 배포 완료로 표현하지 않는다.
## 제한 계정 시험 재현

원본을 보존 정지하고 기존 runbook에 따라 이번 작업 전용 modulith의 6개 인프라를 준비한 뒤 Ubuntu에서 실행한다. 준비는 해당 계정이 없는 새 시험 환경에서 한 번만 수행한다. 기존 자격 파일이 있으면 덮어쓰지 않고 권한을 확인해 재사용한다.

```bash
SENTINEL_RUNTIME_NAMESPACE=modulith python3 scripts/reliability/module_privileges.py
# 기존 검증 실행기에 modulith 설정을 적용한 realInfraTest 범위에 포함된다.
```

비밀번호를 인수나 문서에 넣지 않는다. 파일은 Ubuntu private state에 0600으로 생성되며 Git에 포함하지 않는다. 원본 portfolio namespace에서는 준비가 거부된다. 제한 계정은 SELECT만 허용하므로 finance 테이블 쓰기도 실패해야 한다.

## 관심기업 기본 정보의 배치 조회

관심기업의 기본 정보도 finance의 CompanyInformationBatches.latest로 ID 목록을 한 번 전달한다. finance가 기존 stockCode 기반 최신 ACTUAL/snapshot 조건과 업종·점수를 일괄 조회하고, 필요한 뉴스 평판은 news 구현의 배치 SPI로 받는다. 입력 순서/중복 ID·NULL 분기/stock code·점수 반올림은 단일 조회와 대조한다. 같은 분석 시각의 뉴스는 최신 ID를 선택해 단일/배치 조회의 동점을 정의한다. 이 정렬은 기존에 명시되지 않았던 동점 규칙이다. 실제1/20기업 finance 배치 API 내부 SQL 수와 기업 개요 HTTP 전후 비교는 B08의 서로 다른 증거로 기록한다. 관심기업 관계/인증 SQL은 이 배치 API 내부 수에 포함하지 않는다.

전체 자원 경로 정책은 app의 ResourceSecurityConfig가 담당한다. identity의 SecurityConfig는 계정 인증·키 보관·JWT발급/검증과claims converter를 제공한다. app은프레임워크의JwtDecoder/Converter 빈을받고개인키/identity 내부저장소를참조하지않는다. 독립서비스추출시에는공개키/JWKS와토큰폐기전파정책을별도로구현해야한다.

계정/기업/파일의 소유 조회는 Entity 사본을 반환하고 `FinancialSnapshot`은 record와 `List.copyOf`로 수락 source의 불변성을 유지한다. 기존 HTTP 응답 DTO의 생성자/컬렉션까지 모두 깊은 불변 타입으로 바꿨다는 의미는 아니다. 기존 JSON 형식을 보존하면서 JPA 관리 객체의 외부 유출을 제거한 범위를 구분한다.
