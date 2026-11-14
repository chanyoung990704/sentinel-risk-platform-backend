# 맞춤 보고서 화면·상태 조회 측정

승인 범위는 R07→M03→M04다. 기존 프런트 보관본에서 `frontend/`로 소스만 복원했으며 원본은 변경하지 않았다. 실행은 모두 WSL2 Ubuntu다. 원격 AI 대신 실제 HTTP 모의 PDF 서버를 사용한다.

## 화면 실행

`scripts/reliability/frontend_runtime.py`는 소스를 `/home/chanyoungpark/.local/share/sentinel/workspaces/r07-frontend`에 복사한다. `node_modules`, `dist`, `.git`, `.env*`, 시험 결과는 동기화하지 않는다. 원본 환경 파일을 복사하지 않는다.

```bash
python3 scripts/reliability/frontend_runtime.py
cd ~/.local/share/sentinel/workspaces/r07-frontend
npm ci
VITE_API_BASE_URL=http://localhost:18080 npm run dev -- --host 127.0.0.1 --strictPort
```

기존 local 백엔드·Compose·CDC가 준비되어야 한다. 테스트 전용 `browser_runtime.py start off`는 20개 소유 사용자와 비공개 임의 자격 정보를 생성한다. 정식 실행용 사용자 생성기가 아니다. 소유 앱을 `stop`한 뒤에만 Gradle을 실행한다. fixture는 한 실험의 전후 측정 동안 동일하게 사용한다. 사용자 승인으로 시험 자료를 정리한 뒤에는 다음 start가 새 시험 계정을 생성하므로 새 run ID와 새 참조 요청을 준비한다. 다른 사용자/실제 자료는 정리하지 않는다.

## 브라우저 계약

한 의도에 UUID 키 하나를 사용하고, 수신 응답 유실 시 같은 키와 본문으로 재전송한다. 재전송에도 서버에서 요청 하나를 유지한다. 완료/실패 후 `새 요청 작성`은 새 키를 만든다. 입력과 requestId는 브라우저 sessionStorage의 사용자·기업별 공간에 보관하며 로그아웃/인증 만료 시 지운다. 이 저장소는 서버의 권한 검사를 대체하지 않는다.

상태 확인은 1초 간격이며 terminal 상태에서 멈춘다. 상태 응답의 version이 낮아지면 화면을 되돌리지 않는다. PDF는 인증 헤더를 넣어 받고 object URL을 해제한다. 401은 로그인 재시도, 404는 요청/권한 없음, 409는 새 의도 작성, 429/503은 같은 의도 재전송을 안내한다. 실제 UI 입력은 React text/textarea로 표시하고 HTML로 실행하지 않는다. 기존 보고서 버튼은 유지했다.

저장된 의도를 읽을 수 없으면 새 접수를 막고 설정 확인/새로고침을 안내한다. 최초 key/body 저장이 실패해도 POST하지 않는다. key는 저장됐지만 requestId 저장만 실패한 경우는 같은 키 재전송으로 복구하며 저장 경고를 조회 성공으로 지우지 않는다. 로그아웃의 보고서 저장소 정리 실패는 인증 세션 삭제를 막지 않는다. FAILED 뒤 운영자가 재개한 상태는 화면을 새로고침해 같은 requestId로 다시 조회한다.

```bash
VITE_API_BASE_URL=http://localhost:18080 npm run test:e2e -- --project=chromium custom-report.spec.ts
npm run build
```

UI 계약 시험의 API 대체와 실제 로그인/JWT/CORS/Kafka/worker/파일 시험은 이름으로 구분한다. 민감 정보가 포함될 수 있는 trace/video는 저장하지 않는다. 시험 사용자 자격 정보는 Ubuntu private 파일에만 둔다.

## 수치의 의미

`scripts/perf/report_benchmark.py`는 기존 M01 Python 발생기·P6Spy 계측을 확장한다. JMeter를 실행한 결과가 아니다. 20 사용자 로그인은 측정 전에 하고 접수/상태/완료 지연은 별도로 보관한다. DB 시각은 트랜잭션 내부 기록 시각으로 정확한 commit 순간을 의미하지 않는다. 모의 지연 200ms/2s는 실제 AI 지연이 아니다.

원자료는 `perf/results/20261112-r07-m03-m04/<runId>/` 아래 manifest, requests.csv, lifecycle.csv/json, observations.json, runtime.csv/json, reconciliation.json에 보관한다. 응답 실패와 미완료를 삭제하지 않는다. SQL sampling/인증 준비는 API latency/쿼리 계측 밖이다. 원자료·결과·포트폴리오 참고 문서는 Git 제외이며 실행 계약/시험 코드는 공개 가능한 내용만 기록한다.

발생기는 Ubuntu native filesystem에 먼저 기록하고 종료 뒤 보관본으로 복사한다. `windows.json`은 wall/monotonic 준비·본측정 경계와 실제 부하 중단 시각을 기록한다. 고정 도착률도 마지막 송신 뒤 구간 끝까지 대기한다. P95는 본구간에 시작한 요청의 지연 분포이며 성공 TPS는 정확한 본구간에 응답이 끝난 요청 수/120초다. 준비 중 시작해 본구간에 끝난 carry-in과 본구간 시작 후 종료 뒤 끝난 carry-out을 따로 보관한다. 가드로 단축된 구간의 정규 TPS는 null이며 drain 완료 수를 처리량으로 바꾸지 않는다.

준비 예외도 ExitStack으로 소유 스레드·CSV를 정리한 뒤 partial 자료를 보관한다. 현재 알려진 제한은 READ HTTP/DTO 오류가 표본에는 남지만 즉시 중단 guard와 연결되지 않는 점이다. 해당 실패에서는 설정된 기간까지 계속될 수 있다. 이번V2 READ는오류0이며일반manifest의HTTPerror가드설명은이예외를정확히반영하지못한다.

```bash
python3 scripts/perf/report_benchmark.py --run-id m03-off-a1-r1 --rate 1 --latency 200 --cache off
```

warmup30초/측정120초/최대drain180초, worker4, pool10, heap512MiB, USER/GLOBAL cap10000을 고정한다. 도착률1/5/10/20과 반복3회는 각각 독립 run이다. 클라이언트 미완료 관측200건, pool pending 양수 연속5표본(약1초 간격), 가용메모리512MiB 미만, HTTP 오류, 클라이언트 동시 실행40건 한도에서 높은 부하를 중단하고 기존 접수 집합을 보존·대조한다. 미완료 관측은 순간 DB job backlog의 정확값이 아니다. 포화 가드로 단축된 조건은 120초 정상 처리량 성과로 해석하지 않는다.

상태 READ 비교는 동시성10·같은 완료요청100개·소유사용자20명으로 off/on/on/off 네 블록을 각3회 실행한다. 블록마다 같은 소스 앱을 재기동하고 on은 기존 그룹의3개 파티션 처리 완료 barrier를 확인한 뒤 시작한다. 각 run의 DB 버전/generation/attempt와 전체 HTTP 상태 응답을 먼저 대조하며 동일 참조 응답 hash를 보관한다. 준비 전에 해당100개 키만 비워 cold 상태를 맞추고30초 준비 뒤120초를 측정한다. 다른 동시성이나 보고서 생성 속도 개선으로 일반화하지 않는다. 정확한 group lag/outbox age/job backlog의 초당 수집을 주장하지 않으며 수집하지 못한 항목은0으로 채우지 않는다.

## M04 구현 전 확인한 경계

기존 인증은 Redis에서 토큰 블랙리스트와 전체 로그아웃 시각을 조회한다. 같은 Redis 전체를 중단하고 인증 검사를 우회해 상태 API 200을 만드는 것은 캐시 fallback 검증이 아니다. 상태 캐시는 별도의 로컬 Redis 인스턴스/연결 timeout을 사용해 이 의존성을 분리한다. 캐시 Redis 장애에서 DB fallback을 검증하며 인증 Redis 전체 장애의 fail-open은 도입하지 않는다.

캐시가 켜져도 ACTIVE 사용자/삭제 여부는 DB에서 확인하고 PDF 다운로드는 최신 DB 상태를 확인한다. 따라서 상태 cache hit에서도 최소한의 DB 권한 확인은 남는다. 현재 상태 조회가 이미 한 SQL이라면 쿼리 수가 줄지 않거나 Redis 왕복으로 더 느려질 수 있다. TTL2초와 projector의 비용까지 보고 기본 채택 여부를 결정하며, 효과가 없으면 기본 off로 남긴다. 성공 수치에 맞춰 권한 확인을 제거하지 않는다.

같은 권한 확인 SQL에서 현재 `state_version`도 읽어, 캐시 버전과 다르면 DB 전체 상태를 다시 조회한다. 이벤트 소비가 지연되어도 과거 상태를 화면에 반환하지 않으며, cache hit에서도 이 버전 확인 SQL 한 번은 남는다. Lua는 저장 중 버전 역전을 막고, 상태 projector는 모든 wire 상태를 현재 DB 기준으로 재구성한다. TTL은 저장 후 만료 시간이며 외부 서비스 SLA를 뜻하지 않는다.

캐시용 Redis는 `status-cache` Compose profile의 `status-cache-redis` 서비스(127.0.0.1:16380, memory128MiB/maxmemory64MiB, 영속화 없음)를 사용한다. 인증 Redis(16379)와 분리한다. 명령/연결 timeout200ms, 연결 단절 시 큐 적재 거부·재연결, 대기 명령 상한128이다. cache-off가 기본값이다.

```bash
python3 scripts/reliability/cache_runtime.py up
./gradlew realInfraTest --tests '*ReportStatusCache*' --console=plain --no-daemon
./gradlew reliabilityTest --tests '*StatusCacheFailureTest' --console=plain --no-daemon
```

상태 소비자는 `sentinel-custom-report-status-cache-v1` 별도 group과 MANUAL_IMMEDIATE ACK를 사용한다. 새 그룹은 latest에서 시작하고 기존 offset은 유지한다. 과거 상태는 DB miss fill로 재구성하며 신규 상태 이벤트는 DB 조회와 Redis 갱신 뒤 ACK한다. Redis/DB 실패는 1초 간격 재시도로 ACK를 보류한다. 잘못된 메시지는 소비자별 격리 원장과 DLT Outbox commit 뒤 ACK한다. 상태 격리 메시지를 보고서 접수 메시지로 재발행하는 관리 명령은 409로 차단한다.

2026-11-12 비교에서 기본 cache-off를 유지하기로 했다. 각 모드6회 중앙값은 P95 36.122→32.393ms, 성공TPS446.379→483.413으로 좋아졌지만, 반복 범위가 크게 겹치고 요청당 평균SQL은1→1.0975로 증가했다. 약3.73ms 차이와 별도 Redis/projector·cold miss의 비용을 함께 판단한 해당 경로의 결론이다. 모든 Redis 캐시의 기각이나 통계적 유의성 검정 결과를 의미하지 않는다. 기능은 명시적으로 켜 실험할 수 있으며 권한 확인을 줄여 성능을 높이지 않는다.
