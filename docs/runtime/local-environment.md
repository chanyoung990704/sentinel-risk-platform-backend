# WSL Ubuntu 복원 환경 (E02)

애플리케이션과 Gradle/Docker 시험은 Ubuntu에서 실행한다. Windows의 원본 checkout은 보존하고, 현재 작업 사본의 실행 입력만 `~/.local/share/sentinel/workspaces/e02-runtime`으로 동기화한다. 기존 `.env`, 운영 키, 업로드, `.git` 및 기존 build는 복사하지 않는다.

동기화 이력에 등록된 입력은 원본에서 삭제하면 실행 사본에서도 제거한다. build·비밀 파일·실행 상태는 이 정리 대상에 넣지 않는다. 저장된 소유 경로는 작업 사본 경계와 symlink 여부를 검사한 뒤 사용한다.

## 구성

| 구성 요소 | Ubuntu 접속 주소 | 격리 범위 |
| --- | --- | --- |
| MySQL | 127.0.0.1:13306 | DB sentinel_portfolio, 전용 mysql-data volume |
| Redis | 127.0.0.1:16379 | 전용 redis-data volume |
| Kafka | 127.0.0.1:19092 | 내부 listener kafka:9092, 전용 kafka-data volume |
| 모의 AI | http://127.0.0.1:18081 | 표준 라이브러리 HTTP 서버, 로컬 journal |
| Spring API | http://127.0.0.1:18080 | local 프로필, 전용 업로드 경로 |

Compose project는 `sentinel-portfolio`다. 모든 게시 포트는 127.0.0.1에만 바인딩한다. 기존 3306/6379/9092 서비스는 변경하지 않는다. MySQL 1GiB, Redis 256MiB, Kafka 1GiB, mock 128MiB 제한이며 앱 heap은 512MiB다. 단일 broker 구성이며 고가용성 검증을 의미하지 않는다.

이미지 digest는 `infra/portfolio-images.lock.json`에 고정한다. 현재 프로젝트의 MySQL8.0/Redis7.0/ApacheKafka3.7.0 계열을 복원 비교 기준으로 사용했다. Python base도 digest로 고정하며 mock 서버에 추가 pip 의존성은 없다.

## 실행과 종료

Windows PowerShell에서는 현재 작업 사본에서 아래 명령을 사용한다. wrapper가 Ubuntu 실행 도구로 전달한다.

```powershell
.\scripts\reliability\local.ps1 up
.\scripts\reliability\local.ps1 verify
.\scripts\reliability\local.ps1 start-app
.\scripts\reliability\local.ps1 stop-app
.\scripts\reliability\local.ps1 down
```

Ubuntu에서 직접 실행할 때는 다음과 같다. 소스 변경 후에는 첫 번째 명령으로 실행 사본을 동기화한다.

```bash
python3 /mnt/c/Users/chanyoungpark/.codex/worktrees/sentinel-wsl-baseline/sentinel-risk-platform-backend/scripts/reliability/local_runtime.py up
cd ~/.local/share/sentinel/workspaces/e02-runtime
python3 scripts/reliability/local_runtime.py verify
python3 scripts/reliability/local_runtime.py start-app
python3 scripts/reliability/local_runtime.py stop-app
python3 scripts/reliability/local_runtime.py down
```

`up`은 비밀 파일 준비→포트 충돌 확인→구성 요소 기동→연결 확인 순서다. `start-app`은 bootJar 생성 후 JVM을 기동하고 liveness를 기다린다. `stop-app`은 저장한 PID의 명령 경로를 확인한 뒤 정상 종료를 요청한다. `down`은 이 프로젝트의 컨테이너/네트워크만 제거하며 볼륨과 로컬 비밀 파일은 보존한다. 기존 자원은 정리하지 않는다.

WSL 자체를 종료하면 실행 중인 서비스도 중단된다. Ubuntu 터미널을 열어 둔 상태에서 작업하고, WSL 재기동 후에는 `up`으로 다시 준비한다. systemd의 Docker 서비스만으로 WSL 인스턴스가 계속 유지된다고 가정하지 않는다.

비밀 값은 `~/.local/share/sentinel/portfolio/secrets`에서 생성한다. DB 비밀번호, JWT 키, pepper를 저장소나 실행 결과에 복사하지 않는다. local 프로필의 메일 sender는 실제 SMTP 연결을 하지 않으며 DART/집계 스케줄은 비활성이다. 수동 DART API/외부 자료 수집은 이번 시험에 포함하지 않는다.

실행 도구는 필요한 OS 환경만 상속한다. 상위 프로세스의 Spring/APP/JWT 설정과 JAVA_TOOL_OPTIONS 등을 그대로 넘기지 않으며, Docker는 Ubuntu의 로컬 socket을 사용한다. loopback probe는 외부 HTTP proxy를 사용하지 않는다. 실행 매개변수 변경이 필요하면 이 전용 구성과 측정 manifest를 함께 수정한다.

## 모의 AI 계약

- `GET /health`: fixture 상태. 주입한 오류/지연에 영향받지 않는다.
- `POST /control`: seed(1~128자), delay_ms(0~10000), fail_first(0~1000), error_status(429/500/503). 생략한 필드는 기본값으로 재설정한다. 설정 변경 시 오류 횟수 counter는 초기화하며 전체 journal은 보존한다.
- `POST /test-ai/custom-reports`: JSON object 입력, 성공 시 application/pdf. 같은 seed와 정규화된 JSON 입력은 같은 PDF bytes를 반환한다. 이것은 실제 AI 생성 품질이나 업무 멱등성 구현을 의미하지 않는다.
- `GET /api/v1/analysis/{companyCode}/report`: 기존 AiServerClient PDF 다운로드 호환 경로.
- `GET /journal`: sequence, 입력 SHA-256, 주입 seed, 상태, 처리 시간. 요청 본문·추가 지시문 원문은 기록하지 않는다. journal은 mock-journal 디렉터리에 저장한다.

기존 AI 예측·건강점수·뉴스 API 전체를 모의 구현한 것은 아니다. 새 맞춤 보고서 업무 API는 후속 R01~R04 범위다. fixture의 테스트용 제어 API는 127.0.0.1로만 노출한다.

## 검증

```bash
cd ~/.local/share/sentinel/workspaces/e02-runtime
./gradlew realInfraTest --tests '*PortfolioRuntimeIntegrationTest' --no-daemon --console=plain
./gradlew realInfraTest --no-daemon --console=plain
./gradlew fastTest --no-daemon --console=plain
python3 -m unittest discover -s infra/mock-ai/tests -p 'test_server.py'
python3 -m unittest discover -s scripts/reliability/tests -p 'test_*.py'
```

PortfolioRuntimeIntegrationTest는 실제 MySQL/V1~V27, Redis TTL, Kafka 전송·수신, 기존 AI HTTP 클라이언트 PDF, SMTP 미사용을 확인한다. 지원 클래스는 H2 driver와 migration-h2가 대신 쓰이지 않도록 DB URL/driver/Flyway 위치를 명시한다. 기존 AuthIntegrationTest는 별도 Redis Testcontainer와 H2를 사용한다. 전체 realInfraTest 통과를 모든 시험이 MySQL이었다고 해석하지 않는다.

Kafka 중단 중 API liveness 기동 여부와 전체 구성 요소 정상 여부는 분리한다. Kafka가 중단된 동안 Kafka 기능이 사용 가능하다는 의미는 아니다. CDC 발행기 선택(E03), 실제 자료 적재(D01~), Outbox/Inbox와 장애 복구 시험은 아직 수행하지 않았다.

## 원자료

`perf/results/20261103-e02-runtime/`에 실행 로그, XML, 소스 지문, 실제 버전/이미지 ID, 기동·종료·재기동 관찰을 저장한다. 이 경로는 Git 제외 대상이다. 기준선 소요 시간은 서비스 latency/throughput 성능 수치가 아니다.

참고한 공식 설정 자료: [Kafka Docker](https://kafka.apache.org/37/getting-started/docker/), [MySQL 공식 이미지의 파일 기반 secret](https://hub.docker.com/_/mysql/).

## 2026-11-03 확인 결과

| 검증 | 이번 결과 |
| --- | --- |
| 신규 실제 인프라 시험 | 4/4 통과, MySQL 마이그레이션 V1~V27 포함 |
| 기존 인증 통합 시험 | 15/15 통과, H2 + 별도 Redis Testcontainer |
| 전체 fast 회귀 | 384 통과, 기존 15 skip, 실패/error 0 |
| Python 실행·검증 도구 | 11/11 통과 |
| 모의 AI 서버 단위 시험 | 4/4 통과 |
| API 정상 기동·종료 | 통과, health/liveness 및 API 문서 HTTP 200 |
| Kafka 중단 중 API 기동 | broker stopped 상태에서 liveness UP 확인 |
| 관리 wrapper | up/verify/start-app/stop-app/down 및 재기동 통과 |
| 컨테이너 재생성 후 보존 | MySQL migration 이력 27행 hash, Redis marker, mock journal hash 일치 |

실행 이미지의 실제 버전은 MySQL 8.0.46, Redis 7.0.15, Kafka 3.7.0, Python 3.12.16이다. API는 Java 21/Gradle 8.14.3 기준이다. 컨테이너 ID/이미지 ID/digest 및 자원 상태는 manifest에 기록한다.

독립 검토의 Important 2건(상위 환경변수로 로컬 격리 우회, 삭제된 소스 잔존)을 수정하고 회귀 시험의 실패→통과를 확인했다. 새 Minor 보류 항목은 없다. E01에서 기록한 전체 verifiedCheck 보고 Minor 2건은 별도 후속 항목으로 유지한다. 최종 원자료의 집계/연결/일관성은 작성자가 확인했으며 독립 검토 이후 원자료를 갱신했다.


## 후속 E03 CDC 상태 (2026-11-03)

CDC probe를 통과해 발행 방식을 CDC로 선택했다. [선택 근거와 한계](../adr/0001-outbox-relay.md)를 따른다. 선택한 조합은 Debezium3.4.3.Final/Connect4.1.1과 기존 Kafka3.7.0/MySQL8.0.46이다. worker는 선택 profile `cdc`이며 REST는 127.0.0.1:18083이다.

```powershell
.\scripts\reliability\probe-cdc.ps1 -RunId new-cdc-probe-01
```

probe는 서비스 정지/교체를 포함하므로 전용 로컬 환경에서 단독으로 실행한다. 초기 준비 중 MySQL 설정 적용이 필요하면 전용 MySQL이 재기동될 수 있다. 기존 업무 원본 checkout/다른 PC 서비스를 대상으로 하지 않는다. 전체 `down`은 CDC worker도 종료하며 named volume은 유지한다.

Linux의 cnf는 0644로 동기화한다. `mysql-cdc.cnf`는 ROW/FULL/server-id/binlog 보존을 명시하며 worker 기동 전에 실제 서버 변수를 확인한다. Connector가 업무 Outbox에 연결된 상태는 아니며 probe 종료 시 시험 소유 connector는 제거한다. 이후 K01C에서 업무 발행기를 구현한다.
