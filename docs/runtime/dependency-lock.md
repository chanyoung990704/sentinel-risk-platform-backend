# Ubuntu 실행 기준선 (E01)

2026-11-03부터 애플리케이션, Gradle, Docker 기반 시험은 WSL2 Ubuntu 안에서 실행한다. Windows는 파일 편집과 명령 전달에 사용한다. 실제 인프라 구성과 앱 기동은 E02 범위다.

## 확인한 환경

| 항목 | 현재 기준 |
| --- | --- |
| Ubuntu | 26.04 LTS / WSL2 |
| Java | OpenJDK 21.0.12.1 |
| Gradle Wrapper | 8.14.3 |
| Spring Boot | 3.5.9 |
| Spring Framework / Spring Kafka | 6.2.15 / 3.3.11 |
| Kafka client / Testcontainers | 3.9.1 / 1.21.4 |
| JUnit / Mockito | 5.12.2 / 5.17.0 |
| Lettuce | 6.6.0.RELEASE |
| Docker client/server | 29.1.3 |
| Docker Compose | 2.40.3+ds1-0ubuntu1 |
| Python | 3.14.4 |
| 실행 위치 | `/home/chanyoungpark/.local/share/sentinel/workspaces/e01-wsl` |
| 원본 commit | `f6f41169f810eab66f791fb307d0e3e600ba06de` + manifest의 변경 파일 지문 |

이 문서는 E01의 해석된 의존성 목록과 실행 조건 기록이다. Gradle dependency locking을 활성화한 lockfile은 아니다. 전체 의존성 출력과 `spring-kafka` 선택 근거는 해당 실행의 `dependency-tree.log`, `spring-kafka.log`에 보관한다. 라이브러리 업데이트는 이번 작업에 포함하지 않았다.

## 시험 경계

| task | 선택 범위 | 현재 해석 |
| --- | --- | --- |
| `test` | 기존 옵션/태그 필터 유지 | Docker 의존 시험도 포함 가능 |
| `fastTest` | integration/realinfra/cdc/reliability 제외 | H2, mock, loopback 시험. Kafka admin/listener 비활성 |
| `realInfraTest` | integration 또는 realinfra, cdc/reliability 제외 | 기존 Redis Testcontainers 인증 시험 포함. MySQL/Kafka 검증 여부는 개별 시험으로 판단 |
| `cdcTest` | cdc 태그 | E01에서는 시험 미구현 |
| `reliabilityTest` | reliability 태그 | E01에서는 시험 미구현 |
| `verifiedCheck` | fast+realinfra+reliability, CDC 선택 시 cdc 추가 | `-PoutboxMode=cdc` 또는 `polling` 필요. 누락/빈 결과/전체 skip/실패는 성공 불가 |

새 Test task는 fork=1, forkEvery=0, maxHeap=512m로 실행한다. 기존 Mockito javaagent와 test 보안 프로필을 공유한다. 새 task는 매 실행 XML을 갱신한다. `verifiedCheck`는 skip도 허용하지 않는다. 기존 Disabled 시험이 있어 현재 전체 검증 완료를 주장할 수 없다. 폴링 모드의 CDC는 명시적 N/A이며, 아직 릴레이 방식은 채택하지 않았다.

## Ubuntu에서 재실행

실행 사본에 수정 소스를 먼저 동기화한다. `.env`, 업로드, 원본 데이터, `.git`, `build`, `.gradle`은 복사하지 않는다. 복사 대상은 `src`, `gradle`, `scripts/reliability`, `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`이다. 원본 경로의 자료는 수정하지 않는다.

```bash
cd /home/chanyoungpark/.local/share/sentinel/workspaces/e01-wsl
python3 -m unittest discover -s scripts/reliability/tests -p 'test_*.py'
./gradlew fastTest --tests '*ExcelMetricParserTest' --no-daemon --console=plain
./gradlew fastTest --no-daemon --console=plain
python3 scripts/reliability/verify_test_results.py --require fastTest --output build/reports/fast-summary.json
python3 scripts/reliability/capture_baseline.py --compare /mnt/c/Users/chanyoungpark/.codex/worktrees/sentinel-wsl-baseline/sentinel-risk-platform-backend --output build/reports/source-fingerprint.json
./gradlew dependencyInsight --dependency spring-kafka --configuration runtimeClasspath --console=plain
```

결과 검증기의 `--since <실행 시작 Unix seconds>`로 오래된 XML을 거절할 수 있다. 실제 인프라 시험이 준비된 뒤 `./gradlew verifiedCheck -PoutboxMode=cdc` 또는 `polling`을 실행한다. 현재 이 명령은 완성된 검증이 아니며, E02 이후 시험을 추가해야 한다. Python 검증기의 exit code는 통과(일부 skip 포함 가능)=0, 시험 실패=1, 누락/빈 결과/불일치=2다. Windows용 PowerShell 파일은 Python 진입점에 인수를 전달하는 호환용이다.

## 증거와 제한

현재 실행 증거는 `perf/results/20261103-e01-wsl/`에 저장한다. 해당 디렉터리는 Git 제외 대상이며 원자료는 별도 보관해야 한다. manifest에는 소스 지문, 실행 명령/종료 코드/시각, suite별 결과, 제외·skip 근거를 연결한다. 실행 시간은 빌드/회귀 소요 시간이고 API 지연이나 처리 성능 수치가 아니다.

Ubuntu의 기존 ExcelMetricParserTest는 실제 1개 실행·통과했다. 과거 Windows의 class loading 실패가 Ubuntu에서 재현되지는 않았다. fastTest 추가 전 task 부재 실패를 확인한 뒤, 같은 시험을 새 task로 실행했다. 기존 소스의 Disabled는 보존하며 외부 AI에 직접 요청하는 시험도 활성화하지 않는다. 실제 DB/Kafka/Redis 및 장애 복구 검증 결과는 이 기준선으로 대신하지 않는다.


## 2026-11-03 실제 결과

- 기존 `test`와 신규 `fastTest`에서 ExcelMetricParserTest 각각 1개 실행·1개 통과.
- 전체 `fastTest`: 120 suite, 399개 발견, 384개 실행·통과, 15개 skip, 실패/error 0개. Gradle 소요 시간 3분 37초이며 서비스 성능값이 아니다.
- 원본 `src` 550개 파일 대비 실행 사본 해시 불일치 0건. 전체 실행 입력 해시는 manifest에 기록한다.
- 기존 `AuthIntegrationTest`는 integration 태그로 fastTest에서 제외하며 E02에서 Redis 컨테이너 환경으로 검증한다.
- CDC·장애 task는 실행했지만 대상 시험이 없어 빈 결과였다. Gradle 자체의 성공과 달리, 결과 검증기는 전체 검증에 exit 2(INCOMPLETE)를 반환했다. 실제 인프라 시험은 E01에서 실행하지 않았다.

| 건너뛴 기존 시험 | 수 | 확인한 근거 |
| --- | --- | --- |
| AiServerClientTest 외부 서버 시험 | 1 | Disabled에 실제 외부 서버 수동 시험 사유 명시 |
| CompanyAiControllerTest PDF 다운로드 시험 | 1 | 메서드에 완전한 이름의 Disabled, 이유 미기재 |
| ReportFileDownloadControllerTest | 6 | 클래스 Disabled, 이유 미기재 |
| ReportMetricQueryControllerTest | 6 | 클래스 Disabled, 이유 미기재 |
| ReportPdfPublishControllerTest | 1 | 클래스 Disabled, 이유 미기재 |

15개를 통과 수에 넣지 않는다. XML의 skip 사유는 비어 있어 위 표의 소스 근거로 보완했다. 이유 미기재 14개는 이번 작업에서 임의로 활성화하거나 정상 동작을 추정하지 않았다. 구체적인 메서드/표시 이름은 `test-summary.json`, 소스 위치는 `source-fingerprint.json`에 남긴다.


## 전체 검증 보고의 후속 보완점

독립 검토에서 중대한 문제는 발견되지 않았고 다음 보고 기능 2건은 보류했다.

- polling을 선택한 `verifiedCheck` 자동 JSON에는 현재 CDC N/A와 제외 이유가 출력되지 않는다. 첫 폴링 전체 검증 전에 보강한다. 현재 릴레이 방식은 미결정이며 dry-run 결과를 채택 근거로 쓰지 않는다.
- 의존 Test task가 실패하면 요약 Exec은 실행되지 않아 이전 요약 JSON이 남을 수 있다. Gradle 자체는 실패하므로 성공 판정은 하지 않는다. CI에 연결하기 전 실패 시 요약 경로를 보강하며, 그때까지 실행 종료 코드와 시각이 확인된 개별 task XML을 함께 확인한다.

위 두 제약은 이번 fast 회귀 수치를 바꾸지 않는다. E02 이후의 실제 인프라·CDC·장애 동작과 데이터 적재 정합성은 아직 검증하지 않았다.

후속 상태(2026-11-03): E02 실제 인프라 구성과 모의 AI/API 기동 검증을 완료했다. 최신 결과는 [로컬 실행 환경](local-environment.md)을 따른다. 위 E01 기록은 당시의 범위이며 CDC 선택·데이터 적재·업무 장애 복구 검증은 여전히 후속 작업이다.

후속 시험 구성(2026-11-04, D03): fork1/heap512m는 유지하고 portfolio Test task의 `spring.test.context.cache.maxSize`를16으로 제한했다. 전체 fast 실행에서 다수 JPA 컨텍스트를 보관하면서 heap520574/524288KiB와 지속 GC가 관측되어 소유 worker를 중단했다. 재실행 final-regression-02는 realInfra48/48·fast426pass/기존15skip(총441)·실패/error0으로 통과했다. 중단 실행은 통과 근거에 넣지 않는다. 원자료는 `perf/results/20261104-d03-snapshot/stalled-fast-*`, 최종 실행 결과는 D03 문서에 기록했다. 애플리케이션 처리량 개선으로 해석하지 않는다.
