# ADR 0001: Outbox 발행 방식은 CDC를 사용한다

- 날짜: 2026-11-03
- 상태: E03 로컬 probe 통과에 따른 선택. 업무 발행기는 후속 K01C에서 구현한다.
- 근거 실행: `perf/results/20261103-cdc-04/cdc-decision.json`

## 결정과 적용 범위

WSL Ubuntu의 전용 MySQL/Kafka에서 Debezium을 사용한 CDC가 실행 가능함을 확인했으므로 `relayMode=cdc`를 선택한다. 이번에는 기술적 불가 사유가 확인되지 않아 폴링 대안을 선택하지 않았다. 업무 Outbox, Event Router, Inbox, durable job은 아직 구현하지 않았으며 이번 전달 시험을 업무 중복 방지나 exactly-once 보장으로 해석하지 않는다.

실제 검증 조합은 MySQL 8.0.46, Kafka broker 3.7.0, Debezium 3.4.3.Final, Kafka Connect 4.1.1이다. 이미지는 기존 `infra/portfolio-images.lock.json`과 추가 `infra/cdc/images.lock.json`에 digest로 고정한다. 공식 호환 목록만으로 결정하지 않고 이 조합의 실제 I/O와 재개 결과를 근거로 선택했다.

## 실제 시험

전용 `sentinel_cdc_probe.outbox_probe`를 사용했다. 업무 DB의 Flyway V1~V27과 기존 원본 데이터는 변경하지 않았다. CDC reader는 이 DB의 SELECT/SHOW VIEW 및 필요한 전역 replication 권한만 가지며, writer는 SELECT/INSERT만 가진다. Connector include-list로 게시 범위를 probe 테이블에 한정한다. MySQL replication 권한 자체가 DB별로 제한된다는 의미는 아니다.

| 시나리오 | 관찰 결과 |
| --- | --- |
| Connector 등록 전 커밋 | 초기 snapshot 1건 수신 |
| 정상 상태 개별 커밋 | 10건 모두 수신 |
| 개별 롤백 | 10건 모두 미전달, DB 행 없음 |
| worker 중단 중 커밋 | 5건, worker 컨테이너 교체 후 모두 수신 |
| broker 중단 중 커밋 | 5건, broker 컨테이너 교체 후 모두 수신 |
| 전체 eventId 대조 | DB 21행, 예상/수신 고유 ID 21개 일치 |
| 재시작 상태 | source checkpoint 재개, 초기 snapshot 재수행 없음, history hash 동일 |
| 내부 토픽 | config/offset/status compact, 각 1 partition |

이 실행의 추가 중복 레코드는 0건이었다. 유한한 정상 종료·컨테이너 교체 시험 결과이며, 강제 종료·전원 차단·다중 broker 장애에서 중복이나 유실이 없다는 보장이 아니다. 중복 처리는 후속 Inbox와 업무 고유 제약에서 검증한다.

독립 검토에서 발견한 두 예외 경로도 보강했다. 기존 발행기 잠금이 polling일 때 CDC 성공 결과가 나오더라도 결정 파일은 undecided와 충돌 사유를 기록하고 기존 잠금을 유지한다. 정지 제어가 부분 성공 후 실패해도 정지 시도 전에 복구 책임을 등록한다. 단위 회귀 2개와 실제 worker/broker 정지 직후 예외를 주입한 통합 시험으로 복구를 확인했다.

최종 CDC JUnit은 3/3 통과했다(전제 조건, 정상 전달/재개, 실제 부분 정지 실패 복구). Python 도구 17/17이 통과했으며 여기에는 잠금 충돌·불완전 증거 선택 거부가 포함된다. 이는 독립 검토 후의 회귀 검증이며 재검토 통과를 별도로 주장하지 않는다.

## 설정과 보존 정책

- MySQL: server-id 223344, ROW/FULL, log-bin=mysql-bin, binlog 보존 864000초(10일). Debezium replication client ID는 223345다.
- Connector는 `cdc` Compose profile의 별도 worker이며 REST는 127.0.0.1:18083에만 게시한다. heap 512MiB, 컨테이너 1GiB 제한이다. 기본 `up`은 worker를 새로 시작하지 않으며 전체 `down`은 CDC profile도 종료한다.
- config/offset/status는 Kafka 내부 compact 토픽으로 보존한다. schema history는 별도 단일 partition 토픽으로 만들고 delete 정책에 retention.ms/bytes=-1을 설정한다. Kafka named volume을 보존하며 worker의 로컬 파일 시스템에 offset을 의존하지 않는다.
- 초기 정책은 `snapshot.mode=initial`, `snapshot.locking.mode=none`이다. 시험 중 schema DDL을 바꾸지 않고 snapshot 수신 이후 정상 쓰기를 시작하는 제한된 fixture에서 확인했다. 실제 업무 테이블의 동시 DDL/초기 이관 정책까지 검증한 것은 아니다.
- Probe 계정 비밀번호는 Ubuntu private state에서 읽는다. Connector JSON에는 제한된 FileConfigProvider 참조만 넣고 원문 비밀번호를 넣지 않는다. 필요한 secret 한 파일만 읽기 전용으로 마운트한다.
- Probe는 자신의 connector만 삭제하며 결과 topic/history와 probe 행은 증거 확인을 위해 보존한다. 실제 Outbox 삭제/정리 정책은 K01C 이후 전달 위치·업무 상태와 함께 설계하고 시험한다. 업무 row를 임의 TTL로 삭제하지 않는다.

## 구현 중 확인한 문제

1. Windows에서 복사한 cnf의 0777 권한 때문에 MySQL이 파일을 무시했다. Linux 실행 사본의 cnf를 0644로 맞추고 실제 서버 변수로 적용 여부를 확인했다.
2. Connector 등록 직후 status가 일시적으로 404였다. 404/409/503에 한해 제한 시간 안에서 상태 준비를 기다리며, task FAILED나 전달 누락은 성공으로 처리하지 않는다.
3. 처음에는 envelope의 `source.pos`와 Connect 저장 offset의 `pos`를 비교해 잘못된 timeout을 만들었다. Debezium offset은 재시작 지점과 event/row skip을 함께 저장한다. 전체 체크포인트를 보관하고 실제 worker 교체 후 재개·진행·snapshot 재수행 여부로 검증했다. 두 위치의 숫자를 같은 의미로 비교하지 않는다.

## 재실행

```powershell
.\scripts\reliability\probe-cdc.ps1 -RunId new-cdc-probe-01
```

Windows wrapper는 Ubuntu의 Python 관리 도구로 전달한다. Ubuntu에서 직접 실행하려면 현재 작업 사본의 `scripts/reliability/cdc_probe.py --run-id <새 ID>`를 python3로 호출한다. 같은 run-id의 결과는 덮어쓰지 않는다. 모든 필요한 시험과 관찰이 통과해야 cdc-decision.json이 CDC를 선택하며, setup/네트워크 오류나 빈 결과만으로 폴링을 선택하지 않는다. 선택 기록은 Ubuntu state의 relay-mode.json에도 보존한다.

## 후속 위험과 작업

Binlog 보존 기간을 넘긴 중단, history/offset 토픽 또는 Kafka volume 유실은 이번 범위 밖이다. 이를 정상 재개처럼 처리하거나 임의 최신 위치로 넘어가면 안 된다. 후속 업무 원장과 기존 eventId를 대조하는 재발행·복구 절차를 구현해야 한다. 이번 단계는 CDC 운영 가능성의 로컬 근거이며 실제 서비스 수준의 복구 보장은 아니다.

다음은 D01 실제 자료의 manifest/canonical parser/사전 검증이며, 업무 CDC 발행기는 계획상 K01C를 따른다. E01의 전체 verifiedCheck 보고 보완 사항과 아직 없는 reliability 시험은 별도 후속 항목이다.

공식 근거: [Debezium 3.4 호환 목록](https://debezium.io/releases/3.4/), [MySQL connector](https://debezium.io/documentation/reference/3.4/connectors/mysql.html), [3.4.3 offset 구현](https://github.com/debezium/debezium/blob/v3.4.3.Final/debezium-connector-binlog/src/main/java/io/debezium/connector/binlog/BinlogOffsetContext.java), [Kafka FileConfigProvider](https://kafka.apache.org/41/configuration/configuration-providers/).


## 독립 검토와 후속 확인

검토에서 발견한 잠금 충돌과 부분 정지 복구 문제는 각각 실패 회귀를 확인한 뒤 수정했다. 재검토를 추가로 요청하지 않고 관련 전체 Python 도구 17개, 복구 guard 단위 2개, CDC 통합 3개, 기존 realInfraTest 19개를 실행해 통과했다. realInfraTest는 기존 인증 H2 프로필 15개와 실제 MySQL/Redis/Kafka/HTTP 4개를 구분한다. 이번 fastTest 실행은 복구 guard 2개만 선택했으며 전체 fast 회귀를 새로 수행했다는 의미가 아니다.

처음 legacy 회귀를 실행할 때 검증용 runner에 앱 실행 환경(MySQL URL)을 잘못 전달해 인증 시험이 실패했다. 해당 실패 로그/XML을 보존했고, 앱 설정을 주입하지 않는 시험 환경으로 바로잡아 다시 검증했다. 애플리케이션 코드를 수정하거나 실패 시험을 제외해서 통과시킨 것이 아니다.

버전·이미지·소스 지문·현재 상태를 정리한 최종 manifest는 작성자가 검증했으며 독립 검토 범위에는 포함되지 않았다. 기본 이미지들은 기존 lock 파일을 참조한다. E01/E02의 일반 변경과 E01의 보고 보완 두 항목은 이번 검토에서 재판정하지 않았다.
