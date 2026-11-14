# 실제 MySQL 조회 측정 실행

앱·Gradle·수집기·부하 발생기는 모두 WSL Ubuntu에서 실행한다. 기존 `perf` 프로필은 H2 실험이므로 이번 실제 데이터 측정에서는 사용하지 않는다. `local,portfolio-perf`는 루프백에서만 허용된다. 실제 금융 API나 AI 호출은 이 조회 경로에 포함하지 않는다.

## 실행 순서

1. `scripts/reliability/local.ps1 -Action Verify`로 로컬 인프라를 확인한다. 포트·볼륨을 초기화하지 않는다.
2. `scripts/perf/Start-PortfolioRun.ps1 -Action Start`로 소스를 Linux 파일시스템에 동기화하고 계측 앱을 빌드·기동한다. 같은 작업공간에서 Gradle을 동시에 실행하지 않는다.
3. `scripts/perf/Invoke-ReadBenchmark.ps1 -RunId read-before -Concurrency 1,10,50,100 -WarmupSeconds 30 -DurationSeconds 120 -Repeat 3 -CacheMode off`로 측정한다.
4. `perf/results/<runId>-c<동시성>-r<반복>/`의 manifest·requests·runtime·guard·summary를 확인한다. 같은 ID의 원자료/요약을 덮어쓰지 않는다.
5. SQL을 변경하거나 테스트를 다시 컴파일하기 전에 `scripts/perf/Start-PortfolioRun.ps1 -Action Stop`으로 소유 앱을 종료한다. 변경 후 응답 동등성을 먼저 검증하고 같은 조건으로 재기동·측정한다.

처음 실행할 때 실제 ROLE_USER를 로컬 회원가입/로그인으로 만든다. Turnstile만 전용 로컬 계측 프로필에서 모의 허용한다. 비밀번호와 토큰은 Linux 비밀 디렉터리 또는 프로세스 메모리에만 존재하며 원자료에는 쓰지 않는다. 메트릭 수집도 동일한 JWT를 사용하고 보안 경로를 공개하지 않는다. 매 run 로그인은 준비 구간 밖에서 새로 수행한다.

최초 가입이 실패해도 같은 자격 파일을 보존한다. 재실행 시 로그인을 먼저 확인하고 미생성이면 가입을 다시 시도한다. 가입 commit 뒤 응답이 유실돼도 로그인으로 생성 여부를 확인한다. 반복 횟수는 한 번의 가입/로그인 확인으로 제한하며 인증 잠금 정책을 우회하지 않는다.

SQL 수집은 이 작업이 만든 고유한 SELECT 전용 계정을 사용한다. 자격은 Linux 비밀 디렉터리에만 저장하고, 최초 준비 시 기존 로컬 MySQL 관리 권한으로 SELECT만 부여한다. SQL 비밀번호를 명령행 인수나 원자료에 넣지 않는다. SQL 파일은 단일 SELECT/SHOW/EXPLAIN SELECT만 지원하며 끝의 세미콜론 하나는 허용한다. 다중 문장·주석·백슬래시·파일 출력/클라이언트 지시어는 보수적으로 거부하므로 일반 SQL 덤프를 입력하지 않는다.

## 측정 의미

대상은 기존 `GET /api/companies/{id}/overview?quarterKey=20253`이다. 활성 실제 snapshot의 2,083개 기업 중 정렬한 ID에 등간격으로 분포한 20개 기업을 선택하고, 각 스레드가 순환 조회한다. 분기·기업·전체 업무 응답 hash를 고정한다. API 응답 봉투의 `timestamp`만 hash에서 제외하며 업무 데이터의 시각이나 NULL은 제외하지 않는다.

SQL 수는 P6Spy의 실제 JDBC execute callback을 요청 스레드 범위에서 센다. 배치 호출은 1회다. 필터가 응답 본문을 버퍼링해 직렬화까지 포함한 수를 헤더로 전달하므로 계측 비용이 존재한다. 전후 같은 계측을 적용한다. health·인증 준비·Prometheus 수집은 대상 요청 범위 밖이며, 대상 요청 안에서 실행된 인증 SQL은 포함한다. 비동기 작업이나 다른 스레드의 SQL 수를 의미하지 않는다.

실행기는 Ubuntu의 Python 표준 HTTP 클라이언트이며 고정 동시성 closed-loop 방식이다. JMeter 설치를 전제하지 않는다. `perf/jmeter/portfolio-read.jmx`는 선택 실행 템플릿으로, 이 계산기와의 JTL 변환·동등성은 검증하지 않았다. JMeter 실행 결과로 서술하지 않는다. 맞춤 보고서용 `Invoke-ReportBenchmark.ps1`은 M03 이전에 수치를 생성하지 않고 미구현으로 종료한다.

성공 TPS는 성공 표본 수를 `max(start+elapsed)-min(start)`로 나눈다. 성공 지연과 실패 지연은 따로 집계하고, 본 측정 중 시작된 요청은 마지막 응답까지 포함한다. 준비 요청은 제외한다. HTTP 오류율·예상 거부·202 접수는 업무 완료와 구분한다. P99는 성공 표본 2,000개 미만일 때 참고값이다. 미수집은 0으로 채우지 않는다.

## 부하 상승 중단과 한계

각 run 후 HTTP 오류 1건 이상, 응답 hash 불일치, SQL 헤더 미수집, 필수 runtime 수집 실패, 1초 표본에서 5회 연속 pool pending>0, 가용 메모리 512MiB 미만 또는 앱 RSS 1,536MiB 초과면 다음 부하로 올리지 않는다. 중단 판단은 `guard.json`에 보존한다. 이 조건은 측정 중 요청을 취소하는 과부하 제어가 아니라 다음 run의 부하 상승을 막는 실험 가드다.

서버·MySQL·Redis·Kafka·발생기가 같은 PC 자원을 공유한다. client CPU 누적값도 남겨 발생기 병목 가능성을 판단한다. 이 환경의 처리량을 실서비스 용량으로 일반화하지 않는다. cold OS/DB buffer 성능, 실제 AI 처리시간, 캐시 효과, durable 보고서 완료는 이번 측정 범위 밖이다. `runner-finished.json`이 없거나 행 수가 맞지 않는 실행은 완료로 계산하지 않는다.
