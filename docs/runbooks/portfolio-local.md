# 로컬 운영·정지 복원 절차

이 문서는 Windows의 관리 worktree를 원본으로 유지하고 앱·Gradle·Docker를 WSL2 Ubuntu 안에서 실행하는 절차다. 원격 AI와 실제 서비스 배포를 포함하지 않는다. 원자료·로그·SQL dump·시험 자격은 공개 문서에 붙이지 않는다.

## 환경과 상태 확인

Java21/Gradle8.14.3, 기존 잠금 파일의 MySQL/Kafka/Redis/Debezium 이미지를 사용한다. API18080, MySQL13306, 인증Redis16379, 상태Redis16380, Kafka19092, 모의AI18081, Connect18083은 loopback 포트다. 메모리 제한 때문에 다음 세 환경을 같은 포트로 **순차** 실행한다.

| 용도 | Compose project | Ubuntu private state |
| --- | --- | --- |
| 원래 복원 환경 | sentinel-portfolio | ~/.local/share/sentinel/portfolio |
| 정지 checkpoint 생성 | sentinel-operations | ~/.local/share/sentinel/operations |
| 별도 DB/broker 복원 | sentinel-restore | ~/.local/share/sentinel/restore |

임의 namespace는 거부한다. operations/restore 도구는 원본 변경·초기화를 거부한다. 원본은 `stop`과 보존 재개만 하며 `down -v`, offset reset, schema history 삭제를 하지 않는다. 다른 개발 앱이 같은 포트를 쓰면 먼저 상태를 확인한다.

PowerShell 진입점은 경로를 Ubuntu 경로로 바꾼 뒤 실행을 위임한다. Ubuntu에서 직접 실행할 때는 worktree 루트에서 다음처럼 namespace를 명시한다.

```sh
export SENTINEL_RUNTIME_NAMESPACE=operations
python3 scripts/reliability/operations_runtime.py prepare
python3 scripts/reliability/operations_app.py pause-start --build
```

기존 데이터는 private seed manifest의 행수·지문을 검증한 뒤 빈 시험 DB에만 넣는다. seed에는 원천 재무·snapshot·적재 원장이 포함되며 원래 사용자/요청/828개 격리 메시지를 옮기지 않는다. 임의 기존 DB를 seed로 덮어쓰지 않는다.

## 준비 상태와 종료

- `/actuator/health/liveness`: 프로세스 생존. broker/DB 장애와 분리한다.
- `/actuator/health/apiReady`: DB와 GLOBAL 접수 한도. 슬롯이 가득 차면503, DB를 조회할 수 없으면 DOWN이다. 일반 `/readiness`는 이 그룹의 대체 경로가 아니다.
- `/actuator/health/workerReady`: worker 의존성을 확인한다. 실제 전달/생성 완료는 별도 요청 smoke로 확인한다. connector RUNNING만으로 전달 성공이라고 판단하지 않는다.

M05 실행기는 실제 로그인 사용자20명·GLOBAL/사용자cap100·worker4·기본cache-off를 사용한다. 준비 상태 DB 장애 시험의 pool 접속 제한은2초다. 새 키가 한도를 넘을 때 거부하되 이미 커밋한 동일 키는 같은 requestId를 반환해야 한다. 예외 상태의 5xx는 수락 성공으로 세지 않는다.

```sh
python3 scripts/reliability/operations_app.py pause
# RUNNING이 0이 될 때까지 제한된 관찰을 수행한다.
python3 scripts/reliability/operations_app.py stop
```

정상 종료는 새로운 claim을 막고 이미 실행 중인 시도에 종료 시간을 준다. 강제 종료 시험은 이 도구가 만든 PID의 명령/상태 경로가 맞을 때만 `kill`로 종료한다. kill 이후 RUNNING을 임의로 성공 처리하거나 lease/token을 수정하지 않고 DB lease 만료와 recovery scan을 기다린다.

## checkpoint와 복원

앱이 정지하고 RUNNING0인 상태에서만 capture한다. checkpoint는 private `~/.local/share/sentinel/operations-evidence/20261113-m05-m07/checkpoints`에 생성된다. DB schema/data, 실제 파일 ZIP·파일별 크기/SHA, active snapshot·재무 전체 정렬 지문, 작업/Inbox/Outbox 수와 source 소비 위치를 보존한다. 자격 fixture는600 권한의 private 파일로만 옮기고 로그인 토큰·원래 비밀 키는 옮기지 않는다.

```powershell
.\scripts\reliability\Capture-Checkpoint.ps1 -Name seed1
.\scripts\reliability\Restore-Checkpoint.ps1 -Name seed1
```

restore는 manifest·SQL/ZIP hash·압축 경로를 먼저 검증한다. 대상은 빈 `sentinel-restore` DB와 빈 업로드 디렉터리여야 한다. 원본 환경은 정지 보존한다. 새 broker/새 connector에서 Outbox snapshot을 재전달하며 복원된 Inbox와 job 상태로 중복 DB 효과를 막는다. 원래 broker의 최신 offset을 과거 DB에 가져오지 않는다. DB의 파일 절대 경로만 새 업로드 루트로 치환하고 실제 파일 크기/SHA와 DB 참조를 대조한다.

capture는 첫 REST 변경 전에 실제6서비스의 project·실행 상태·loopback 포트를 검사하고, RUNNING0 및 완료 artifact 전체의 DB 참조·파일 크기/SHA가 맞아야 진행한다. 공용 CDC REST의 변경 요청도 Connect 컨테이너의 소유권을 별도로 검사한다. namespace 이름만 바꾼 채 다른 환경의 connector를 정지하지 않는다.

restore는 DB 적용 전에 기존 Kafka volume이나 Kafka/Connect 컨테이너가 있으면 거부한다. `prepare-empty`로 Kafka를 미리 만들고 restore를 실행하는 경로도 이 조건에 걸린다. DB만 비운 뒤 예전 broker를 붙이지 말고, 기존 시험 자료를 보존한 새 환경에서 restore 진입점이 직접 준비하도록 한다. 복원 후 artifact 전체와 `files`의 경로·크기·유형 및 실제 SHA가 일치해야 성공을 기록한다. 검사 실패 후의 부분 복원은 조사용으로 보존하며 자동 초기화하지 않는다.

```powershell
.\scripts\reliability\Test-Runbook.ps1 -Action enqueue -Namespace operations -Seed 1
.\scripts\reliability\Test-Runbook.ps1 -Action restore -Namespace restore -Seed 1
```

`enqueue`는 실제 HTTP100건을 수락하고 동일 키/한도 거부를 확인한 뒤 완료·대기가 섞인 정지 checkpoint를 만든다. `restore`는 빈 환경 복원 뒤 원래100건과 신규1건의 종결 상태·job/artifact/파일/슬롯·본인 다운로드를 대조하고 정지한다. 앞 명령을 실행해 이미 checkpoint가 생겼다면 Capture 명령을 다시 실행하지 않는다. 기존 파일은 덮어쓰지 않는다.

반복2·3은 이전 복원이 성공했는지, 현재 project/소유 marker/volume label/실제 경로가 맞는지 확인한 뒤에만 새 시험 자원을 초기화한다. 정리 전 DB와 PDF를 private 사본으로 보존한다. 이 초기화는 새로 만든 operations/restore에만 적용된다. 원래 portfolio에는 적용할 수 없다.

복원 뒤 재대조만 실패했으면 복원을 다시 덮어쓰지 않는다. `restore-resume`은 복원 기록과 커밋된 신규 요청을 하나로 확인한 경우에만 기존 DB를 재대조한다. 원자료가 손상되거나 신규 요청을 특정할 수 없으면 중단하고 실패 원자료를 보존한다. 비밀 파일·전체 로그를 외부에 게시하지 않는다.

## 장애 행렬과 완료 판정

```powershell
.\scripts\reliability\Invoke-FaultMatrix.ps1 -Seeds 1,2,3
.\scripts\perf\Verify-Evidence.ps1 -RunId 20261113-m05-m07
```

행렬은 원본이 아닌 restore 환경에서 실행한다. [고정 조건](../../perf/fixtures/fault-matrix.json)에 각 case의 시험 계층·입력수·경계·한계를 명시한다. 정상 복구100건과 경계 대상1건을 혼동하지 않는다. F15는 선택하지 않은 polling 경계이므로 N/A다. 격리 메시지는 수락 요청의 상태 분모와 분리한다. 최종 성공뿐 아니라 누락/중복/예상실패/비종결·외부 호출·고아 파일을 독립 확인한다.

원자료는 `perf/results/20261113-m05-m07`, 실행 command/XML/조건/해시는 각 run에 보존한다. 과거 성능 측정과 후속 코드의 차이를 기록하며, 실험되지 않은 개선율을 추가하지 않는다. 기존 disabled15개와 별도 JMeter 미실행, 선택하지 않은 polling도 숨기지 않는다.

정지 checkpoint는 온라인 무중단 백업이나 RPO0을 입증하지 않는다. 단일 WSL/단일 broker·모의 HTTP의 결과를 서비스 배포, 전원 장애, 다중 서버 재해복구, 실제 AI 정확도나 외부 호출 exactly-once로 확대하지 않는다.
