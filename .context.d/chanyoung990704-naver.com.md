# Per-User Session Log

- 2026-11-12 | 중간 결과: R07 브라우저9/9/build·M03 15회4848성공/6가드단축·M04실제cache8시험/Redis중단3seed 통과. 추가version gate RED보강후전체회귀중, native ABBA비교/최종검토/정리 남음. docs/results/browser-and-cache.md와실행원장에조건·한계기록. 원본/자료/offset 보존, 커밋·push없음.

- 2026-11-12 | 작업: 승인된 R07→M03→M04 시작 | 기존 프런트 원본 보존 복원·실제 브라우저→cache-off 기준선→상태 Redis/장애/전후 비교까지 진행. 원자료 perf/results/20261112-r07-m03-m04. 기존 자료/변경 보존, Ubuntu 전용 실행, 커밋·push 없음.

## 1. Owner (소유자)
- id: chanyoung990704-naver.com
- source: git config user.email
- email: chanyoung990704@naver.com

## 2. Recent Notes (최근 메모)

- 2026-11-14 | 작업: README 보강·GitHub main fast-forward 게시 | 결과: backend1d07f3a/frontend7d5f009/docsfa691d8 3개논리커밋을origin/main에push,원격fa691d8확인. README새결과·측정범위·cache-off판단·실행상태/흐름정리,Ubuntu명령예시점검·환경/키Git제외·MP4binary/원본blob일치·비밀패턴0/깨진링크0. 기존607/104검증코드변경없고게시메타데이터만별도패키지검증(1164파일/14주장),원래M07raw는보존 | 이슈: 원본main폴더의미커밋context/AGENTS/계획을보존해로컬checkout은동기화하지않음. 실제원격main에반영한것이며로컬변경정리/force/pull덮어쓰기없음. 게시기록/명령예시최종문서커밋은별도전송.

- 2026-11-14 | 작업: 사용자 GitHub push·README 최신 결과 편집·main 병합 명시 승인 | 결과: README 핵심 표/조건/아키텍처/완료 상태를 검증된 수치로 정리, 원시 자료·참고용 Git 제외 유지 및 환경/키 제외 보강·동영상 binary 속성 보강. 기존607 Java/104 Python 측정 소스와 새 문서 편집을 구분해 게시용 검증 준비 | 이슈: 원본 main checkout의 개인 context/AGENTS/계획 미커밋 변경은 보존하며 원격 main에 fast-forward 반영 예정. 코드·화면·문서를 논리 단위로 커밋하고 원격 SHA 확인,force/사용자 변경 덮어쓰기 없음.

- 2026-11-13 | 작업: M05~M07 최종 마감 | 결과: 복원303성공/적용15조건3반복·정상복구900성공/F15 N/A. 독립Important5단일RED→GREEN보강·F08문구정정후Java607pass/기존15skip·Python104pass·bootJar성공,실행실패/error0. 실제guard/현재실행기·원본과restore149976독립전체대조0차이·격리828/연결Outbox828/전체tablecount/재무SHA/6imageID보존. 원본6서비스재개/API초기정지·시험2환경보존정지·미종결/슬롯/앱0. 최종1162파일/14주장검증과PowerShell→Ubuntu Verify-Evidence wrapper까지exit0 | 이슈: strict gate INCOMPLETE는15기존skip,첫반복분리·준비공개보강전3반복/현재회귀별도·원격AI/HA/RPO0/사실정확성미검증한계와Ruling11기록. 대표3사례각3줄/결과3문서/ADR/runbook/README/계획/context동기화·변경소스50/main0·원자료/Git제외유지·추가재검토/커밋/push/배포/PDF없음. 완료근거perf/results/20261113-m05-m07/evidence-summary.json,evidence-verification-latest.json.

- 2026-11-13 | 작업: M07 독립검토 후 단일 fix pass | 결과: 첫전체603pass/ready공개1fail/기존15skip을보존. Important5의소유권/기존broker/DB파일대조/XML메서드연결/두JVM원자준비공개를RED→GREEN,공용CDC변경소유권도2RED→GREEN,전체Python104pass·준비경계3pass. F08문구조건오표기는Important재평가정정. 3반복M06은수정전실측/입력을보존하고준비공개밖Java/Gradle동일성자동대조·현재최종회귀별도 | 이슈: 세션54335에서m07-final-verified-02전체4scope/bootJar/strictGate→실제guard/runbook/독립대조/원본보존재개감사중. finish_evidence(prereview/final)·문서/계획/context마감남음; 추가독립재검토없음,원본149976/828격리보존정지,Ubuntu실행·커밋/push/배포/PDF없음. 원장에판정보류Ruling5건과제한기록.

- 2026-11-13 | 작업: M05~M07 이어서 실행 | 결과: 복원3회303/303·M06적용15조건×3반복완료/F15 N/A·정상복구9묶음900성공. 첫반복91기존성공+F04별도1성공/원래실패보존, seed2/3각92pass. 최초전체H2환경오염1045를실행기환경분리1RED→GREEN으로수정, 새m07-full-fixed에서fast444pass/기존15skip·실제인프라136pass확인후CDC/운영회귀중 | 이슈: strict15skip미완료표시유지, Python/현재runbook·독립원본대조·원본start/보존감사·최종증거/독립검토1회/문서마감남음. 세션88436·원장perf/results/20261113-m05-m07,원본volume/828격리/재무149976보존정지·Ubuntu실행·커밋/push/배포/PDF없음.

- 2026-11-13 이후 계속 | M05 세반복각101/101(총303)·금융149976/NULL8656/zero1383·기업3942/업종2083원본독립대조차이0. M06 첫정식반복92시험중91pass/ACK관측1fail;업무는성공했지만재할당전재수신1로조기판정. 같은180초예산의수신/ACK/업무 barrier추가후F04실제1/1pass(초기jobSUCC·offset287/수신1→44.732s뒤offset288/수신2). 업무·공유실행기·다른시험/Gradle변경없고해당메서드밖바이트일치검사,기존실패를보존한m06-seed1-repaired분리근거생성. seed2/3·전체회귀계속(세션12926),원본portfolio계속보존정지. docs/resultReliability/최종편집·전체검증·fresh검토1회·원본재개/보존감사남음. 진행원장과원자료는perf/results/20261113-m05-m07;사용자“이어서계속진행”은전체승인범위유지로반영.

- 2026-11-13 | 중간 결과: M05 seed1/2 각각101/101·실제PDF/기존완료보존확인,seed3별도DB복원중. 첫harness readiness경로/attempt대조SQL실패는수락·복원DB를보존해대조재개했고실패raw도보존. 원본portfolio정지·828격리/149976금융행/volume/history보존,새operations/restore초기화전에DB/PDF ZIP사본과소유label/경로확인. M06 실제중단/ACK/파일/응답유실·재시도/source/권한시험·조건JSON과M07증거검증/ADR/로컬문서초안작성. Python89pass+PID준비전소유권1RED→GREEN,Java컴파일통과·기능행렬/전체회귀는아직미실행. 역사41회CSV재계산일치·새성능주장없음. 전체승인범위를끝까지이어가며마지막fresh검토1회후원본재개/보존대조예정.

- 2026-11-13 | 작업: 사용자 “M공칠 최종 증거 편집까지 진행하자” 승인으로 M05→M06→M07 실행 시작 | 기존 managed worktree/dirty 변경·원본/149976금융행/미확인격리828/offset/history와 비밀 파일 보존. Ubuntu 애플리케이션/Gradle/Docker 실행, inline TDD·마지막 전체 독립검토1회, 커밋/push/배포·포트폴리오PDF제작 제외. operations/restore 별도 Compose·비밀상태·volume을 같은 포트로 순차 기동해 메모리 제한을 관리하고 기존 환경은 정지/재개만 한다. 원장 perf/results/20261113-m05-m07/progress.md.

- 2026-11-13 | 작업: 다음 실행 범위 확정 문서화 | R07/M03/M04·승인된 시험자료 정리 완료 기록과 기존 M05/M06/M07 계획을 대조했다. 다음 실행 단위는 M05 운영 절차·기동/종료/한도·정지 checkpoint/독립 namespace 복원 및 검증·증거 기록까지로 한정한다. READ 오류 가드 미연결은 안전 중단 범위에서 보강하고 기존 일회성 로그인/reader 준비 실패는 재현·오류 분류 대상으로 포함한다. M06 전체 장애행렬/M07 최종 편집·새 성능 튜닝/원격AI/배포는 제외 | 범위 문서만 작성하며 애플리케이션 구현·인프라 실행에는 착수하지 않는다. docs/superpowers/plans/2026-11-13-m05-scope.md 참조.

- 2026-11-12 19:10 | 사용자 “정리까지 진행해” 명시승인에 따라 시험자료 정리 | 계정20/보고서·PDF9622/입증격리714와종속원장삭제,소유인증캐시1013키(세션20/refresh993)제거,GLOBAL0. 미확인격리828/연결Outbox828 ID집합보존·ZIP9622사본6005339bytes/SHA검증. cleanup-applied-before/after.json기록 | cleanup-verified-data-audit.json149976금융행전체SHA282ba715...일치·schema35/33. 패키지/보존최종감사통과(cleanup-verified-audit.json). 기존원본/volume/offset/history·raw/ZIP보존,앱코드변경/커밋/push/배포없음.

- 2026-11-12 18:58 | 체크포인트재개마감 | final-audit.json exit0/769입력·104frontend·infra10불일치0/598클래스·83리소스동일/현재CDC RUNNING/소유앱종료. resumed-final-data-audit.json전체149976행SHA282ba715...유지 | 최초reader준비실패뒤별도준비확인·전체SELECT통과를구분기록. 삭제승인거절유지/계정20·보고서/PDF9622·격리1542보존(후보714/미확인828제외). R07/M03/M04코드·시험·3줄참고문서·계획/컨텍스트마감. 선택적시험정리만명시승인대기.

- 2026-11-12 마감 | R07/M03/M04구현·27회V2실측·독립Important3보강완료. Java591pass/15기존skip이후Java/리소스/Gradle동일,Python71·브라우저13/build통과. 기준선4756완료·READ663484시작/오류·DTO불일치0,캐시P95중앙36.122→32.393ms/TPS446.379→483.413/SQL1→1.0975와편차/비용으로기본off. 소유9622PDF실파일대조/ZIP사본SHA검증·149976금융행지문유지 | 최종읽기감사통과(final-audit.json). 시험계정20/9622보고서·PDF/격리1542는구체적삭제승인없다는자동승인거절로보존,입증714만정리후보/미확인828제외. M05이후미착수·커밋/push/배포없음. 참고용3줄/조건/한계·계획완료체크갱신.

- 2026-11-12 14:15 | 작업: R07→M03→M04 진행 | 결과: 브라우저9/build·Java591pass/15기존skip·Python70pass. 측정구간/완료경계 보강 V2 M03 15회4756접수모두성공(6가드단축TPSnull),소유9620PDF/원장대조·실제149976행지문유지 | 이슈: M04ABBA12회실행중,source동결/최종독립계산·검토·정리남음. V1초기수치는탐색으로보존/정규인용보류. session56967/원장perf/results/20261112-r07-m03-m04/progress.md

- 2026-11-09 | 작업: K01~K03 최종 완료 | 결과: 실제CDC/commit뒤ACK·격리DLT/관리수정replay·실제로그인HTTP~모의PDF 전체복구·readiness/대조CLI 구현. 독립검토Important2를한번RED→GREEN+최종전체회귀로보강했고새Minor1보류. Java594중579pass/기존15skip(실제124·fast444·CDC7·전체복구4),Python60pass·bootJar성공. 104종결요청/wire104/모의call108/상태기록303대조·실제149976행지문유지·9원장/파일메타0·GLOBAL0·소스752/infra10불일치0·실행모의소스동일·소유앱종료·업무CDC RUNNING | 이슈: null/empty격리표현Minor1후속,CDC FAILED는명시stop/정지확인/resume와실제전달확인필요·자가치유아님. 실제HTTP수신/응답보류중kill은외부2/artifact1/attempt2(56558ms관찰)확인. 최초audit관리연결Reset은읽기관찰12회로실제RUNNING확인·제품상태변경없음. 문서조건/각3줄10사례/계획/README/컨텍스트갱신,원자료/참고용/결과Git제외·기존관리worktree/원본/offset/history보존·커밋/push/배포없음. 다음R07→M03/M04,이번자동진행없음. 근거perf/results/20261109-k01-k03/final-verified-audit.json,docs/results/messaging-recovery.md

- 2026-11-09 | 작업: K01~K03 단계 검증·최종 검토 보강 | 결과: K01 실제CDC4/4, K02/대조/기존Snapshot27/27·fast5/5, K03 기본실제HTTP전체4/4통과. 독립검토2Important는CDC기존RUNNING오인/실제HTTP진입전kill시험이며정지확인→재개와모의응답보류/inflight관찰로보강;Python RED→GREEN(복구4/모의7) | 이슈: 소유JVM의실제inflight통합RED→새모의컨테이너→최종4task회귀/실자료지문/문서갱신이남음. 새Minor1(null/empty격리원문구분)보류·재검토없음·자동CDC자가복구미구현명시·원본/offset/history보존·커밋/push없음. perf/results/20261109-k01-k03

- 2026-11-09 | 작업: K01~K03 착수 | 결과: 사용자 승인 CDC실제전달/DBcommit후ACK·격리DLT/HTTP~PDF전체장애복구까지진행. 기존worktree·E03이미지/선택·R01~R06계약·공식3.4/3.3문서확인,단계별TDD로구현 | 이슈: R07/Redis/성능비교/원격AI/외부배포는제외,원자료 perf/results/20261109-k01-k03,원본/실자료/기존변경·offset/history보존,Ubuntu전용·커밋/push없음

- 2026-11-09 | 작업: R01~R06 최종 완료 | 결과: 최종Java565중550통과/기존15skip·실제인프라111/111·fast439pass,Python54pass. 독립검토Important2는실제4RED→4GREEN+최종전체회귀,새Minor0. 실제149976행/NULL8656/zero1383/8332버전·M02전체행hash유지,시험원장7종/맞춤파일메타0·GLOBAL0,721코드/9infra불일치0·실행모의서버hash일치·소유앱종료확인. CLIwrapper한글argv/미등록관리자exit2거부확인. 원자료/57개상태기록·참고용각3줄/조건/한계·계획체크·README/실행문서갱신 | 이슈: 실제Kafka/CDC전달K/브라우저R07/processkillM06/실제AI품질·새성능수치/Redis는후속. 원본checkout/실자료/기존변경보존·커밋/push없음. 최종근거 perf/results/20261109-r01-r06/final-verified-audit.json,docs/results/custom-report.md(로컬Git제외)

- 2026-11-09 | 작업: R01~R06 구현·검토 보강 후 최종 검증 진행 | 결과: 원자 접수/Inbox/job/고정source 모의HTTP/lease·retry/파일·소유자API/관리재개CLI 구현. 단계별 RED→GREEN,설정5개,추가경합4개 GREEN. 독립검토 Important2(완료·고아정리/잠금전시각)를 한 번의 보강으로 재현·수정. 이전 전체실제인프라107중106통과/기대schema30→33 수정,fast454중439pass·기존15skip,Python54pass | 이슈: final-verified 전체회귀 실행 중이며 종료 후 final_audit.py final-verified로 실제149976행 hash·기능원장/슬롯0·편집본/native 일치를 확인하고 문서/계획/컨텍스트 완료 처리. 원자료 perf/results/20261109-r01-r06,재검토/커밋/push없음. 실제Kafka/CDC업무연결·R07·M06·Redis캐시는후속

- 2026-11-09 | 작업: R01~R06 착수 | 결과: 사용자가 R06까지 전체 구현·검증을 명시 확인. 기존 worktree/계획/공통계약 확인 후 테스트 우선으로 원자접수→Inbox/job→모의HTTP→worker→파일/본인API→관리재개 순서 진행 | 이슈: 실제Kafka/CDC업무연결·화면R07은후속,원본/DB/기존변경보존·Ubuntu전용·외부AI미호출·실측전수치미정

- 2026-11-05 | 작업: M01/M02 완료·최종검토 Important2 보강 | 결과: 실제20기업4분기/149976값/8332버전·Ubuntu local,portfolio-perf·Python closed-loop·heap512/pool10/cache-off·warm30s+main120s·c1/c10전후각3회. c1 P95중앙54.3963→40.4278ms(-25.68%)/TPS23.8553→29.0184(+21.64%),c10 P95 200.8360→144.8047(-27.90%)/TPS79.7926→104.4904(+30.95%)로 한SQL MAX→NOT EXISTS채택. c50각1회pending40가드/100미실행. 본14회105364건성공·SQL평균11/coverage100%·20응답hash동일·DB149976행지문불변·14summary재계산일치. Java518중503통과/기존15비활성(실제인프라69/69),도구최종Python29+19통과 | 이슈: 독립검토1회 Important2 SQL다중문장/쓰기위험+최초가입실패재실행차단 RED→GREEN. 단일SQL+고유SELECT전용계정(실MySQL UPDATE1142거부)/동일자격로그인·재가입/commit전후유실검증. 보강후smoke74성공·Java앱jar/계측·발생AST같음·현재sourcecopy0,실측원자료동결/후속보강별도공개. 새Minor없음·기존4보류유지. 참고용3줄사례/결과·조건·원자료Git제외,실행가이드/계획/컨텍스트갱신. 원본checkout보존·소유앱종료·DB/volume보존·커밋/push없음. 다음R01원자접수

- 2026-11-05 | 작업: M01 완료·M02 before 저장 및 한SQL 후보 적용 | 결과: M01 Python23/도구19/실제인프라67/SQL단위6·HTTPsmoke69개/SQL11개. before c1·c10 각3회30s+120s 완료·오류0·계측100%,c50 1회poolpending40지속으로가드중단/100미실행. 선택repository시계열MAX→NOT EXISTS 한쿼리만변경. 실제20기업업무JSON동등성·숫자값변경거부2개통과. 전체재무149976행 before fingerprint282ba71516abf75b3bd414a6f2db753a0181147aad4115923de8b48f6cfb4f09 저장 | 이슈: parity초기LongNode/IntNode비교오류RED→wire JSON GREEN,운영코드변수1개유지·검증시험파일변경별도공개. 전체회귀/after 부하/채택판정은진행중. 중단50단계를3회성과로표시하지않음

- 2026-11-05 | 작업: M01→M02 계측 검증·MySQL 조회 기준선/한SQL 실험 착수 | 결과: 승인된 계획·측정 프로토콜·D04 기록과 기존 perf 경로 확인 후 테스트 우선으로 구현 진행 | 이슈: Ubuntu 전용/기존격리worktree·원본·비밀자료·사용자변경 보존, 수치/개선율은 실측 전 미정

- 2026-11-04 | 작업: D04 실제 원본 전체 적재·재접수·중단재개·독립대조 완료 | 결과: local 최소Spring/JDBC 별도JVM CLI와PS→Ubuntu전달, 마스터/키·값·NULL·출처대조 구현. 실제master3942/업종2083/재무149976(NULL8656·0값1383)/8332버전. 60000개/120chunk에서소유JVM SIGKILL후같은run b0154d6e-dbd1-4ab3-9001-0abcb507e7aa로재개,첫검증timeout보강후snapshot2ba59132-4743-446b-a307-f81e9dd563f2 VERIFIED/ACTIVE. 새run재접수도같은snapshot/값/버전유지. 독립CSV·OOXML/Decimal vsMySQL3시점모두차이0·원본6hash변경0·소스불일치0. 최종realInfra65/65(D04 MySQL17포함)·fast426pass/기존15skip/0fail(441),Python17·mock4통과 | 이슈: ASCII/UTF8출처join이PK첫열만사용해180초TXtimeout→비교값ASCII/bin으로PK5열eq_ref보강. 독립Important1+원본보존상Important로재평가한symlink1 RED→GREEN·재검토없음,PS역슬래시전달실패수정. 새classpath추적Minor1/기존D03 Minor1/E01 Minor2보류. 원천사실전체미검증(3S표본target없음/삼성2018·2021범위밖),첫실패구간시간N/A·성공stage시간은재사용확인. 포트폴리오6개3줄사례/조건/원자료·결과문서Git제외. G1통과/다음M01,커밋·push미수행

- 2026-11-04 | 작업: D04 전체 원본 적재·재적재·중단재개·대조 착수 | 결과: 기존 D01~D03/실행계약을 확인하고 실제 MySQL 실패시험→CLI/검증→전체자료 실행 진행 | 이슈: 원본/기존checkout 보존, Ubuntu 전용, 성능 개선 수치와 원천 사실 정확성은 별도 구분

- 2026-11-04 | 작업: D03 완료 | 결과: V29 버전bridge/원자포인터·V30 평균출처, 현재조회격리·검증·과거고정읽기·VERIFIED재접수 구현. 최종 realInfra48/48(D03 MySQL14 포함), fast426pass/기존15skip/0fail(총441). halt94 rollback/재시도, 독립MySQL A/B각144행·8bridge missing/extra/different0·코드동기화불일치0. 포트폴리오3줄사례5건·조건·최종원자료 기록, 새운영메모Git제외 | 이슈: Important3 RED→GREEN 보강·재검토없음, PDF finder Minor1·기존E01 Minor2보류. 초기fast heap포화실행은소유worker중단143으로보존, context cache16/fork1/heap512MiB로전체재실행통과. 평균출처불일치는NULL/재계산. 다음D04실제149976개/원천대조·성능미수행. fixtureDB보존/종료시포인터복구·기존checkout보존·커밋/push미수행

- 2026-11-04 | 작업: D03 최종 검증 | 결과: 전용 실제 MySQL14/14 통과. V29 버전/포인터 및 V30 평균출처, 현재조회·명시과거읽기·원자전환·halt94·지연집계·VERIFIED재접수 구현. 전체 realInfra/fast 회귀 진행 중 | 이슈: Important3 실패재현 후 수정, PDF derived finder Minor1 보류. 전체 실제 원본/D04·성능·커밋/push 미수행

- 2026-11-04 | 작업: D03 snapshot 조회 격리·검증 후 전환 착수 | 결과: 기존 계획/조회 경로 확인 후 Ubuntu 실패 재현·구현·실제 MySQL 검증 진행 | 이슈: 기존 자료/작업 공간 보존, 실제 전체 원본 적재는 D04이며 이번에는 합성 fixture로 경계를 검증

- 2026-11-04 | 작업: D02 적재 원장/staging/chunk 재시작 완료 | 결과: V28 MySQL/H2, snapshot/run 분리·DB 입력 payload·원자 checkpoint·새TX 재시도·명시resume 구현. D02 MySQL15/15·정책4/4, 전체realInfra34/34·fast426pass/기존15skip/0fail(총441). 실제 JVM commit전/후50/60행에서 새JVM100행 재개, 독립MySQL전체값대조0차이·코드불일치0. 포트폴리오 참고용 3줄사례5건·명령·원자료 기록, 신규운영메모도Git제외 | 이슈: 독립 Important3 RED→GREEN 보강·재검토없음. 감사DB불가 시RecoveryRequired/타JVM차단보장없음. 준비복구는예외주입. 실제149976개/D03/D04·성능·커밋/push미수행, E01보고Minor2유지

- 2026-11-04 | 작업: D02 적재 원장/staging/chunk 재시작 착수 | 결과: 기존 격리 작업 공간과 Ubuntu 실제 MySQL에서 실패 재현→구현→검증 진행 | 이슈: 전체 실제 자료 적재·snapshot 조회 활성화는 D03/D04 범위이며 이번에 수행하지 않음

- 2026-11-04 | 작업: 로컬 참고 문서 Git 제외 | 결과: 사용자 요청으로 포트폴리오 참고용.md와 docs/data/preflight.md를 .gitignore에 추가. 두 파일 모두 기존 Git 미추적 상태이며 로컬 파일 보존 | 이슈: 없음

- 2026-11-04 | 작업: D01 실제 자료 사전 검증 완료 | 결과: strict reader/service/model/CLI·원본6파일 manifest·출처/오류·CSV 대조 구현. Ubuntu36/36, 전체fast422pass/15기존skip/0fail(총437), 실제149976위치 독립전체대조 missing/extra/different0, 원본hash변경0·코드동기화불일치0. 포트폴리오 참고용에 3줄 사례4건과 조건/결과/원자료 연결 | 이슈: DL에너지 코드 누락5위치 오류로 전체ready=false, 업종명 NULL13건은 경고로 값 보존. 독립 지적 수식/완료표시/CSV구조와 nullable 오판을 실패재현 후 수정. D02/DB적재/성능실험/커밋/push 미수행, E01 보고 Minor2 유지
- 2026-09-15 | 작업: 포트폴리오와 저장소 구현을 교차 검증해 채용 평가자용 README 재구성 | 결과: 프로젝트명을 SENTINEL로 정리하고 데이터 라이프사이클, Kafka 비동기 처리, 사전 집계, 동시성, 장애·배포 경계, 구현/향후 설계를 근거 링크와 함께 재구성. 상대 링크 25개·Markdown fence·Docker Compose config 검증 통과 | 이슈: 포트폴리오의 Full-Text/N+1 수치는 별도 Spring 개인 프로젝트 근거이므로 SENTINEL 성과와 분리. 전체 테스트는 클래스 파일 생성 후 Gradle Test Worker가 모든 테스트 클래스를 읽지 못하는 ClassNotFoundException으로 2회 실패
- 2026-03-04 | 작업: CodeBuild 대량 테스트 실패 원인 수정 및 재발 방지 가드 추가 | 결과: `application.yaml`에서 `spring.*` 설정이 `server` 하위로 잘못 들어간 구조와 최상위 `server` 중복 키를 정리해 컨텍스트 초기화 실패 경로를 제거하고, `buildspec.yml` PRE_BUILD에 YAML 중복 키/파싱 검증 스크립트를 추가해 동일 유형 오류를 테스트 전 즉시 탐지하도록 보강 | 이슈: 샌드박스 제약으로 로컬 Gradle 테스트 재실행은 미검증
- 2026-03-04 | 작업: CodeBuild 실패 원인 추적성 개선 및 POST_BUILD 연쇄 오류 노이즈 제거 | 결과: `buildspec.yml`에서 Gradle 테스트 실패 시 XML 리포트의 failure/error를 로그로 출력하도록 보강하고, 이미지 미생성 시 POST_BUILD docker push를 조건부 스킵하도록 정리 | 이슈: 없음
- 2026-03-04 | 작업: CodeBuild auth/company 테스트 실패 원인 분석 및 CI 프로파일 고정 | 결과: `buildspec.yml` 테스트 명령에 `SPRING_PROFILES_ACTIVE=test`를 추가해 CI에서 `application-test.yaml`이 일관 적용되도록 수정 | 이슈: 샌드박스 네트워크 제한으로 로컬 Gradle 재실행 검증은 미실행
- 2026-02-26 | 작업: AI 리포트 비동기 기존 보고서 조회 경로 LazyInitializationException 수정 및 동시성/트랜잭션 보강 반영 점검 | 결과: `CompanyAiService.processReportGeneration`에서 self-invocation으로 트랜잭션 프록시가 적용되지 않던 기존 파일 조회 경로를 ID 직접 조회 쿼리(`findLatestPdfFileIdsByCompanyIdAndYearAndQuarter`)로 교체해 no session 오류를 제거. auth/ai/report/watchlist 관련 타깃 테스트 10종 묶음 통과 및 `CompanyAiReportStoreServiceTest`, `AiJobKafkaConsumerTest` 추가 | 이슈: 없음
- 2026-02-26 | 작업: AI 리포트 PDF 다운로드 검증 강화 및 저장 트랜잭션 분리 | 결과: `AiServerClient`에서 리포트 다운로드 시 `ResponseEntity<byte[]>` 기반으로 `Content-Type=application/pdf`를 검증하고 비정상 응답 본문을 로깅하도록 보강. `CompanyAiService`는 AI 다운로드(트랜잭션 외부)와 파일/버전 저장(`storeAndLinkReport`, `@Transactional`)을 분리해 DB 트랜잭션 점유 구간을 축소 | 이슈: 없음
- 2026-02-25 | 작업: 인사이트 트랜잭션 경계 분리 및 조회 오케스트레이션 정리 | 결과: `CompanyInsightService`를 `ensureInsightData(@Transactional)`/`loadInsights(@Transactional(readOnly=true))`로 분리하고, `CompanyInsightController`/`AdminCompanyQueryController`/`PerfBenchmarkController` 호출 흐름을 `ensure -> load`로 변경해 LazyInitializationException 재발 가능성을 낮춤. 관련 타깃 테스트 4종 통과(`CompanyInsightServiceTest`, `CompanyInsightControllerTest`, `AdminCompanyQueryControllerTest`, `PerfBenchmarkControllerTest`) | 이슈: 없음
- 2026-02-18 | 작업: CompanyOverview ACTUAL 부재 예외 완화 및 배포 설정 보강 | 결과: `CompanyOverviewService`에서 quarterKey 미지정+ACTUAL 부재 시 빈 개요 응답으로 fallback 처리, `ApiLoggingAspect` 비동기 로깅 리팩터링(`ApiLogProcessor`)에 맞춰 테스트 정합화, `.env.example`/`docker-compose.app.yml`/`docker-compose.local.yml`/`README.MD`에 Kafka·Virtual Thread 환경변수 가이드 추가, CI와 동일한 Gradle 테스트 명령 통과 확인 | 이슈: 없음
- 2026-02-16 | 작업: 이슈 #127 AI 장시간 작업 Kafka+VirtualThread 1차 이관 | 결과: `CompanyAiController` 리포트 요청을 Kafka 디스패치 기반으로 전환(미사용 시 기존 @Async fallback), `CompanyWatchlistAsyncHandler`의 AI 코멘트 워밍업을 Kafka 경로로 우선 전송(미사용 시 동기 fallback), `AiJobDispatchService`/`AiJobKafkaConsumer`/`AiJobMessage`/`AiJobType`/`KafkaTopicConfig` 추가, docker compose(local/app/base)에 Kafka 서비스 연동, 관련 타깃 테스트 통과 | 이슈: 전체 테스트는 실행 세션 지연으로 결과 확정 전 중단
- 2026-02-16 | 작업: AuthIntegrationTest 전체 회귀 보완(테스트 코드 한정) | 결과: `change-password`/`logout-all` 관련 4개 통합 테스트에 CSRF 쿠키(`csrf_token`) + 헤더(`X-CSRF-Token`) 전달을 반영해 현재 컨트롤러 정책과 정합화, `AuthIntegrationTest` 단독 및 `./gradlew test` 전체 통과 확인 | 이슈: 없음
- 2026-02-16 | 작업: 이슈 #125 AI 타임아웃 전파 완화 및 인사이트 복원력 강화 | 결과: `application.yaml` AI timeout 기본값(connect/response/call) 상향, `ExternalAiUnavailableException`+`COMMON_503`+전역 핸들러 매핑 추가, `CompanyInsightService` fallback(기존 데이터 우선/데이터 없음 시 503) 적용, `CompanyWatchlistAsyncHandler` 로그에 reasonCode(`AI_TIMEOUT`/`AI_CIRCUIT_OPEN`/`AI_UNAVAILABLE`) 및 구조화 필드(operation/companyId/stockCode) 추가, 관련 타깃 테스트 4종 통과 | 이슈: 전체 테스트(`./gradlew test`)에서 `AuthIntegrationTest` 4건이 기존 기준으로 실패(변경 범위 외 회귀 여부 추가 확인 필요)
- 2026-02-14 | 작업: CodeBuild test 컴파일 실패(compileTestJava) 후속 수정 | 결과: `CommentsService.listByPost(Long)` 시그니처 변경에 맞춰 `CommentsServiceTest`/`CommentIntegrationTest` 호출부를 정리하고, `AuthIntegrationTest`에 `TokenHashService` import를 추가했으며, `PostMapperTest`의 구 role-mocking 테스트를 현재 `PostMapper` 구현(`toResponseWithQnaStatus`) 기준으로 정합화 | 이슈: 로컬 샌드박스 네트워크 제한으로 Gradle 전체 재검증은 미실행
- 2026-02-14 | 작업: upstream/main CodeBuild 컴파일 실패 핫픽스 정리 | 결과: `CommentController`의 `commentsService.listByPost` 호출 시그니처를 단일 인자로 정합화하고, `PostMapper`의 미사용/미완성 `isAdminComment` 블록을 제거해 `UserRoleEntity/userRoleRepository/RoleName` 심볼 오류를 해소 | 이슈: 샌드박스 네트워크 제한으로 로컬 Gradle 컴파일 재검증은 미실행
- 2026-02-14 | 작업: CodeBuild 댓글 컴파일 오류 수정 및 배포 브랜치 반영 | 결과: `CommentsRepository`에 `findByPostIdAndDeletedAtIsNullOrderByDepthAscSequenceAsc`를 추가하고 `CommentsService.listByPost` 시그니처를 정합화해 CodeBuild `cannot find symbol` 컴파일 실패를 해소. 관련 테스트(`CommentsRepositoryTest`, `CommentIntegrationTest`)와 `compileJava` 통과 확인 후 `origin/main` 및 upstream PR(#117) 반영 진행 | 이슈: 없음
- 2026-02-14 | 작업: 이슈 #115 사용자명 마스킹 + 로그인 실패 잠금 구현 | 결과: `NameMaskingUtil` 기반으로 `PostMapper`/`CommentMapper`/`UserMapper`/`AdminUserQueryService`의 name 노출을 마스킹 처리하고, `LoginAttemptService`(Redis) + `LoginAttemptProperties` + `AuthService` 연동으로 5회 실패 시 15분 잠금(`AUTH_429`)을 적용. 단위/통합 타깃 테스트 통과 및 `./gradlew cleanTest test` 재실행 통과, upstream 이슈 #115/PR #117 및 Notion 상위·하위 페이지 기록 완료 | 이슈: `cleanTest test` 1회차에서 Gradle XML test-results 쓰기 오류가 간헐 발생했으나 재실행으로 해소
- 2026-02-14 | 작업: 외부 AI 호출 회복탄력성 + 대시보드/게시글 성능 최적화(#113) | 결과: `AiWebClientConfig`와 Resilience4j(timeout/circuit breaker/retry/bulkhead) 적용, 수동 재시도 제거, 벤치마크 전/후 delta CSV(`comparison-api-delta.csv`, `comparison-runtime-delta.csv`) 산출 추가, QnA `qnaStatus`를 role 기반 쿼리로 계산, 대시보드 risk 집계 쿼리 최적화. `./gradlew cleanTest test` 및 변경 범위 타깃 테스트 통과 | 이슈: `upload/` 런타임 산출 디렉터리는 커밋 제외
- 2026-02-14 | 작업: P0 API 로깅 토큰/쿠키 마스킹 강화(#111) | 결과: `ApiLoggingAspect`에 JWT/Bearer/Cookie 패턴 마스킹과 `@CookieValue` 인자 비노출 처리를 추가하고 `ApiLoggingAspectTest`를 보강, `./gradlew test --tests com.aivle.project.common.logging.ApiLoggingAspectTest` 및 `./gradlew cleanTest test` 통과 | 이슈: 없음
- 2026-02-14 | 작업: P0 API 로깅 토큰/쿠키 마스킹 강화(#111) | 결과: `ApiLoggingAspect`에 JWT/Bearer/Cookie 패턴 마스킹과 `@CookieValue` 인자 비노출 처리를 추가하고 `ApiLoggingAspectTest`를 보강, `./gradlew test --tests com.aivle.project.common.logging.ApiLoggingAspectTest` 및 `./gradlew cleanTest test` 통과 | 이슈: 없음
- 2026-02-14 | 작업: 토큰 해시 pepper(HMAC) 적용(#109) | 결과: `TokenHashService`를 HMAC-SHA256(+pepper)로 전환하고 `APP_TOKEN_HASH_PEPPER_B64` 미설정/오형식 시 Fail-Fast를 적용, refresh/email verification 조회에 레거시 SHA-256 fallback+자동 마이그레이션을 반영했으며 `./gradlew cleanTest test` 전체 통과 | 이슈: 없음
- 2026-02-14 | 작업: 인증 재전송 API 보안 전환(#107) | 결과: `POST /api/auth/resend-verification` 인증 기반 경로 추가, legacy `GET`에 Deprecation/Sunset 헤더 적용, Redis 기반 사용자/IP 레이트리밋 추가 및 컨트롤러/서비스 테스트 통과 | 이슈: 없음
- 2026-02-14 | 작업: 토큰 저장 해시 전환(#107) | 결과: refresh/email verification 토큰을 SHA-256 해시 저장으로 전환하고 MySQL/H2 `V27__hash_auth_tokens` 마이그레이션 추가, dual-read fallback 및 관련 서비스 테스트 통과 | 이슈: 없음
- 2026-02-14 | 작업: refresh/logout CSRF 더블서브밋 적용(#107) | 결과: `csrf_token` 쿠키 발급 및 `X-CSRF-Token` 검증 추가, CORS 허용 헤더 반영, `AuthIntegrationTest` 포함 `./gradlew cleanTest test` 전체 통과 | 이슈: 없음
- 2026-02-13 | 작업: 최소 변경 배포 안전망 반영 | 결과: `buildspec.yml` artifacts에 `build/libs/app.jar`를 추가해 Docker 주 배포 유지 중에도 `DEPLOY_RUNTIME=systemd` 전환 시 JAR 아티팩트 누락 실패를 방지 | 이슈: 없음
- 2026-02-13 | 작업: CodeBuild YAML 파싱 실패(`YAML_FILE_ERROR`) 수정 | 결과: `buildspec.yml` commands의 `:` 포함 셸 라인을 YAML-safe 문자열로 감싸 `DOWNLOAD_SOURCE` 단계 파싱 오류 해소 | 이슈: 없음
- 2026-02-13 | 작업: CodeBuild Docker build 실패 원인 보정 | 결과: `Dockerfile`을 `build/libs/app.jar` 단일 복사 방식으로 고정하고 `.dockerignore`에서 해당 파일만 include되도록 조정, `buildspec.yml`에 non-plain bootJar 선택/복사 및 push 전 이미지 존재 검사 추가 | 이슈: 없음
- 2026-02-13 | 작업: 로컬 Docker 실행 오버라이드 정리 | 결과: `docker-compose.local.yml` 추가, `docker-compose.app.yml`의 env file 경로를 `APP_ENV_FILE`로 변수화, `LOCAL_ENV_FILE` 지원으로 운영/로컬 compose 병합 실행 가능하도록 보정 | 이슈: 없음
- 2026-02-13 | 작업: Docker 배포 전환 2차(CI/아티팩트/문서) | 결과: `Dockerfile`/`.dockerignore`/`docker-compose.app.yml` 추가, `buildspec.yml`을 ECR 이미지 빌드·푸시 + `image-uri.env` 아티팩트 생성으로 변경, `README.MD` 배포 가이드 반영, `./gradlew test` 및 배포 스크립트 테스트 통과 | 이슈: 없음
- 2026-02-13 | 작업: Docker 배포 전환 1차(스크립트 듀얼 런타임 리팩토링) | 결과: `scripts/lib/deploy-runtime.sh` 공통 모듈 추가, `setup/install/start/stop/health-check`에 `DEPLOY_RUNTIME(systemd|docker)` 분기 반영, env 예시에 Docker 변수 추가, `scripts/tests/deploy-runtime-test.sh` 작성 후 `bash -n`/테스트 통과 | 이슈: 없음
- 2026-02-12 | 작업: 전체 테스트 회귀 보정(role 시드 파급) | 결과: `CompanyOverviewServiceTest`에 `CompanyReportVersionIssueService` mock bean을 추가하고, `CustomUserDetailsServiceTest`를 role 시드 조회 방식으로 전환해 role UNIQUE 충돌을 제거. `./gradlew test` 전체 통과(383 tests, 15 skipped) 확인 | 이슈: 없음
- 2026-02-12 | 작업: role 시드 고정 + signup duplicate 도메인 변환(P0) | 결과: Flyway `V26__seed_roles`(MySQL/H2)를 추가해 `ROLE_USER/ROLE_ADMIN/ROLE_ANALYST`를 멱등 시드로 고정하고, `UserDomainService`의 역할 동적 생성을 제거해 시드 미존재 시 실패하도록 변경. `SignUpService`에 `DataIntegrityViolationException` -> `EMAIL_ALREADY_EXISTS(409)` 변환을 추가하고 관련 단위/통합 테스트(`SignUpServiceTest`, `UserDomainServiceTest`, `AuthIntegrationTest`, `CompanyWatchlistServiceTest`, `UserRepositoryTest`) 통과 | 이슈: 없음
- 2026-02-12 | 작업: 리포트 버전 발급 공통화(P0) 1차 | 결과: `CompanyReportVersionIssueService`를 추가해 report row lock + next version 발급을 단일 경로로 통합하고, report import/publish/predict/pdf + `CompanyAiService`가 공통 서비스를 사용하도록 정리. `CompanyAiServiceTest`/`CompanyReportVersionIssueServiceTest` 통과 후 커밋(`fix(report): centralize company report version issuance`) 완료 | 이슈: 없음
- 2026-02-12 | 작업: getOrCreate 충돌 재조회 패턴 통일(P0) | 결과: `GetOrCreateResolver` 공통 유틸을 추가해 분기/리포트 생성 경로의 UNIQUE 충돌 시 재조회로 수렴하도록 통일하고, `CompanyPredictionCacheService`도 버전 발급 공통 서비스 사용으로 보강. 관련 타깃 테스트(`GetOrCreateResolverTest`, report 서비스 4종, `CompanyAiServiceTest`, `CompanyPredictionCacheServiceTest`) 통과 | 이슈: 없음
- 2026-02-12 | 작업: AI 리포트 비동기 요청 즉시 완료 지연 수정(#98) | 결과: `CompanyAiService`의 파일 존재 판정을 "PDF가 있는 최신 버전" 기준으로 변경하고, 기존 PDF 존재 시 `PROCESSING` 없이 `COMPLETED`로 즉시 전환하도록 보정. `CompanyAiServiceTest`에 기존 PDF 재사용/상태 전이 테스트 추가 후 타깃 테스트 통과 | 이슈: 없음
- 2026-02-11 | 작업: Virtual Thread Phase1 성능측정 계측 고도화(3번) | 결과: `collect-runtime-metrics.sh`/`summarize-runtime-metrics.sh`를 추가하고 `run-benchmark.sh`/`compare.sh`에 통합해 before/after 런타임 지표를 자동 수집/비교하도록 확장. perf 보안 규칙에 `/actuator/metrics/**` 허용 추가, 문서 갱신 후 `20260211-vt-phase1-metrics-v3` 실측에서 after 성능 저하 및 CPU/GC 상승 확인 | 이슈: `tomcat.threads.busy` 지표는 환경에 따라 미노출(NaN) 가능
- 2026-02-11 | 작업: ai-report 동시성 500 오류(UK_REPORT_VERSION_INDEX_2) 완화 | 결과: `CompanyAiService` 버전 생성 시 `company_reports` 비관적 락(`findByIdForUpdate`) 적용, `PerfDataInitializer`에 2026Q1 분기/보고서 선생성 추가, `application-perf.yaml` 메일 설정 보강. `CompanyAiServiceTest`/`PerfBenchmarkControllerTest` 통과 및 perf 병렬 호출(100회, 동시 20)에서 HTTP 200 100건/유니크 충돌 로그 0건 확인 | 이슈: 없음
- 2026-02-11 | 작업: Phase1 Virtual Thread 도입 및 JMeter 벤치마크 기반 구축(#95) | 결과: `insightExecutor`/`emailExecutor`에 Virtual Thread 토글을 도입하고 perf 프로파일(`application-perf.yaml`), perf 벤치마크 API(`/api/perf/benchmark/*`), 외부 의존성 mock(AI/Turnstile), JMeter 플랜/실행·비교 스크립트(`perf/jmeter`, `perf/scripts`) 및 문서를 추가함. 관련 단위 테스트와 `./gradlew test` 전체 통과 | 이슈: 실행 환경에 `jmeter` CLI가 없어 before/after 실측 리포트 생성은 미실행
- 2026-02-11 | 작업: logout-all 이후 refresh 재발급 차단 보안 보강(#93) | 결과: `logoutAll` 시 사용자 refresh 토큰을 Redis/DB에서 일괄 폐기하도록 보강하고, `refresh` 경로에 logout-all 기준시각 검증을 추가함. `AuthServiceTest`/`RefreshTokenServiceTest`/`AuthIntegrationTest` 보강 후 `./gradlew test` 전체 통과, upstream PR #94 생성 | 이슈: 없음
- 2026-02-11 | 작업: Virtual Thread Phase1 성능측정 계측 고도화(3번) | 결과: `collect-runtime-metrics.sh`/`summarize-runtime-metrics.sh`를 추가하고 `run-benchmark.sh`/`compare.sh`에 통합해 before/after 런타임 지표를 자동 수집/비교하도록 확장. perf 보안 규칙에 `/actuator/metrics/**` 허용 추가, 문서 갱신 후 `20260211-vt-phase1-metrics-v3` 실측에서 after 성능 저하 및 CPU/GC 상승 확인 | 이슈: `tomcat.threads.busy` 지표는 환경에 따라 미노출(NaN) 가능
- 2026-02-11 | 작업: ai-report 동시성 500 오류(UK_REPORT_VERSION_INDEX_2) 완화 | 결과: `CompanyAiService` 버전 생성 시 `company_reports` 비관적 락(`findByIdForUpdate`) 적용, `PerfDataInitializer`에 2026Q1 분기/보고서 선생성 추가, `application-perf.yaml` 메일 설정 보강. `CompanyAiServiceTest`/`PerfBenchmarkControllerTest` 통과 및 perf 병렬 호출(100회, 동시 20)에서 HTTP 200 100건/유니크 충돌 로그 0건 확인 | 이슈: 없음
- 2026-02-11 | 작업: Phase1 Virtual Thread 도입 및 JMeter 벤치마크 기반 구축(#95) | 결과: `insightExecutor`/`emailExecutor`에 Virtual Thread 토글을 도입하고 perf 프로파일(`application-perf.yaml`), perf 벤치마크 API(`/api/perf/benchmark/*`), 외부 의존성 mock(AI/Turnstile), JMeter 플랜/실행·비교 스크립트(`perf/jmeter`, `perf/scripts`) 및 문서를 추가함. 관련 단위 테스트와 `./gradlew test` 전체 통과 | 이슈: 실행 환경에 `jmeter` CLI가 없어 before/after 실측 리포트 생성은 미실행
- 2026-02-11 | 작업: logout-all 이후 refresh 재발급 차단 보안 보강(#93) | 결과: `logoutAll` 시 사용자 refresh 토큰을 Redis/DB에서 일괄 폐기하도록 보강하고, `refresh` 경로에 logout-all 기준시각 검증을 추가함. `AuthServiceTest`/`RefreshTokenServiceTest`/`AuthIntegrationTest` 보강 후 `./gradlew test` 전체 통과, upstream PR #94 생성 | 이슈: 없음
- 2026-02-11 | 작업: 관리자 사용자 목록에서 ROLE_ADMIN 제외(#91) | 결과: `UserRepository`에 `NOT EXISTS` 기반 역할 제외 쿼리를 추가하고 `AdminUserQueryService` 호출을 변경했으며, `UserRepositoryTest`로 관리자/복합권한 제외 동작을 검증함. `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-11 | 작업: CodeBuild ApplicationContext 실패(뉴스/업종 통합테스트) 대응 | 결과: 테스트 전용 RSA 키(`src/test/resources/jwt`)를 추가하고 `application-test.yaml`에 JWT 키 경로/issuer/kid를 명시했으며, `buildspec.yml` 테스트 단계에서 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`를 강제 지정해 CI 환경 의존성 제거. 대상 4개 테스트 클래스 재실행 통과 | 이슈: 없음
- 2026-02-10 | 작업: CI 테스트 컨텍스트 연쇄 실패 안정화(#90) | 결과: `dev` 프로필 기반 테스트를 `test`로 통일하고 파일 업로드 통합테스트를 테스트 스토리지 스텁 기준으로 보정, `buildspec.yml`에 test/package 단계 분리 및 상세 로그 옵션 추가 | 이슈: 샌드박스 네트워크 제한으로 `./gradlew bootJar -x test` 단독 검증 불가
- 2026-02-11 | 작업: 관리자 사용자 목록에서 ROLE_ADMIN 제외(#91) | 결과: `UserRepository`에 `NOT EXISTS` 기반 역할 제외 쿼리를 추가하고 `AdminUserQueryService` 호출을 변경했으며, `UserRepositoryTest`로 관리자/복합권한 제외 동작을 검증함. `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-11 | 작업: CodeBuild ApplicationContext 실패(뉴스/업종 통합테스트) 대응 | 결과: 테스트 전용 RSA 키(`src/test/resources/jwt`)를 추가하고 `application-test.yaml`에 JWT 키 경로/issuer/kid를 명시했으며, `buildspec.yml` 테스트 단계에서 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`를 강제 지정해 CI 환경 의존성 제거. 대상 4개 테스트 클래스 재실행 통과 | 이슈: 없음
- 2026-02-10 | 작업: CI 테스트 컨텍스트 연쇄 실패 안정화(#90) | 결과: `dev` 프로필 기반 테스트를 `test`로 통일하고 파일 업로드 통합테스트를 테스트 스토리지 스텁 기준으로 보정, `buildspec.yml`에 test/package 단계 분리 및 상세 로그 옵션 추가 | 이슈: 샌드박스 네트워크 제한으로 `./gradlew bootJar -x test` 단독 검증 불가
- 2026-02-11 | 작업: 관리자 사용자 목록에서 ROLE_ADMIN 제외(#91) | 결과: `UserRepository`에 `NOT EXISTS` 기반 역할 제외 쿼리를 추가하고 `AdminUserQueryService` 호출을 변경했으며, `UserRepositoryTest`로 관리자/복합권한 제외 동작을 검증함. `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-11 | 작업: CodeBuild ApplicationContext 실패(뉴스/업종 통합테스트) 대응 | 결과: 테스트 전용 RSA 키(`src/test/resources/jwt`)를 추가하고 `application-test.yaml`에 JWT 키 경로/issuer/kid를 명시했으며, `buildspec.yml` 테스트 단계에서 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`를 강제 지정해 CI 환경 의존성 제거. 대상 4개 테스트 클래스 재실행 통과 | 이슈: 없음
- 2026-02-10 | 작업: CI 테스트 컨텍스트 연쇄 실패 안정화(#90) | 결과: `dev` 프로필 기반 테스트를 `test`로 통일하고 파일 업로드 통합테스트를 테스트 스토리지 스텁 기준으로 보정, `buildspec.yml`에 test/package 단계 분리 및 상세 로그 옵션 추가 | 이슈: 샌드박스 네트워크 제한으로 `./gradlew bootJar -x test` 단독 검증 불가
- 2026-02-11 | 작업: CodeBuild ApplicationContext 실패(뉴스/업종 통합테스트) 대응 | 결과: 테스트 전용 RSA 키(`src/test/resources/jwt`)를 추가하고 `application-test.yaml`에 JWT 키 경로/issuer/kid를 명시했으며, `buildspec.yml` 테스트 단계에서 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH`를 강제 지정해 CI 환경 의존성 제거. 대상 4개 테스트 클래스 재실행 통과 | 이슈: 없음
- 2026-02-10 | 작업: CI 테스트 컨텍스트 연쇄 실패 안정화(#90) | 결과: `dev` 프로필 기반 테스트를 `test`로 통일하고 파일 업로드 통합테스트를 테스트 스토리지 스텁 기준으로 보정, `buildspec.yml`에 test/package 단계 분리 및 상세 로그 옵션 추가 | 이슈: 샌드박스 네트워크 제한으로 `./gradlew bootJar -x test` 단독 검증 불가
- 2026-02-10 | 작업: companies 이름 FULLTEXT 인덱스 마이그레이션 분리 | 결과: MySQL에 `V25__add_companies_name_fulltext_index.sql` 멱등 인덱스 생성 추가, H2에는 no-op `V25` 추가 | 이슈: 없음
- 2026-02-10 | 작업: 기업 검색 하이브리드 fallback 적용 | 결과: CompanySearchService에 FULLTEXT 우선 + 실패/0건 LIKE fallback 로직을 추가하고 MySQL FULLTEXT 인덱스(Flyway V25) 및 하이브리드 단위 테스트를 보강함 | 이슈: 레포 기존 테스트 컴파일 오류(SecurityEndpointAuthorizationTest의 dashboard 서비스 import 누락)로 타깃 테스트 단독 실행 불가
- 2026-02-10 | 작업: 보안 매처/문서 정리 3차 | 결과: `SecurityConfig`를 공개/인증/USER 그룹 상수 기반으로 재정렬해 신/구 URI를 동시 허용하도록 정비하고, `SecurityEndpointAuthorizationTest`를 실제 컨텍스트 기반으로 추가해 공개/인증/USER/ADMIN 권한 회귀를 검증했으며, `docs/rest-endpoint-migration-2026-02.md`에 Old->New 매핑표를 문서화 | 이슈: 없음
- 2026-02-10 | 작업: 컨트롤러 URI 정규화 2차(회사/AI/뉴스/리포트/파일/워치리스트) | 결과: `CompanySearchController`(me/query 파라미터), `CompanyOverviewTempController`(/{companyId}), `CompanyAiController`(analysis/ai-reports), `NewsController`/`NewsAdminController`/`ReportAnalysisController`(sync·latest 신 경로), `FileDownloadController`(download-url), `CompanyWatchlistController`(metrics/values) 신 경로를 추가하고 하위호환 경로를 병행 지원, 관련 테스트 5개 클래스 통과 | 이슈: 없음
- 2026-02-10 | 작업: 게시글 API REST 경로 정규화 1차 | 결과: `PostController`에 신 경로(`/api/posts?categoryName=...`, `/api/posts/{postId}`, `POST /api/posts`)와 구 경로 하위호환 매핑을 추가하고, `PostService`에 postId 기준 조회/수정/삭제 메서드를 추가했으며, `PostControllerIntegrationTest` 신 경로 테스트 4건을 통과시킴 | 이슈: 없음
- 2026-02-09 | 작업: 워치리스트 삭제 후 재등록 중복 오류 수정 | 결과: 활성 데이터만 중복 판정하도록 변경하고, 소프트 삭제 데이터는 restore + note 갱신으로 재등록 처리, 관련 서비스/컨트롤러 테스트 통과 | 이슈: 없음
- 2026-02-09 | 작업: 인사이트 조회 refresh 파라미터 기반 강제 최신화 추가 | 결과: `GET /api/companies/{companyId}/insights`에 `refresh` 추가, refresh=true 시 뉴스/사업보고서 재수집과 external_health_score 동기화까지 수행, 관련 테스트 통과 | 이슈: 없음
- 2026-02-09 | 작업: 기업 AI 종합 코멘트 캐시 저장/수동 적재 API 구현 | 결과: ai-comment 클라이언트/서비스 추가, company_key_metrics.ai_comment 저장, 워치리스트 등록 시 선행 캐시, 관리자 수동 캐시 API 및 테스트 추가, cleanTest test 통과 | 이슈: 전체 테스트 최초 실행 시 Gradle 결과 XML 쓰기 오류가 cleanTest 재실행 후 해소됨
- 2026-02-09 | 작업: 뉴스 재수집 API 후 company_key_metrics 동기화 연결 | 결과: refresh-latest 호출 후 external_health_score 즉시 동기화, 관련 테스트 통과 | 이슈: 없음
- 2026-02-09 | 작업: 뉴스 재수집 API 권한을 ROLE_USER로 조정 | 결과: 경로를 `/api/companies/{stockCode}/news/refresh-latest`로 변경하고 SecurityConfig에 USER 권한 규칙 추가, 관련 테스트 통과 | 이슈: 없음
- 2026-02-09 | 작업: 뉴스 최신 재수집/평균 점수 복구 API 추가 | 결과: `/api/admin/companies/{stockCode}/news/refresh-latest` 구현, `average_score` 누락 시 기사 점수 평균으로 복구, 관련 테스트 통과 | 이슈: 없음
- 2026-02-09 | 작업: CompanyInfo reputationScore 응답 스케일링 | 결과: reputationScore에 100배 및 소수 0자리 반올림 적용 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverview ID/종목코드 해석 보정 및 리포트/지표 컨트롤러 복구 | 결과: ID 우선 해석, 보고서/지표 다운로드 컨트롤러 재활성화, 테스트 전체 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: 워치리스트 비동기 캐시 read-only 오류 수정 | 결과: Overview 호출 대신 캐시 서비스 직접 호출로 쓰기 트랜잭션 보장 | 이슈: 없음
- 2026-02-08 | 작업: 워치리스트 등록 후 비동기 캐시 프리워밍 | 결과: 이벤트 기반 비동기 처리 및 @EnableAsync 적용, 컨트롤러 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: @CurrentUser Long 주입 문제 해결 | 결과: userId 클레임 기반 주입 지원 및 watchlist 컨트롤러 테스트 보정 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: 워치리스트 기업 목록 API 추가 | 결과: /api/companies에서 CompanyInfoDto 목록 반환 및 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: 인사이트 응답에 평균 점수 추가 | 결과: CompanyInsight 응답에 averageScore 필드 추가 및 테스트 통과 | 이슈: finalize()/MockBean deprecated 경고
- 2026-02-08 | 작업: 인사이트 기반 외부 건강도 동기화/폴백 | 결과: NewsAnalysis 평균 점수로 external_health_score 동기화 및 CompanyInfo 폴백 응답, 테스트 추가/통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview metric level 보정 | 결과: metrics level을 최신 ACTUAL signal_color로 보정하고 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview 신호등 캐시 추가 | 결과: AI signals 응답 캐시 서비스/DTO/테스트 추가 및 Overview 연동 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyInfo overallScore 매핑 수정 | 결과: overallScore를 compositeScore(DB)로 변경 및 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview companyId 입력 보정 | 결과: 종목코드(예: 000020) 입력 시 stock_code로 해석하도록 컨트롤러 수정 및 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview 건강점수 캐시 연동 | 결과: AI health-score 응답 저장 및 개요 서비스 연계, 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview 예측 캐시 연동 | 결과: Overview 서비스에 예측 캐시 호출 추가 및 테스트 정합화 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview 예측 캐시 서비스 추가 | 결과: 최신 ACTUAL 분기 기준 예측값 캐시 서비스/테스트 추가 및 통과 | 이슈: MockBean deprecated 경고
- 2026-02-08 | 작업: CompanyOverview 기본 분기 자동 선택 | 결과: quarterKey 미지정 시 ACTUAL 최신 분기로 대체 및 서비스 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: 게시글 상세 응답에 첨부파일/다운로드 정보 추가 | 결과: PostDetailResponse 및 서비스 로직/테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: 게시글 첨부파일 다운로드 권한 변경 | 결과: 로그인 사용자 전체 다운로드 허용 및 FileService 테스트 보강 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverview metrics 필터 기준 변경 | 결과: metrics를 다음 분기 PREDICTED 기준으로 필터링하고 최신 버전 탐색 조건에서 valueType 제약 제거 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverview metricSeries/metrics 조회 로직 재구성 | 결과: metricSeries는 위험/비위험 최신 버전 병합, metrics는 현재 분기 ACTUAL + signal_color 매핑 | 이슈: 없음
- 2026-02-08 | 작업: 개요 지표 최신 버전 조회 보강 | 결과: 위험/비위험 지표 각각 최신 버전 병합 조회 및 서비스 테스트 추가/통과 | 이슈: 없음
- 2026-02-08 | 작업: CompanySectorService 트랜잭션 추가 | 결과: LazyInitializationException 방지(readOnly) | 이슈: 없음
- 2026-02-08 | 작업: 분기키 정규화 로직 보강 | 결과: 6자리 분기키 입력을 5자리로 변환, CompanyInfoService 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverview 임시 컨트롤러 추가 | 결과: Swagger 확인용 API 및 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverviewService 추가 | 결과: 개요 응답 조립 서비스/테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanyOverview DTO 추가 | 결과: Overview 응답/하위 DTO 및 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: H2 키 메트릭 텍스트 타입 정합화 | 결과: key_metric_descriptions/ai_comment H2 컬럼 타입을 VARCHAR로 조정 | 이슈: 없음
- 2026-02-08 | 작업: MetricDescriptionEntity TEXT 매핑 정합화 | 결과: @Lob 제거 후 TEXT 컬럼 정의로 스키마 검증 오류 해결 | 이슈: 없음
- 2026-02-08 | 작업: company_key_metrics ai_comment 타입 정합화 | 결과: ai_comment 컬럼을 TEXT로 매핑하도록 엔티티 수정 | 이슈: 없음
- 2026-02-08 | 작업: CompanyInfoService 추가 | 결과: 분기 기준 기업 정보 조회 서비스/테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanyInfoDto 필드 변경 | 결과: id 타입 Long 변경 및 stockCode 필드 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanyInfoDto 추가 | 결과: CompanyInfoDto 및 단위 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanySectorDto 추가 | 결과: key/label 필드 DTO 추가 | 이슈: 없음
- 2026-02-08 | 작업: CompanySectorService 추가 | 결과: 업종명 기반 섹터 DTO 조회 서비스 및 테스트 추가 | 이슈: 없음
- 2026-02-08 | 작업: key_metric_descriptions 시드 데이터 추가 | 결과: MySQL/H2 시드 마이그레이션 추가 및 테스트 통과 | 이슈: 없음
- 2026-02-08 | 작업: 키 메트릭 엔티티/리포지토리 테스트 정합화 | 결과: TEXT 필드 @Lob 적용 및 H2 is_active BOOLEAN 정합화, 리포지토리 테스트 통과 | 이슈: 없음
- 2026-02-08 | 작업: 키 메트릭 스키마 마이그레이션 추가 | 결과: 신규 테이블/컬럼 H2 호환 포함 및 마이그레이션 테스트 통과 | 이슈: 없음
- 2026-02-07 | 작업: report_contents 비어있는 경우 placeholder 저장 | 결과: 사업보고서 응답이 빈 경우 placeholder 콘텐츠 저장 로직 추가 및 서비스 테스트 통과 | 이슈: finalize() deprecated 경고 유지
- 2026-02-07 | 작업: 인사이트 조회 시 기존 콘텐츠 기반 재사용 로직 추가 | 결과: 최신 분석에 콘텐츠 없으면 기존 report/news 콘텐츠가 있는 분석을 사용, 서비스 테스트 통과 | 이슈: finalize() deprecated 경고 유지
- 2026-02-07 | 작업: 인사이트 조회 외부 API 저장 폴백 적용 | 결과: DB 없을 때 fetchAndStore 실행 후 DB 조회로 응답 구성, 서비스 테스트 수정/통과 | 이슈: finalize() deprecated 경고 유지
- 2026-02-07 | 작업: 인사이트 조회 외부 API 폴백 및 DTO 변경 | 결과: DB 없을 때 NewsClient로 조회 후 응답 구성, CompanyInsightDto(LocalDateTime) 적용 및 서비스/컨트롤러 테스트 통과 | 이슈: finalize() deprecated 경고 유지
- 2026-02-07 | 작업: DataJpaTest Querydsl 설정 및 watchlist 평균 집계 보정 | 결과: DataJpaTest에 QuerydslConfig 적용, watchlist 평균 집계 BigDecimal 캐스팅 적용 후 테스트 통과 | 이슈: 없음
- 2026-02-07 | 작업: 보고서 지표/리포트 업로드 컨트롤러 복구 | 결과: /api/admin/reports/metrics 및 /api/admin/reports/pdf 컨트롤러 활성화, 관련 테스트 통과 | 이슈: 없음
- 2026-02-07 | 작업: 보고서 PDF 다운로드 스트리밍 전환 | 결과: /api/reports/files 컨트롤러 활성화 및 스트리밍 응답/URL 내부화, 테스트 수정/통과 | 이슈: 없음
- 2026-02-07 | 작업: 파일 다운로드 URL 응답 통일 | 결과: /api/files/{id}/url이 내부 다운로드 URL만 반환하도록 변경, 테스트 수정/통과 | 이슈: 없음
- 2026-02-07 | 작업: AI 리포트 다운로드 스트리밍 처리 | 결과: CompanyAiController 다운로드를 스트리밍 방식으로 전환, 관련 컨트롤러 테스트 추가/통과 | 이슈: 없음
- 2026-02-07 | 작업: 파일 다운로드 스트리밍 처리 변경 | 결과: /api/files/{id}에서 리다이렉트 제거 후 스트리밍 응답 및 헤더 적용, 컨트롤러 테스트 수정/통과 | 이슈: 없음
- 2026-02-07 | 작업: 파일 스트리밍 서비스 추가 | 결과: FileStreamService 및 dev/prod 구현(Local/S3) 추가, 관련 단위 테스트 작성 | 이슈: 없음
- 2026-02-07 | 작업: Swagger CurrentUser 파라미터 숨김 및 파일 업로드 multipart 적용 | 결과: @CurrentUser에 @Parameter(hidden=true) 적용, 파일 업로드 API consumes=multipart/form-data 추가 | 이슈: 없음
- 2026-02-07 | 작업: spring-boot-devtools 의존성 추가 | 결과: build.gradle에 developmentOnly devtools 추가 | 이슈: 없음
- 2026-02-06 | 작업: 테스트 실패 정리 및 H2/Flyway 설정 보정 | 결과: test 프로필 Flyway 활성화 및 db/migration-h2 사용, H2 report_contents summary 타입 TEXT로 정합화, 중복 V16 마이그레이션 정리 후 ./gradlew test 통과 | 이슈: 없음
- 2026-02-06 | 작업: 테스트 실패 정리 및 H2/Flyway 설정 보정 | 결과: test 프로필 Flyway 활성화 및 db/migration-h2 사용, H2 report_contents summary 타입 TEXT로 정합화, 중복 V16 마이그레이션 정리 후 ./gradlew test 통과 | 이슈: 없음
- 2026-02-06 | 작업: 인사이트 폴링 첫 응답 빈 배열 이슈 수정 | 결과: CompanyInsightService.getInsights의 @Transactional 제거로 최신 커밋 데이터 조회 가능, 컨트롤러 테스트 통과 | 이슈: finalize() deprecated 경고 2건
- 2026-02-06 | 작업: 보고서 PDF/지표 입력 분리 및 미발행 버전 재사용 로직 적용 | 결과: PDF/지표 수동/엑셀/예측 입력 모두 최신 미발행 버전 재사용(해당 타입 값/ PDF 없을 때)으로 개선, /api/admin/reports/metrics JSON 수동 적재 엔드포인트 및 콘솔 갱신, 관련 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 최신 지표 조회 시 ACTUAL/PREDICTED가 있는 최신 버전 선택 보정 | 결과: 최신 버전 서브쿼리에 값 존재 조건 추가 및 서비스 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 최신 버전 조회에서 발행 조건 제거 | 결과: published 필터 제거 및 관련 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 지표 값 범위 조회 API 확장 | 결과: from/to 파라미터 지원 및 컨트롤러 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 지표 값 범위 조회 서비스 추가 | 결과: 분기 범위( from/to ) 조회 쿼리와 그룹핑 응답 구현, 서비스 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: 워치리스트 지표 값 조회 API 추가 | 결과: /api/watchlists/metric-values 컨트롤러/보안 경로/Swagger 및 컨트롤러 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 분기별 지표 값 조회 서비스 추가 | 결과: ACTUAL + 최신 발행 버전 기준 조회(분기별 그룹 응답) 구현, 서비스 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: 인사이트 폴링 첫 응답 빈 배열 이슈 수정 | 결과: CompanyInsightService.getInsights의 @Transactional 제거로 최신 커밋 데이터 조회 가능, 컨트롤러 테스트 통과 | 이슈: finalize() deprecated 경고 2건
- 2026-02-06 | 작업: 보고서 PDF/지표 입력 분리 및 미발행 버전 재사용 로직 적용 | 결과: PDF/지표 수동/엑셀/예측 입력 모두 최신 미발행 버전 재사용(해당 타입 값/ PDF 없을 때)으로 개선, /api/admin/reports/metrics JSON 수동 적재 엔드포인트 및 콘솔 갱신, 관련 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 최신 지표 조회 시 ACTUAL/PREDICTED가 있는 최신 버전 선택 보정 | 결과: 최신 버전 서브쿼리에 값 존재 조건 추가 및 서비스 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 최신 버전 조회에서 발행 조건 제거 | 결과: published 필터 제거 및 관련 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 지표 값 범위 조회 API 확장 | 결과: from/to 파라미터 지원 및 컨트롤러 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 지표 값 범위 조회 서비스 추가 | 결과: 분기 범위( from/to ) 조회 쿼리와 그룹핑 응답 구현, 서비스 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: 워치리스트 지표 값 조회 API 추가 | 결과: /api/watchlists/metric-values 컨트롤러/보안 경로/Swagger 및 컨트롤러 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 분기별 지표 값 조회 서비스 추가 | 결과: ACTUAL + 최신 발행 버전 기준 조회(분기별 그룹 응답) 구현, 서비스 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: 워치리스트 등록 컨트롤러 테스트 추가 | 결과: /api/watchlists 등록 성공/중복(409) 응답 검증 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-02-06 | 작업: 워치리스트 분기 지표 평균 조회 API 추가 | 결과: `/api/watchlists/metric-averages`(ACTUAL+비위험+최신 발행 버전 기준) 구현, 보안 경로/Swagger 반영, 서비스 테스트 및 전체 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: metric_averages 실행 추적 필드 확장 | 결과: 배치 결과에 triggerType/executionId 추가, 수동 API/스케줄러에서 실행 원인과 UUID 전달 및 로그 반영, 관련 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: metric_averages 수동/스케줄 저장 경로 추가 | 결과: 관리자 초기 실행 API(`/api/admin/metric-averages/initialize`)와 새벽 3시 스케줄러 추가, dev/prod 스케줄 설정 반영, 컨트롤러/스케줄러 테스트 통과 | 이슈: 없음
- 2026-02-06 | 작업: metric_averages 저장 정책 분리(기존 데이터 skip) | 결과: 분기/전체 저장 결과 DTO 추가 및 insert-if-missing 서비스 메서드 구현, 관련 서비스 테스트 추가 통과 | 이슈: 없음
- 2026-02-05 | 작업: Swagger 누락 컨트롤러 전수 보강 | 결과: CompanyWatchlistController에 Tag/Operation/ApiResponses/Parameter/보안요구 추가, DevHome/Favicon 컨트롤러는 @Hidden 처리, 전체 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: 뉴스 datetime 파싱 혼재 포맷 대응 | 결과: News DTO의 날짜 필드를 문자열로 수신하고 서비스에서 offset 포함/미포함을 모두 처리하는 파서(OffsetDateTime→LocalDateTime fallback) 추가, NewsArticleDto 변환 로직도 문자열 날짜 파싱 지원으로 보강, 관련 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: 뉴스 AI 응답 날짜 파싱 오류 대응 | 결과: NewsApiResponse/NewsItemResponse 날짜 타입을 LocalDateTime으로 변경하고 NewsService 저장 로직을 무오프셋 포맷에 맞게 수정, NewsClient에 DecodingException 분기 처리로 "AI Server response format error" 메시지 분리, 관련 뉴스 서비스/컨트롤러 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: 뉴스 컨트롤러 Swagger 문서 보강 | 결과: NewsController에 bearerAuth 보안 요구사항 및 200/401/404 응답 문서(@ApiResponses) 추가, 뉴스 컨트롤러 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: companies-업종 코드 외래키 마이그레이션 추가 | 결과: MySQL/H2에 V15__add_companies_industry_code_fk.sql 추가로 companies.industry_code_id 컬럼/인덱스/FK(fk_companies_industry_code, ON DELETE SET NULL) 반영 | 이슈: 없음
- 2026-02-05 | 작업: watchlist 중복 저장 정책을 충돌 에러로 변경 | 결과: 중복 등록 시 restore 대신 WATCHLIST_DUPLICATE(409, '중복저장입니다.')를 ApiResponse 오류 포맷으로 반환하도록 WatchlistErrorCode/서비스/테스트 반영 | 이슈: 없음
- 2026-02-05 | 작업: 분기 전체 대상 metric_averages 배치 서비스 구현 | 결과: quarters 전체 순회로 분기별 비위험 ACTUAL 최신버전 집계를 저장하는 MetricAverageBatchService 및 테스트 추가, 관련 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: 위험도 요약 전체 대상 배치 서비스 구현 | 결과: 최신 보고서 버전 기준(company+quarter별 max version_no)으로 risk_score_summaries를 페이지 배치 처리하는 RiskScoreBatchService/프로젝션/리포지토리 쿼리 추가, risk_level 저장(DANGER/CAUTION) 검증 테스트 포함 | 이슈: 없음
- 2026-02-05 | 작업: watchlist 대시보드 기반 기능 및 users.company_id 제거 | 결과: company_watchlists 생성(V11), users.company_id 제거(V12, MySQL/H2), Watchlist 등록/삭제/대시보드 API 및 서비스 추가(비위험 ACTUAL 최신버전 + 위험도 분리 조회), 보안 경로 반영, 서비스 테스트 추가 후 `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-05 | 작업: 분기별 비위험 지표 통계(metric_averages) 집계 기능 추가 | 결과: metric_averages 테이블 마이그레이션(V10, MySQL/H2) 추가, 비위험(`is_risk_indicator=0`) + ACTUAL + 최신 보고서 버전 기준 집계 쿼리/서비스 구현(avg/median/min/max/stddev/company_count), 집계 테스트 추가 후 `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-05 | 작업: 위험도 요약 집계(risk_score_summaries) 기능 추가 | 결과: risk_score_summaries 테이블 마이그레이션(V9, MySQL/H2) 추가, RiskLevel(UNDEFINED 포함)·RiskScoreSummary 엔티티/리포지토리/계산 서비스 구현, `is_risk_indicator=1` + `ACTUAL`만 집계하도록 쿼리 추가, 위험도 계산/UNDEFINED 처리 테스트 추가 후 `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-05 | 작업: metrics is_risk_indicator 컬럼 추가 및 기본값 회귀 검증 | 결과: MySQL/H2 Flyway 마이그레이션(V8) 추가, MetricsEntity 필드 매핑(boolean) 반영, 시드 기본값(false) 및 컬럼 미지정 INSERT 기본값(0) 검증 테스트 2건 추가 후 관련 테스트 통과 | 이슈: 없음
- 2026-02-05 | 작업: Swagger 서버 URL 상대 경로화 | 결과: OpenApiConfig의 servers를 `/`로 고정해 절대 URL(`http://...`) 주입을 제거하고 Mixed Content/CORS 위험을 차단, OpenApiConfigTest 추가 후 `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-04 | 작업: CompanyAiService PDF 생성/저장 기능 API 및 dev 콘솔 연동 | 결과: `/api/companies/{companyCode}/ai-report`(POST) 추가, ROLE_ADMIN 보안 규칙 반영, api-console에 PDF 생성 버튼/입력 추가, 관련 테스트 및 `SPRING_PROFILES_ACTIVE=test ./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-04 | 작업: api-console 기업 AI 분석 테스트 UX 보완 및 테스트 재검증 | 결과: 기업명 검색(디바운스/선택)으로 AI 분석 companyCode 자동 입력 기능 추가, `SPRING_PROFILES_ACTIVE=test ./gradlew test` 전체 통과 확인 | 이슈: 기본 프로필(dev)로 전체 테스트 시 로컬 MySQL 미기동이면 실패 가능
- 2026-02-04 | 작업: CompanyAiService 호출용 조회 컨트롤러 및 dev 콘솔 연동 추가 | 결과: `/api/companies/{companyCode}/ai-analysis` 엔드포인트와 권한(ROLE_USER) 규칙을 추가하고, api-console에 기업 AI 조회 섹션/호출 스크립트를 연결. CompanyAiControllerTest/DevConsoleControllerTest 통과 | 이슈: 없음
- 2026-02-04 | 작업: dev 콘솔 템플릿 API 경로 정합화 및 AI 응답 DTO 누락 보완 | 결과: templates 내 fetch 경로를 `/api/*` 실제 컨트롤러 매핑에 맞게 수정하고 AiAnalysisResponse DTO 및 ai.server.url 설정을 추가, `./gradlew test` 통과 | 이슈: 없음
- 2026-02-02 | 작업: 전체 테스트 실패 수정 | 결과: ReportFileDownloadControllerTest 경로를 `/api` 접두사로 정합화하고 SecurityConfig에 `/api/reports/files/*/url` ROLE_USER 규칙 추가 후 `./gradlew test` 전체 통과 | 이슈: 없음
- 2026-02-02 | 작업: 공통 응답(ApiResponse) 전면 적용 1단계(Auth) | 결과: AuthController를 ApiResponse 기반으로 통일하고 refresh 누락 토큰을 예외 처리로 전환, AuthIntegrationTest 보강 후 통과 | 이슈: 없음
- 2026-02-02 | 작업: 공통 응답(ApiResponse) 전면 적용 2단계(이메일 인증) | 결과: EmailVerificationController의 JSON/재전송 응답을 ApiResponse로 통일하고 WebMvc 테스트 보강 후 통과 | 이슈: 없음
- 2026-02-02 | 작업: 공통 응답(ApiResponse) 전면 적용 3단계(보고서 파일 URL) | 결과: `/reports/files/{id}/url` 엔드포인트 추가, 보안 규칙 반영, ReportFileDownloadControllerTest 보강 후 통과 | 이슈: 없음
- 2026-02-02 | 작업: 보고서 PDF 다운로드 API USER 권한 전환 3차 | 결과: `/reports/files/{id}` 경로로 이관하고 ROLE_USER 전용 보안 규칙, 다운로드 URL 경로 반영, 401/403/302 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 보고서 지표 조회 API USER 권한 전환 2차 | 결과: `/reports/metrics/*` 경로로 이관하고 ROLE_USER 전용 보안 규칙 및 grouped/predict-latest 401/403/200 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 기업 검색 API USER 권한 전환 1차 | 결과: `/admin/companies/search`를 `/companies/search`로 변경하고 ROLE_USER 전용 보안 규칙 및 401/403/200 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 이메일 인증 JSON 응답 모드 정비 | 결과: redirect=false에서 status/message JSON 응답을 반환하도록 DTO 추가 및 WebMvc 테스트 보강 | 이슈: 없음
- 2026-02-02 | 작업: 이메일 인증 리다이렉트 정책 강화 | 결과: verify-email에 status 매핑(success/expired/invalid/already_verified/error), returnUrl 화이트리스트 검증, redirect=true 302 처리 및 WebMvc 테스트 보강 | 이슈: 없음
- 2026-02-02 | 작업: 보고서 PDF 다운로드 API USER 권한 전환 3차 | 결과: `/reports/files/{id}` 경로로 이관하고 ROLE_USER 전용 보안 규칙, 다운로드 URL 경로 반영, 401/403/302 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 보고서 지표 조회 API USER 권한 전환 2차 | 결과: `/reports/metrics/*` 경로로 이관하고 ROLE_USER 전용 보안 규칙 및 grouped/predict-latest 401/403/200 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 기업 검색 API USER 권한 전환 1차 | 결과: `/admin/companies/search`를 `/companies/search`로 변경하고 ROLE_USER 전용 보안 규칙 및 401/403/200 테스트 추가 | 이슈: 없음
- 2026-02-02 | 작업: 이메일 인증 JSON 응답 모드 정비 | 결과: redirect=false에서 status/message JSON 응답을 반환하도록 DTO 추가 및 WebMvc 테스트 보강 | 이슈: 없음
- 2026-02-02 | 작업: 이메일 인증 리다이렉트 정책 강화 | 결과: verify-email에 status 매핑(success/expired/invalid/already_verified/error), returnUrl 화이트리스트 검증, redirect=true 302 처리 및 WebMvc 테스트 보강 | 이슈: 없음
- 2026-02-01 | 작업: favicon 요청 처리 추가 | 결과: favicon 컨트롤러 및 테스트 추가로 예외 로그 제거 | 이슈: MockBean deprecated 경고
- 2026-02-01 | 작업: dev 카테고리 API 통합 | 결과: DevCategoryController 제거 후 DevConsoleController로 통합, dev 전용 테스트 추가 | 이슈: MockBean deprecated 경고
- 2026-02-01 | 작업: dev 인증 콘솔 컨트롤러 통합 | 결과: AuthConsoleController 제거 후 DevConsoleController로 통합 | 이슈: 없음
- 2026-02-01 | 작업: dev 콘솔 경로 정리 | 결과: /dev/auth/console로 경로 이관 및 템플릿 링크 정합화 | 이슈: 없음
- 2026-02-01 | 작업: 이메일 인증 리다이렉트 옵션 추가 | 결과: redirect 파라미터 지원 및 프론트 리다이렉트 URL 응답, 관련 MockMvc 테스트 추가 | 이슈: MockBean deprecated 경고
- 2026-02-01 | 작업: 파일 다운로드 URL 조회 API 추가 | 결과: /files/{id}/url 응답 및 MockMvc 테스트 추가 | 이슈: MockBean deprecated 경고
- 2026-02-01 | 작업: 카테고리 조회 API 추가 | 결과: /categories 컨트롤러 및 MockMvc 테스트 추가, 공개 경로 허용 | 이슈: MockBean deprecated 경고
- 2026-02-01 | 작업: prod 보안/운영 설정 정비 | 결과: 공개 경로 축소, CORS origins 외부화, prod actuator/health/swagger 설정 보강 | 이슈: 없음
- 2026-02-01 | 작업: prod compose에서 mysql 비활성화 | 결과: docker-compose.prod.yml에 mysql profiles=dev 추가 | 이슈: 없음
- 2026-02-01 | 작업: storage_key 테스트 복구 | 결과: H2 마이그레이션 추가 및 test Turnstile 시크릿 기본값 설정, 전체 테스트 통과 | 이슈: 없음
- 2026-01-31 | 작업: 리프레시 토큰 쿠키 정책 분리 | 결과: SameSite/secure 설정을 프로파일별로 적용하고 테스트 보강 | 이슈: MockBean deprecated 경고
- 2026-01-31 | 작업: 헬스체크 접근 테스트 안정화 | 결과: 테스트용 컨트롤러 분리 및 MockMvc 테스트 통과 | 이슈: MockBean deprecated 경고
- 2026-01-31 | 작업: 헬스체크 엔드포인트 접근 허용 | 결과: /actuator/health permitAll 추가 | 이슈: 없음
- 2026-01-31 | 작업: 다운로드 리다이렉트 통합 테스트 추가 | 결과: presigned URL 응답 흐름 검증 | 이슈: MockBean deprecated 경고
- 2026-01-31 | 작업: S3 presigned URL 다운로드 준비 | 결과: resolver 추가 및 presigner 설정, 단위 테스트 통과 | 이슈: 없음
- 2026-01-31 | 작업: files에 storage_key 추가 | 결과: 저장 메타데이터 확장 및 저장소 테스트 보강 | 이슈: 없음
- 2026-01-31 | 작업: 파일 업로드 크기 설정 분리 | 결과: FILE_MAX_SIZE_BYTES 도입 및 바인딩 테스트 보강 | 이슈: 없음
- 2026-01-31 | 작업: prod에서 Redis만 compose로 실행 | 결과: start-server에서 prod 프로필 감지 시 Redis만 기동 | 이슈: 없음
- 2026-01-31 | 작업: README 환경변수 요약 추가 | 결과: 공통/메일/S3 필수 변수 정리 | 이슈: 없음
- 2026-01-31 | 작업: .env.example 환경변수 목록 정리 | 결과: 공통/DB(dev/prod 예시) 변수 한눈에 정리 | 이슈: 없음
- 2026-01-31 | 작업: prod 이메일 설정 import 추가 | 결과: prod 프로파일에서도 application-email.yml 로딩 | 이슈: 없음
- 2026-01-30 | 작업: prod 이메일 인증 유지 확인 | 결과: prod 설정에 skip=false 유지, dev는 skip=true로 복구 | 이슈: 없음
- 2026-01-30 | 작업: env 예시/운영 env 템플릿 정합 | 결과: LOG_PATH/LOG_FILE 추가 및 README 로컬 실행 예시 갱신 | 이슈: 없음
- 2026-01-30 | 작업: dev/prod DB 환경변수 체계 통일 | 결과: SPRING_DATASOURCE_* 기준으로 dev/prod 설정 정렬 | 이슈: 없음
- 2026-01-30 | 작업: dev 전용 JPA SQL 로그/Turnstile 기본값 분리 | 결과: 공통 설정에서 dev 옵션 제거 및 prod에 Turnstile 키 요구 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 그룹/예측 응답 매퍼 적용 | 결과: ReportMapper에 그룹/예측 응답 매핑 추가 및 테스트 보강 | 이슈: 없음
- 2026-01-30 | 작업: DART 동기화 응답 매핑 MapStruct 전환 | 결과: JobExecution → DartCorpSyncResponse 매퍼 추가 | 이슈: 없음
- 2026-01-30 | 작업: DTO 매핑 MapStruct 전환 주석 보강 | 결과: mapper 전반 매핑 규칙 설명 주석 추가 및 테스트 통과 | 이슈: 없음
- 2026-01-30 | 작업: 배치 런타임 의존성 보강 | 결과: spring-batch-core 추가로 ItemProcessor 클래스 로딩 실패 방지 | 이슈: 없음
- 2026-01-30 | 작업: 테스트 프로파일/데이터 의존성 정리 | 결과: PostsRepositoryTest 등에서 test 프로파일 적용, 엑셀 파서 테스트는 메모리 워크북으로 전환 | 이슈: 없음
- 2026-01-30 | 작업: 예측 보고서 콘솔 토큰 로딩 수정 | 결과: auth-console.tokens 기반으로 관리자 API 호출 가능 | 이슈: 없음
- 2026-01-30 | 작업: 예측 보고서 콘솔 경로 허용 | 결과: report-predict-console 보안 허용 설정 추가 | 이슈: 없음
- 2026-01-30 | 작업: 예측 보고서 조회 콘솔 추가 | 결과: 최신 예측값/PDF 조회 전용 dev 페이지 추가 | 이슈: 없음
- 2026-01-30 | 작업: 예측 조회/보고서 PDF 다운로드 API 추가 | 결과: predict-latest 조회 및 PDF 다운로드 API/테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: 최신 예측값 조회 서비스 추가 | 결과: 최신 버전 PDF/예측값 조회 로직 및 테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: API 콘솔 보고서 발행 폼 추가 | 결과: 지표+PDF 발행 API를 콘솔에서 테스트 가능 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표+PDF 발행 API 추가 | 결과: multipart 발행 API 및 테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표+PDF 발행 서비스 추가 | 결과: 예측/실제값 저장 후 PDF 연결 및 published 처리 | 이슈: 없음
- 2026-01-30 | 작업: API 콘솔 예측값 적재 UI 추가 | 결과: stockCode/quarterKey/metrics 입력 후 예측값 적재 요청 가능 | 이슈: 없음
- 2026-01-30 | 작업: API 콘솔 기업 검색 UI 개선 | 결과: 디바운싱 결과를 입력 아래 리스트로 표시 | 이슈: 없음
- 2026-01-30 | 작업: 예측 지표 적재 API 추가 | 결과: 예측값 컨트롤러/테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 예측값 적재 서비스 추가 | 결과: metric_name_en 기반 적재 서비스/DTO/테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: Auth Console 보호 API UX 개선 | 결과: 토큰 상태에 따른 버튼 제어/실패 메시지 보강 | 이슈: 없음
- 2026-01-30 | 작업: 단일 로그아웃 Access 토큰 무효화 | 결과: logout 시 블랙리스트 적용 및 로그아웃 테스트 보강 | 이슈: 없음
- 2026-01-30 | 작업: 로그아웃 전체 처리 검증 보강 | 결과: AccessTokenValidator 적용 및 로그아웃 후 클레임 실패 테스트 추가 | 이슈: 없음
- 2026-01-30 | 작업: 로그아웃 테스트 중복 Role 처리 보정 | 결과: RoleRepository로 재사용 처리 | 이슈: 없음
- 2026-01-30 | 작업: Auth Console 로그아웃 버튼 동작 보강 | 결과: 로그아웃 성공 시 로컬 토큰 초기화 처리 | 이슈: 없음
- 2026-01-30 | 작업: 기업 검색 API/콘솔 연동 | 결과: 검색 컨트롤러/테스트/문서 및 콘솔 UI 연동 | 이슈: 없음
- 2026-01-30 | 작업: 기업 검색 서비스 추가 | 결과: corp_name/corp_eng_name 검색 로직 및 테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: dev API 콘솔에 보고서 지표 조회 버튼 추가 | 결과: 분기별 그룹 조회 UI 입력/호출 추가 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 조회 API 추가 | 결과: 그룹핑 조회 컨트롤러/테스트 및 문서 추가 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 분기별 그룹핑 응답 추가 | 결과: 그룹 DTO/서비스 처리 및 테스트 보강 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 조회 서비스 추가 | 결과: 최신 버전 조회 서비스/DTO 및 테스트 구현 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 최신 버전 조회 리포지토리 추가 | 결과: Projection/쿼리/테스트 추가 | 이슈: 없음
- 2026-01-30 | 작업: 비동기 로그 설정 추가 | 결과: 콘솔/파일 AsyncAppender 및 롤링 정책 적용 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 기업코드 패딩 규칙 보정 | 결과: 비숫자 포함 코드도 6자리 미만이면 좌측 패딩 적용 | 이슈: 없음
- 2026-01-30 | 작업: 보고서 지표 기업코드 정규화 조정 | 결과: 비숫자 포함 코드는 원문 유지, 숫자 코드는 패딩 유지 | 이슈: 없음
- 2026-01-29 | 작업: 보고서 지표 적재 로그 보강 | 결과: 엑셀 행/컬럼/헤더 기반 로그 추가 및 중복 감지 로그 보강 | 이슈: 없음
- 2026-01-29 | 작업: API 콘솔/문서 업데이트 | 결과: 보고서 지표 업로드 버튼 추가 및 문서화 | 이슈: 없음
- 2026-01-29 | 작업: 보고서 지표 업로드 API 추가 | 결과: 관리자 업로드 컨트롤러 및 MockMvc 테스트 구현 | 이슈: 없음
- 2026-01-29 | 작업: 엑셀 지표 파서 추가 | 결과: input_demo.xlsx 헤더 매핑 파서 구현 및 테스트 추가 | 이슈: 없음
- 2026-01-29 | 작업: 보고서 지표 적재 서비스 1단계 | 결과: 보고서/분기 생성 메서드 추가 및 적재 서비스/테스트 구현 | 이슈: 없음
- 2026-01-29 | 작업: 분기 유틸/리포지토리 추가 | 결과: QuarterCalculator 및 보고서/분기/지표 리포지토리 추가, 분기 계산 테스트 작성 | 이슈: 없음
- 2026-01-29 | 작업: DART 응답 버퍼 상향 설정 | 결과: WebClient maxInMemorySize 50MB 기본값 및 프로퍼티(dart.api.max-buffer-size) 추가 | 이슈: 없음
- 2026-01-29 | 작업: API 콘솔에 DART 동기화 버튼 추가 | 결과: /admin/dart/corp-sync 호출 버튼 및 로그 출력 추가 | 이슈: 없음
- 2026-01-29 | 작업: dev 관리자 회원가입 옵션 추가 | 결과: auth-console에 관리자 체크박스 추가, SignupRequest/SignUpService에 dev 전용 ROLE_ADMIN 처리 | 이슈: 없음
- 2026-01-29 | 작업: 기업 동기화 JDBC 조회 컴파일 오류 수정 | 결과: NamedParameterJdbcTemplate query 모호성 해소 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 동기화 운영 가이드 문서화 | 결과: 수동 실행 API/스케줄/환경변수/타임아웃 설정 정리 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 동기화 테스트 추가 | 결과: Reader ZIP/XML 파싱 테스트 및 Processor 정규화 테스트 추가 | 이슈: 없음
- 2026-01-29 | 작업: DART 배치 운영 설정 보강 | 결과: WebClient 타임아웃/재시도 설정, Batch 이력 테이블 마이그레이션 추가 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 동기화 수동 실행/스케줄 구성 | 결과: 관리자 API, Job 실행 서비스, 스케줄러 토글 및 보안 경로 추가 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 동기화 Reader/Writer 구현 | 결과: ZIP/XML 파싱 Reader, 변경분 기준 업서트 Writer 및 JDBC 저장소 추가 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 동기화 배치 스캐폴딩 | 결과: Batch 의존성 추가, DART 설정/배치 Job·Step·Reader/Processor/Writer 스켈레톤 구성, 자동 실행 비활성화 | 이슈: 없음
- 2026-01-29 | 작업: DART 기업 목록 동기화 요구사항 정리 | 결과: 초기 적재 후 변경분 갱신, 스케줄 기본 OFF, 관리자 API 트리거, DART_API_KEY 확정 | 이슈: 없음
- 2026-01-29 | 작업: dev MySQL 전환 및 마이그레이션 정리 | 결과: dev datasource를 MySQL로 변경, docker compose에 mysql 추가, H2 마이그레이션 분리(db/migration-h2), metrics 시드 추가 | 이슈: 없음
- 2026-01-29 | 작업: Auditing 통합 및 테스트 정합 | 결과: BaseEntity에 createdBy/updatedBy 통합, SecurityAuditorAware 추가, userId 클레임 추가 및 엔티티/서비스/테스트 정리 | 이슈: MockBean deprecated 경고 지속
- 2026-01-28 | 작업: dev Auth Console 호환성 보강 | 결과: /auth/logout, /auth/logout-all 미구현 시 안내 및 버튼 비활성화 처리 | 이슈: 없음
- 2026-01-28 | 작업: ERD 문서 정리 | 결과: ERD.md 추가 및 README에 스키마 안내 섹션 추가 | 이슈: 없음
- 2026-01-28 | 작업: UserRoleRepository 컴파일 오류 수정 | 결과: 잘못된 \\t 문자 제거로 빌드 복구, 전체 테스트 통과 | 이슈: 없음
- 2026-01-28 | 작업: 파일/인증 테스트 정합화 | 결과: session 키 변경 및 post_files 매핑 기반 조회로 통합 테스트 보정, 테스트 설정에 TestSecurityConfig 적용 | 이슈: 없음
- 2026-01-28 | 작업: H2 마이그레이션/테스트 프로파일 정리 | 결과: H2 전용 Flyway 스키마 추가, dev/test DB URL 및 Flyway 경로 정비, CompaniesEntity CHAR 컬럼 정합 | 이슈: 없음
- 2026-01-28 | 작업: 테스트 실패 원인 수정(카테고리 중복/테스트 JWT 키) | 결과: CommentIntegrationTest 카테고리 이름 고유화, EmailVerificationServiceTest에 TestSecurityConfig 적용 | 이슈: MockBean deprecated 경고 확인
- 2026-01-28 | 작업: Flyway 도입 + ERD 정합화 1~2단계 | 결과: V1 초기 마이그레이션/ddl-auto validate 적용, ROLE_ 통일/refresh_tokens·files·조회수 분리 및 신규 엔티티 추가, 테스트 보정 | 이슈: 없음
- 2026-01-28 | 작업: SENTINEL PoC OpenAPI 스펙 Swagger UI 연결 | 결과: 정적 YAML 제공 및 Swagger UI 다중 문서 목록 추가 | 이슈: 없음
- 2026-01-27 | 작업: SecurityConfig 정적 리소스 허용 및 CSP 헤더 추가 | 결과: favicon/정적 경로 permitAll, Turnstile 도메인 포함 CSP를 Report-Only로 설정 | 이슈: 없음
- 2026-01-27 | 작업: Turnstile 디버그 로그 강화 | 결과: 검증 요청/응답 상세 로그 추가 및 dev에서 debug 활성화 | 이슈: 없음
- 2026-01-27 | 작업: dev Turnstile 콘솔/회원가입 위젯 추가 및 검증 API 연결 | 결과: auth/turnstile 콘솔에서 토큰 발급·검증·회원가입까지 확인 가능, dev 키 기본값 제거 | 이슈: 없음
- 2026-01-26 | 작업: CodeDeploy 재시작 안정화 | 결과: stop-server 프로세스 탐지 보강 및 start-server에서 재시작 보장 | 이슈: 없음
- 2026-01-26 | 작업: dev 홈 페이지 및 CodeDeploy 실행 명령 정비 | 결과: 루트 페이지 연결 화면 추가, dev 보안 허용, start-server 실행 커맨드 변경 | 이슈: 없음
- 2026-01-26 | 작업: CodeBuild gradlew 실행 권한 보정 | 결과: buildspec에 chmod +x gradlew 추가 | 이슈: 없음
- 2026-01-25 | 작업: SpringDoc 버전 재업데이트 | 결과: 2.6.0 -> 2.8.15로 변경하여 Spring 6.2 호환성 확보 | 이슈: 확인 중
- 2026-01-25 | 작업: SpringDoc 버전 업그레이드 | 결과: 2.3.0 -> 2.6.0로 변경하여 OpenAPI 생성 오류 대응 | 이슈: 확인 중
- 2026-01-25 | 작업: 작업 에이전트 워크플로우 문서화 | 결과: 단위 작업/테스트/커밋/종합 테스트 절차 명시 | 이슈: 없음
- 2026-01-25 | 작업: Swagger 공개 경로 보안 허용 | 결과: /swagger-ui, /api-docs 인증 예외 처리 | 이슈: 없음
- 2026-01-25 | 작업: Swagger API 버전 관리 설정 | 결과: OpenAPI 버전/베이스 경로 설정값 추가 | 이슈: 없음
- 2026-01-25 | 작업: Swagger UI 커스텀 테마 적용 | 결과: custom.css 추가 및 springdoc 설정 반영 | 이슈: 없음
- 2026-01-25 | 작업: API 예외 응답 문서화 | 결과: 주요 컨트롤러에 400/401/403/404/500 응답 정의 추가 | 이슈: 없음
- 2026-01-25 | 작업: 에러 응답 Swagger 스키마 추가 | 결과: ErrorResponse/FieldErrorResponse에 @Schema 적용 | 이슈: 없음
- 2026-01-25 | 작업: 개발/콘솔 및 이메일 인증 API 문서화 | 결과: EmailVerification/Dev/AuthConsole 컨트롤러에 Swagger 메타데이터 추가 | 이슈: 없음
- 2026-01-25 | 작업: 파일 컨트롤러 Swagger 문서화 | 결과: File 업로드/조회/다운로드 API에 Tag/Operation/Response/파라미터 설명 추가 | 이슈: 없음
- 2026-01-25 | 작업: Swagger 보안/공개 엔드포인트 정리 | 결과: 공개 엔드포인트 security 비활성화, 파일 API에 보안 요구사항 추가 | 이슈: 없음
- 2026-01-25 | 작업: 핵심 컨트롤러 Swagger 문서화 | 결과: Auth/Post/Comment 컨트롤러에 Tag/Operation/Response/보안 요구사항 추가 | 이슈: 없음
- 2026-01-25 | 작업: 파일 DTO Swagger 스키마 추가 | 결과: FileResponse에 @Schema 적용 | 이슈: 없음
- 2026-01-25 | 작업: 핵심 DTO Swagger 스키마 추가 | 결과: Auth/Post/Comment/ApiResponse DTO에 @Schema 적용 | 이슈: 없음
- 2026-01-25 | 작업: Swagger 기본 설정 추가 | 결과: OpenApiConfig 생성 및 springdoc 설정 추가 | 이슈: 없음
- 2026-01-25 | 작업: test 프로파일 파일 스토리지 스텁 추가 | 결과: TestFileStorageConfig로 FileStorageService 기본 빈 제공, EmailVerificationServiceTest에서 스텁 의존 제거 | 이슈: 없음
- 2026-01-25 | 작업: 전체 테스트 실패 원인 분석 및 수정 | 결과: EmailVerificationServiceTest에 FileStorageService MockBean 추가로 컨텍스트 로딩 실패 해결 | 이슈: 없음
- 2026-01-23 | 작업: 파일 조회/다운로드 및 콘솔 화면 개선 | 결과: 파일 목록/다운로드 API 추가 및 dev 콘솔에서 조회 지원 | 이슈: 없음
- 2026-01-23 | 작업: 파일 업로드 콘솔 페이지 추가 | 결과: /dev/file-console 템플릿 및 링크 추가 | 이슈: 없음
- 2026-01-23 | 작업: 파일 업로드 통합/설정 테스트 추가 | 결과: MockMvc 통합, 설정 바인딩, 스텁 스토리지 테스트 추가 | 이슈: 없음
- 2026-01-23 | 작업: 파일 업로드 테스트 추가 | 결과: 검증/서비스/로컬/S3 스토리지 단위 테스트 작성 및 통과 | 이슈: 없음
- 2026-01-23 | 작업: 파일 업로드 커스텀 에러코드/예외 추가 | 결과: FileErrorCode/FileException/검증 상세 오류 분리 | 이슈: 없음
- 2026-01-23 | 작업: 전역 파일 업로드 설정 추가 | 결과: multipart 설정 및 업로드 정책/검증기 적용 | 이슈: 없음
- 2026-01-23 | 작업: 파일 업로드 기본 구성 추가 | 결과: 저장소/서비스/컨트롤러/설정/마이그레이션 추가 | 이슈: 없음
- 2026-01-23 | 작업: 파일 엔티티 추가 | 결과: files 테이블 매핑 엔티티 생성 | 이슈: 없음
- 2026-01-23 | 작업: 서비스 시그니처를 UserEntity/ID 기반으로 정리 | 결과: Post/Comment 서비스 중복 사용자 조회 제거 및 테스트 수정 | 이슈: 없음
- 2026-01-23 | 작업: @CurrentUser 리졸버 추가 및 컨트롤러 적용 | 결과: Post/Comment 컨트롤러에서 JWT subject 파싱 제거, 관련 테스트 통과 | 이슈: 없음
- 2026-01-23 | 작업: prod 이메일 인증 설정 추가 | 결과: application-prod.yaml에 base-url 및 skip 설정 추가 | 이슈: 없음
- 2026-01-23 | 작업: 이메일 인증 URL 외부 접속 대응 | 결과: 인증 링크 base-url 설정 추가 및 EmailService에서 구성값 사용 | 이슈: 없음
- 2026-01-23 | 작업: 미인증 로그인 응답 분리 및 이메일 발송 테스트 보강 | 결과: AuthErrorCode/로그인 예외 처리 추가, 회원가입 이메일 발송 테스트 추가 | 이슈: 없음
- 2026-01-23 | 작업: compose 오버라이드 표준화 | 결과: docker-compose.override.yml 추가 및 dev 파일 제거 | 이슈: 없음
- 2026-01-23 | 작업: docker compose dev/prod 오버라이드 추가 | 결과: docker-compose.dev.yml/docker-compose.prod.yml 추가 및 기본 compose 정리 | 이슈: 없음
- 2026-01-23 | 작업: Redis compose 파일 통합 | 결과: docker-compose.redis.yml 삭제 후 docker-compose.yml로 통합 | 이슈: 없음
- 2026-01-23 | 작업: Redis docker compose 파일 추가 | 결과: 개발용 Redis 실행용 docker-compose.redis.yml 추가 | 이슈: 없음
- 2026-01-23 | 작업: dev 회원가입 이메일 인증 스킵 처리 | 결과: dev 프로파일에서 이메일 인증 생략 및 SignUpService 테스트 보강 | 이슈: 없음
- 2026-01-23 | 작업: 이메일 인증 기능 추가 | 결과: 이메일 인증 엔티티/서비스/컨트롤러, 마이그레이션 및 테스트 추가 | 이슈: 없음
- 2026-01-23 | 작업: 테스트 메일 빈 모킹 및 dev 이메일 설정 import | 결과: 테스트용 JavaMailSender 모킹 추가, dev 프로파일에서 application-email.yml 로딩 | 이슈: 없음
- 2026-01-22 | 작업: 댓글 CRUD 컨트롤러 및 통합 테스트 추가 | 결과: CommentController와 CRUD 통합 테스트 추가, 전체 테스트 통과 | 이슈: 없음
- 2026-01-22 | 작업: Repository 슬라이스 테스트 추가 | 결과: PostsRepository/CommentsRepository DataJpaTest 추가 및 전체 테스트 통과 | 이슈: 없음
- 2026-01-22 | 작업: AccessTokenBlacklistService 단위 테스트 추가 | 결과: 블랙리스트/로그아웃 기준 로직 단위 테스트와 전체 테스트 통과 | 이슈: 없음
- 2026-01-22 | 작업: 서비스 레이어 단위 테스트 추가 | 결과: PostService/UserDomainService 테스트 추가 및 전체 테스트 통과 | 이슈: 없음
- 2026-01-22 | 작업: 게시글 JWT 컨트롤러/페이징 및 테스트 보강 | 결과: /posts 페이징 API 및 JWT subject 검증 테스트 추가, CommonException 핸들러 추가 | 이슈: 없음
- 2026-01-21 | 작업: RefreshTokenServiceTest 불필요 스텁 제거 | 결과: 테스트별로 Redis 스텁 주입하도록 변경 | 이슈: 없음
- 2026-01-21 | 작업: 회원가입 API 추가 | 결과: /auth/signup, USER 역할 매핑, 서비스/DTO/테스트 추가 | 이슈: 없음
- 2026-01-21 | 작업: 로그아웃 전체 로그아웃 기준 시각 검증 보정 | 결과: issuedAt == logoutAllAt 허용 및 검증 테스트 추가 | 이슈: 없음
- 2026-01-21 | 작업: 로그아웃/전체 로그아웃 및 블랙리스트 구현 | 결과: Access 토큰 블랙리스트/전체 로그아웃 검증 추가, 로그아웃 API 및 리프레시 토큰 폐기 로직/테스트 보강 | 이슈: 없음
- 2026-01-21 | 작업: 업스트림 로그아웃 이슈 등록 | 결과: 로그아웃/전체 로그아웃 및 Access Token 블랙리스트 이슈 생성 | 이슈: 없음
- 2026-01-21 | 작업: 테스트 보안 설정 정리 | 결과: TestSecurityConfig 추가 및 테스트 전반 Import 적용, JWT 키 파일 의존 제거 | 이슈: 없음
- 2026-01-21 | 작업: 인증 통합 테스트/의존성 정리 | 결과: Auth 통합 테스트와 Redis 컨테이너 적용, 테스트 의존성 최소화, 실패 케이스 단위 테스트 보강 | 이슈: 없음
- 2026-01-21 | 작업: 전역 예외 처리 리팩터링 | 결과: 공통 에러 코드/필드 에러 응답/GlobalExceptionHandler 추가 및 Auth 핸들러 통합 | 이슈: 없음
- 2026-01-20 | 작업: 이슈 템플릿 추가 | 결과: 일반 이슈 템플릿 생성 | 이슈: 없음
- 2026-01-20 | 작업: 테스트 설명/주석 보강 및 오류 수정 | 결과: Auth/RefreshToken 테스트 DisplayName과 given-when-then 추가 | 이슈: 없음
- 2026-01-20 | 작업: JWT 인증 단위 테스트 추가 | 결과: Auth/RefreshToken/JwtToken 테스트 작성 | 이슈: 없음
- 2026-01-20 | 작업: 레거시 ROLE_ 토큰 컷오프 적용 | 결과: 컷오프 이후 인증 실패 처리 | 이슈: 없음
- 2026-01-20 | 작업: JWT 역할 호환 전환 구현 | 결과: ROLE_ 레거시 허용/만료 설정 추가 | 이슈: 없음
- 2026-01-20 | 작업: JWT 권한 변환기 수정 | 결과: roles 클레임 처리 방식 권장안 반영 | 이슈: 없음
- 2026-01-20 | 작업: JWT 인증 후속 작업 계획 정리 | 결과: 로그아웃/블랙리스트/Lua 동시성/마이그레이션 단계화 | 이슈: 없음
- 2026-01-20 | 작업: JWT 인증 Phase1 구성 | 결과: 설정/토큰/리프레시 저장소 및 로그인·리프레시 API 추가 | 이슈: 없음
- 2026-01-20 | 작업: JWT 인증 로직 추가 준비 | 결과: feat/jwt-auth 브랜치 생성 | 이슈: 없음
- 2026-01-20 | 작업: withdraw 재호출 동작 테스트 추가 | 결과: 삭제 상태 유지 검증 | 이슈: 없음
- 2026-01-20 | 작업: UserEntity UUID 자동 생성 테스트 추가 | 결과: 저장 시 UUID 생성 검증 | 이슈: 없음
- 2026-01-20 | 작업: UserDetails 테스트 DisplayName 추가 | 결과: 테스트 목적을 한국어로 명시 | 이슈: 없음
- 2026-01-20 | 작업: GrantedAuthority 제네릭 타입 오류 수정 | 결과: CustomUserDetails 스트림 타입 명시 | 이슈: 없음
- 2026-01-20 | 작업: 코드 주석 한글화 | 결과: main/test 주석을 한국어로 변경 | 이슈: 없음
- 2026-01-20 | 작업: 코드 전반 간단 주석 추가 | 결과: main/test 클래스에 1~2줄 설명 주석 삽입 | 이슈: 없음
- 2026-01-20 | 작업: UserEntity 테스트 생성자 접근 이슈 해결 | 결과: 리플렉션 기반 생성 로직 추가 | 이슈: 없음
- 2026-01-20 | 작업: UserDetails/withdraw 테스트 추가 | 결과: H2 기반 서비스/도메인 테스트 작성 | 이슈: 없음
- 2026-01-20 | 작업: UserEntity 삭제/탈퇴 도메인 메서드 추가 | 결과: withdraw로 상태 전이와 soft delete 처리 | 이슈: 없음
- 2026-01-20 | 작업: UserDetails 조회 fetch join 최적화 | 결과: 이메일 기준 user_roles fetch join 추가 | 이슈: 없음
- 2026-01-20 | 작업: 분리형 UserDetails 구현 | 결과: CustomUserDetails/Service 및 저장소 추가 | 이슈: 없음
- 2026-01-20 | 작업: RoleName 값 수정 및 UserRoleEntity 추가 | 결과: USER/ADMIN 역할과 user_roles 매핑 엔티티 설계 | 이슈: 없음
- 2026-01-20 | 작업: UserEntity 정리 및 RoleEntity 추가 | 결과: roles 매핑 설계 및 BaseEntity 삭제 로직 개선 | 이슈: 없음
- 2026-01-20 | 작업: UserEntity/Status 추가 | 결과: users 테이블 매핑 엔티티 설계 | 이슈: 없음
- 2026-01-20 | 작업: H2 콘솔 접근 문제 완화 설정 추가 | 결과: dev 프로필 보안 설정에 H2 콘솔 허용 | 이슈: 없음
- 2026-01-20 | 작업: 개인 로그 파일 생성 | 결과: 템플릿 생성 | 이슈: 없음

## 3. History (이전 기록)
- YYYY-MM-DD | 작업: ... | 결과: ... | 이슈: ...
- 2026-02-06 | 작업: 보고서 PDF 단독 업로드 서비스 추가 | 결과: CompanyReportPdfPublishService 및 테스트 추가(기업 미존재 스킵), PDF만 업로드해 버전 발행 처리 | 이슈: 없음
- 2026-02-06 | 작업: 보고서 PDF 단독 업로드 API 추가 | 결과: /api/admin/reports/pdf 컨트롤러 및 테스트 추가, PDF 단일 업로드 지원 | 이슈: 없음

- 2026-02-26 | 작업: AI 리포트 다운로드 예외 처리 보강 | 결과: CompanyAiController에서 PDF 미존재(IllegalArgumentException) 시 404 응답 처리 및 Controller 테스트 추가, ./gradlew test 전체 통과 | 이슈: 없음
- 2026-11-02 | 작업: 개발 부산물과 현재 코드의 로컬 복원 가능성 점검 | 결과: 첫 번째 자료 폴더의 LOCAL_RESTORATION_AUDIT_2026-11-02.md에 기능별 조건/미확보 원본 기록. 동일 소스 영문 임시 경로에서 384 통과, 15 skipped(AuthIntegrationTest 제외, Kafka 시작 비활성화), H2+mock 서버 및 프런트 빌드 확인 | 이슈: 원래 경로 Test Worker ClassNotFoundException, 로컬 인프라/과거 AI 결과 및 DB 전체 백업 미확인. 애플리케이션 소스 변경 없음
- 2026-11-02 | 작업: 포트폴리오 장애 복구·데이터 정합성·성능 보강 계획 | 결과: docs/superpowers/specs/2026-11-02-portfolio-reliability-design.md와 plans/2026-11-02-portfolio-reliability-roadmap.md 작성, 독립 builder 검토 완료. CDC 우선/불가 시 폴링, 요청 원장+Outbox/Inbox·durable job, 재시작 가능한 데이터 적재, MySQL 기준선, 측정 기반 Redis, 장애 시험과 증거 작성 순서 제안 | 이슈: 사용자 계획 검토 전이며 구현/인프라 설치/DB 변경 미수행. Redis 적용 대상은 검토 후 제안하도록 사용자 위임, 연도별 대량 데이터는 후속 범위
- 2026-11-03 | 작업: 포트폴리오 목적에 맞춘 설계/계획 재검토 | 결과: 기존 두 문서에 실제 배포를 가정한 로컬 검증 목적, 필수/조건부/후속 범위, 새 요청 성능 기준선 시점, 정상종료/과부하/역할별 상태 확인·복구 절차, FAILED 관리 재개와 snapshot 고정 시험 반영. 독립 검토 지적 2건 반영 및 링크/구조 검증 | 이슈: 문서만 변경했으며 애플리케이션 구현/인프라 설치/DB 적재/새 기능 시험은 미수행
- 2026-11-03 | 작업: 승인된 설계를 세분화된 애플리케이션 구현·측정 실행 계획으로 구체화 | 결과: docs/superpowers/plans/2026-11-03-execution-index.md 및 공통 계약/하위 4계획/측정 프로토콜 작성. 25개 작업 정의(CDC/폴링 택일로 기본 24개), 작업별 파일/인터페이스/실패 시험/assertion/명령/완료 기준과 125개 체크 단계. 실제 source/query/CORS/파일 접근 경계 대조, migration 순서와 worker fencing 검토 | 이슈: 계획 문서만 작성, 구현·의존성 설치·DB 적재·새 성능/장애 실험 미실행. 설정/부하 수치는 실험 입력값이며 성능 결과 아님


### 2026-11-03 | E01 Ubuntu 실행 기준선

- 애플리케이션·Gradle·Docker 시험의 실행 OS를 WSL2 Ubuntu로 확정했다. 원본 main의 기존 수정 사항을 보존하고 managed worktree와 Linux 소스 사본을 사용한다.
- build.gradle에 fastTest/realInfraTest/cdcTest/reliabilityTest/verifiedCheck를 추가했다. 기존 test 필터·보안 프로필·Mockito agent는 유지한다.
- Python XML 검증기와 PowerShell 호환 진입점, 소스 지문/시험 태그 수집기, 관련 단위 시험을 추가했다. 빈 결과·불일치·stale XML·전체 skip을 성공으로 취급하지 않는다.
- Ubuntu fast 회귀: 399개 발견, 384개 실행·통과, 15개 기존 skip, 실패/error 0. 기존 parser와 fast parser 각각 1/1. 원본 src 550개 SHA-256 불일치 0.
- 실측 환경: Ubuntu 26.04 / Java 21.0.12.1 / Gradle 8.14.3 / Docker 29.1.3 / Spring Kafka 3.3.11. 상세는 docs/runtime/dependency-lock.md 및 Git 제외 원자료 perf/results/20261103-e01-wsl/.
- CDC/reliability 시험은 아직 없으며 실제 인프라·앱 기동·성능/장애 실험은 미수행이다. 다음 작업은 E02이고 이번에 자동 실행하지 않는다. 커밋·push 미수행.

- E01 독립 검토: 중대 이슈 없음. 전체 gate 보고 Minor 2건(CDC N/A 자동 표기·시험 실패 시 요약 갱신)은 docs/runtime/dependency-lock.md에 보류 기록. fast 수치에는 영향 없음.


### 2026-11-03 | E02 실제 인프라·모의 AI·API 실행 완료

- Ubuntu 전용 실행 도구 local_runtime.py와 Windows→WSL 전달 local.ps1, 전용 Compose sentinel-portfolio, local 프로필과 SMTP no-op, 로컬 파일 저장소 프로필을 구성했다. 기존 PC 서비스/원본 checkout은 변경하지 않았다.
- MySQL 8.0.46/Redis 7.0.15/Apache Kafka 3.7.0/Python 3.12.16 이미지를 digest로 잠갔다. 비밀 파일은 Ubuntu private state에만 생성했다.
- realInfraTest 19/19: 신규 4개 MySQL V1~V27/Redis TTL/Kafka send-consume/HTTP PDF+SMTP 미사용, 기존 인증 15개 H2+별도 Redis Testcontainer. fast는 384 통과·기존 15 skip·실패/error 0. Python 도구 11/11, mock 서버 4/4.
- 정상 및 Kafka 중단 상태 API 기동/정상 종료, wrapper up/verify/start-app/stop-app/down과 재기동을 검증했다. 컨테이너 재생성 후 MySQL 마이그레이션 이력 27행/Redis marker/mock journal 보존을 확인했다.
- 독립 검토 Important 2건(상위 환경변수 격리, 삭제 소스 동기화)을 관련 실패 시험으로 재현한 후 수정했다. 경계 밖 경로 삭제 거부·build 보존·최적화 Python 검증도 회귀 시험으로 확인했다. E02 새 보류 Minor 없음.
- 최신 실행/시험 범위는 docs/runtime/local-environment.md, 원자료는 Git 제외 perf/results/20261103-e02-runtime/manifest.json에 보관했다. 현재 API/의존 서비스 UP. 커밋·push 미수행.
- 다음은 E03 CDC probe/발행기 선택이며 아직 수행하지 않았다. 실제 자료 적재/맞춤 보고서 업무 API/Outbox/Inbox/업무 장애 복구는 후속 범위다. E01의 전체 검증 보고 Minor 2건은 별도 보류로 유지한다.


### 2026-11-03 | E03 CDC 가능성 검증·선택 완료

- 전용 sentinel_cdc_probe DB/Outbox, reader/writer 제한 계정, MySQL ROW/FULL/server-id223344/10일 binlog 보존, cdc profile worker를 구성했다. Debezium3.4.3.Final digest를 고정했고 Connect4.1.1/Kafka3.7.0/MySQL8.0.46 조합을 실제 검증했다.
- 최종 probe 20261103-cdc-04: CDC 통합3/3, 초기snapshot1+정상commit10+worker 중단commit5+broker 중단commit5=21개 고유 ID 수신, rollback10개 미전달, DB21행 일치. worker/broker 컨테이너 교체 후 checkpoint 재개·history hash 보존·snapshot 미반복을 확인했다. 추가 중복은 해당 유한 실행0건이며 업무 exactly-once를 의미하지 않는다.
- 독립 검토 Important2(잠금 충돌 시 결정 불일치, 부분 정지 실패 시 복구 누락)을 실패 회귀로 재현한 후 수정했다. guard 단위2/2 및 실제 정지 직후 예외2건 주입 후 양쪽 구성 요소 복구를 확인했다.
- Python17/17, mock4/4, 기존 realInfra19/19 통과. 이번 fastTest는 guard2개 선택 실행이며 전체 fast 회귀를 새로 수행한 것은 아니다. 인증은 실제 로그의 H2 확인, 신규4개는 MySQL/Redis/Kafka/HTTP 구성을 유지했다.
- 초기 cnf777 무시·등록 직후 status404·offset 좌표 비교 오류와 앱 실행 환경을 잘못 주입한 회귀 runner 실패를 보존하고 원인별로 수정했다. 시험 실패를 제외하거나 네트워크 오류만으로 폴링을 고르지 않았다.
- ADR docs/adr/0001-outbox-relay.md, 원자료 perf/results/20261103-e03-cdc/manifest.json 및 perf/results/20261103-cdc-04/. 선택은 Ubuntu relay-mode.json에도 cdc로 기록했다. 현재 API/기본 구성 요소/Connect UP이며 probe connector는 시험 종료 시 제거했다.
- 다음 D01 실제 자료 검증은 미수행. 업무 EventRouter/Outbox/Inbox와 강제 종료·전원 차단·다중 broker·binlog 만료 복구는 후속 범위다. E01 보고 Minor2건 유지. 기존 원본 checkout 보존, 커밋·push 미수행.
