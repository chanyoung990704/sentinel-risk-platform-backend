package com.aivle.project.reliability;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.service.ReportRecoveryService;
import com.aivle.project.messaging.CustomReportTopics;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@Tag("reliability") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.worker.enabled=false","custom-report.messaging.enabled=false","custom-report.admission.user-limit=200","decorator.datasource.p6spy.enable-logging=false"})
class ReportDeliveryRecoveryTest extends ReportFlowIntegrationSupport {
	@Autowired PasswordEncoder passwords;@Autowired ReportRecoveryService recovery;
	final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	final String group="sentinel-delivery-test-"+UUID.randomUUID(),nonce=UUID.randomUUID().toString(),password=UUID.randomUUID().toString()+"aA1!";
	Path root,ready;Process child;int port;String token;UUID ackTarget;boolean brokerStopped,connectStopped,ackBoundary,artifactBoundary,mysqlStopped;AdminClient admin;
	@BeforeEach void setupDelivery()throws Exception {
		root=Files.createTempDirectory("sentinel-delivery-owned-");ready=root.resolve("ready.json");admin=AdminClient.create(kafka());anchorGroup();
		for(long user:users){jdbc.update("UPDATE users SET password=?,password_changed_at=NOW() WHERE id=?",passwords.encode(password),user);jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_USER'",user);}
		jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_ADMIN'",users.getLast());mock(0,0);
	}
	@AfterEach void cleanupDelivery()throws Exception {
		try{stop(false);}finally{
			try{if(mysqlStopped){control("mysql-start");mysqlStopped=false;}if(brokerStopped){control("broker-start");brokerStopped=false;}if(connectStopped){control("up");connectStopped=false;}mock(0,0);}
			finally{if(admin!=null)admin.close();for(long user:users){jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",user);jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);}
				if(root!=null){if(Files.isSymbolicLink(root)||!root.toAbsolutePath().normalize().startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))throw new IllegalStateException("소유 시험 경로 확인 실패");try(var walk=Files.walk(root)){for(var path:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}}
			}
		}
	}
	@Test void hundredUniqueSameKeyAndDuplicateEventsFinishWithOneBusinessResultEach()throws Exception {
		// given
		start();login();mock(100,0);long started=System.nanoTime();List<UUID> ids=postMany(100,false);assertThat(new HashSet<>(ids)).hasSize(100);
		// when
		awaitSuccess(ids);List<UUID> repeated=postMany(100,true);assertThat(new HashSet<>(repeated)).hasSize(1);UUID single=repeated.getFirst();awaitSuccess(List.of(single));
		String body=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,single.toString());String event=mapper.readTree(body).path("eventId").asText();RecordMetadata last=null;
		try(var producer=new KafkaProducer<byte[],byte[]>(producerConfig())){for(int i=0;i<100;i++){var record=new ProducerRecord<byte[],byte[]>(CustomReportTopics.REQUEST,single.toString().getBytes(StandardCharsets.UTF_8),body.getBytes(StandardCharsets.UTF_8));record.headers().add("id",event.getBytes(StandardCharsets.UTF_8));last=producer.send(record).get(15,TimeUnit.SECONDS);}}
		var finalPosition=last;await(()->committed(finalPosition)>=finalPosition.offset()+1,60,"중복 event offset");
		// then
		var all=new ArrayList<>(ids);all.add(single);assertReconciled(all);assertThat(countFor("inbox_events",single)).isEqualTo(1);assertThat(countFor("report_jobs",single)).isEqualTo(1);assertThat(countFor("report_artifacts",single)).isEqualTo(1);assertThat(calls(single)).isEqualTo(1);
		assertPdf(single);assertThat(get("/actuator/health/apiReady").statusCode()).isEqualTo(200);assertThat(get("/actuator/health/workerReady").statusCode()).isEqualTo(200);
		evidence("hundred-and-duplicates",all,Map.of("uniqueHttp",100,"sameKeyHttp",100,"duplicateWire",100,"lastPartition",last.partition(),"lastOffset",last.offset(),"elapsedMs",elapsed(started)));
	}
	@Test void appStartsAndAcceptsWhileBrokerAndConnectorAreDownThenDrains()throws Exception {
		// given
		connectStopped=true;control("connect-stop");brokerStopped=true;control("broker-stop");
		try{
			// when
			start();login();UUID id=post("offline-"+UUID.randomUUID());assertThat(status(id)).isEqualTo("ACCEPTED");assertThat(countFor("report_jobs",id)).isZero();assertThat(get("/actuator/health/apiReady").statusCode()).isEqualTo(200);long started=System.nanoTime();
			// then
			control("broker-start");brokerStopped=false;control("up");connectStopped=false;awaitSuccess(List.of(id));assertReconciled(List.of(id));assertPdf(id);evidence("offline-startup",List.of(id),Map.of("recoveryMs",elapsed(started)));
		}finally{if(brokerStopped){control("broker-start");brokerStopped=false;}if(connectStopped){control("up");connectStopped=false;}}
	}
	@Test void killedGenerationProcessIsRecoveredByLeaseWithoutDuplicateArtifact()throws Exception {
		// given
		start();login();holdResponse(true);UUID id=post("kill-"+UUID.randomUUID());long interruptedCall=awaitInflight(id);assertThat(status(id)).isEqualTo("PROCESSING");acceptedCheckpoint("process-kill",List.of(id));
		// when: 이 시험이 만든 JVM만 강제 종료한다.
		long killedPid=child.pid();stop(true);holdResponse(false);long started=System.nanoTime();start();login();awaitSuccess(List.of(id));
		// then
		assertThat(jdbc.queryForObject("SELECT attempt_no FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(2);assertReconciled(List.of(id));assertPdf(id);assertThat(calls(id)).isEqualTo(2);assertThat(journalEvidence(List.of(id))).anySatisfy(row->{assertThat(((Number)row.get("sequence")).longValue()).isEqualTo(interruptedCall);assertThat(((Number)row.get("status")).intValue()).isEqualTo(200);});evidence("process-kill",List.of(id),Map.of("killedPid",killedPid,"interruptedCallSequence",interruptedCall,"responseHeldAtKill",true,"recoveryMs",elapsed(started),"externalCalls",calls(id)));
	}
	@Test void exhaustedHttpFailuresRequireOneAuditedRecoveryAndKeepAttemptHistory()throws Exception {
		// given
		start();login();mock(0,3);UUID id=post("recover-"+UUID.randomUUID());await(()->status(id).equals("FAILED"),180,"3회 예산 소진");assertThat(countFor("report_artifacts",id)).isZero();
		// when
		mock(0,0);UUID command=UUID.randomUUID();long started=System.nanoTime();assertThat(recovery.recover(users.getLast(),id,command,"모의 서버 장애 제거").generation()).isEqualTo(2);assertThat(recovery.recover(users.getLast(),id,command,"모의 서버 장애 제거").generation()).isEqualTo(2);awaitSuccess(List.of(id));
		// then
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id=? AND a.generation=1",Integer.class,id.toString())).isEqualTo(3);assertThat(countFor("report_recovery_commands",id)).isEqualTo(1);assertReconciled(List.of(id));assertPdf(id);evidence("manual-recovery",List.of(id),Map.of("commandId",command,"generation",2,"externalCalls",calls(id),"recoveryMs",elapsed(started)));
	}
	@Test void remoteProcessingWithLostResponseRetriesWithoutDuplicateBusinessResult()throws Exception {
		// given: 실제 모의 서버의 첫 처리를 완료한 뒤 소켓 응답만 유실한다.
		start();login();customControl(Map.of("drop_first",1));long started=System.nanoTime();var ids=postMany(100,false);acceptedCheckpoint("response-loss",ids);
		// when
		awaitSuccess(ids);
		// then: 외부 호출과 DB 효과의 보장 범위를 구분한다.
		assertReconciled(ids);long external=0,attempts=0;for(UUID id:ids){external+=calls(id);attempts+=jdbc.queryForObject("SELECT COUNT(*) FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id=?",Long.class,id.toString());}assertThat(external).isEqualTo(101);assertThat(attempts).isEqualTo(101);
		evidence("response-loss",ids,Map.of("externalCalls",external,"databaseAttempts",attempts,"databaseArtifacts",100,"recoveryMs",elapsed(started)));
	}
	@Test void inboxCommitThenActualJvmHaltBeforeAckRedeliversSameOffset()throws Exception {
		// given: 소비 JVM의 Inbox commit 이후/실제 ACK 직전 경계에서 종료한다.
		UUID id=requests.accept(users.getFirst(),company,"ack-halt-"+UUID.randomUUID(),new com.aivle.project.company.reportrequest.model.CustomReportCommand(null,"ACK commit 경계")).requestId();acceptedCheckpoint("ack-process-halt",List.of(id));ackBoundary=true;ackTarget=id;start();
		// when
		assertThat(child.waitFor(60,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(95);child=null;
		assertThat(countFor("report_jobs",id)).isEqualTo(1);var gate=mapper.readTree(Files.readString(root.resolve("ack-gate.json")));
		var partition=new TopicPartition(CustomReportTopics.REQUEST,gate.path("partition").asInt());long offset=gate.path("offset").asLong();
		java.util.function.LongSupplier committedOffset=()->{try{var value=admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS).get(partition);return value==null?0:value.offset();}catch(Exception failure){throw new IllegalStateException("ACK 위치 조회 실패",failure);}};
		java.util.function.LongSupplier receipts=()->{try{return Files.readAllLines(root.resolve("ack-received.jsonl")).stream().map(line->{try{return mapper.readTree(line);}catch(Exception failure){throw new IllegalStateException(failure);}}).filter(row->row.path("key").asText().equals(id.toString())&&row.path("partition").asInt()==partition.partition()&&row.path("offset").asLong()==offset).count();}catch(Exception failure){throw new IllegalStateException("재전달 관측 실패",failure);}};
		long committedAtKill=committedOffset.getAsLong();assertThat(committedAtKill).isLessThanOrEqualTo(offset);
		long restarted=System.nanoTime();start();login();long initialReceipts=receipts.getAsLong();long initialCommitted=committedOffset.getAsLong();
		Files.writeString(output().resolve(label()+"-ack-restart-initial.json"),mapper.writeValueAsString(Map.of("requestId",id,"receipts",initialReceipts,"committedOffset",initialCommitted,"targetOffset",offset,"jobStatus",status(id))),StandardOpenOption.CREATE_NEW);
		// worker 완료는 Kafka 재할당/재수신보다 먼저일 수 있다. 같은 180초 예산 안에서 둘 다 관측한다.
		int remaining=180-(int)TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-restarted);assertThat(remaining).isPositive();
		await(()->status(id).equals("SUCCEEDED")&&receipts.getAsLong()>=2&&committedOffset.getAsLong()>offset,remaining,"동일 offset 재전달+ACK+업무 완료");
		// then: 동일 partition/offset 수신이 최소 두 번이어도 job/artifact는 하나다.
		long received=receipts.getAsLong();assertReconciled(List.of(id));assertPdf(id);
		evidence("ack-process-halt",List.of(id),Map.of("sameOffsetReceipts",received,"partition",partition.partition(),"offset",offset,"committedAtKill",committedAtKill,"committedAfter",committedOffset.getAsLong(),"initialReceipts",initialReceipts,"initialCommittedOffset",initialCommitted,"recoveryMs",elapsed(restarted)));
	}
	@Test void connectorOnlyOutagePreservesHundredAcceptedAcrossApiRestart()throws Exception {outageHundred(false);}
	@Test void brokerOnlyOutagePreservesHundredAcceptedAcrossApiRestart()throws Exception {outageHundred(true);}
	void outageHundred(boolean broker)throws Exception {
		// given: 해당 인프라 하나만 중단하며 최초 100개 accepted ID를 장애 중 고정한다.
		start();login();long faultAt=System.nanoTime();if(broker){brokerStopped=true;control("broker-stop");}else{connectStopped=true;control("connect-stop");}
		var ids=postMany(100,false);assertThat(new HashSet<>(ids)).hasSize(100);acceptedCheckpoint(broker?"broker-only-hundred":"relay-only-hundred",ids);assertThat(get("/actuator/health/apiReady").statusCode()).isEqualTo(200);
		// when: 중단을 최소 10초 유지하고 API 자체도 재기동한다.
		Thread.sleep(10000);stop(false);start();login();long resumed=System.nanoTime();
		if(broker){control("broker-start");brokerStopped=false;}else{control("up");connectStopped=false;}
		var repair=new ProcessBuilder("python3","scripts/reliability/configure_cdc.py","recover").redirectErrorStream(true).redirectOutput(output().resolve(label()+"-matrix-cdc-"+UUID.randomUUID()+".log").toFile()).start();assertThat(repair.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(repair.exitValue()).isZero();
		// then: connector RUNNING과 별개로 실제 accepted 전체의 업무 완료를 요구한다.
		awaitSuccess(ids);assertReconciled(ids);evidence(broker?"broker-only-hundred":"relay-only-hundred",ids,Map.of("faultWindowMs",elapsed(faultAt),"recoveryMs",elapsed(resumed),"minimumDowntimeSeconds",10));
	}
	@Test void actualJvmHaltAfterFileWriteRollsBackDatabaseThenLeaseRecoveryCompletes()throws Exception {
		// given: 파일 기록 뒤 최종 Outbox INSERT 직후/commit 전 JVM을 종료한다.
		artifactBoundary=true;start();login();UUID id=post("artifact-halt-"+UUID.randomUUID());assertThat(child.waitFor(90,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(96);child=null;
		assertThat(countFor("report_artifacts",id)).isZero();long orphanCount;try(var paths=Files.walk(root.resolve("uploads"))){orphanCount=paths.filter(Files::isRegularFile).count();}assertThat(orphanCount).isEqualTo(1);
		// when
		artifactBoundary=false;start();login();awaitSuccess(List.of(id));
		// then: 새 attempt만 DB 효과를 확정하고 옛 파일은 별도 고아로 식별된다.
		assertReconciled(List.of(id),1);assertPdf(id);long files;try(var paths=Files.walk(root.resolve("uploads"))){files=paths.filter(Files::isRegularFile).count();}assertThat(files).isEqualTo(2);
		evidence("artifact-process-halt",List.of(id),Map.of("filesBefore",orphanCount,"filesAfter",files,"unreferencedFiles",1,"databaseArtifacts",1,"cleanupProof","ReportArtifactIntegrationTest.cleanupPreservesReferenceAndLiveLeaseButDeletesExpiredOrphan"));
	}
	@Test void actualHttpDeniesOtherOwnerForStatusFileAndLegacyRoutes()throws Exception {
		// given: 실제 JWT 로그인/본인 보고서 완료와 파일 다운로드.
		start();login();UUID id=post("owner-http-"+UUID.randomUUID());awaitSuccess(List.of(id));assertPdf(id);
		long file=jdbc.queryForObject("SELECT file_id FROM report_artifacts WHERE request_id=?",Long.class,id.toString());
		String other=jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.getLast());
		var login=http.send(HttpRequest.newBuilder(uri("/api/auth/login")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("email",other,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(login.statusCode()).isEqualTo(200);token=mapper.readTree(login.body()).path("data").path("accessToken").asText();
		// when / then: 다른 소유자에게 상태/파일/일반 파일 경로를 숨긴다.
		var codes=new LinkedHashMap<String,Integer>();for(String path:List.of("/api/report-requests/"+id,"/api/report-requests/"+id+"/file","/api/files/"+file,"/api/files/"+file+"/download-url","/api/reports/files/"+file,"/api/reports/files/"+file+"/url")){
			var response=get(path);assertThat(response.statusCode()).isEqualTo(404);assertThat(new String(response.body(),StandardCharsets.UTF_8)).doesNotContain("%PDF-","storageKey","additionalInstructions");codes.put(path,response.statusCode());
		}
		evidence("http-owner-denial",List.of(id),Map.of("otherUserHttp",codes,"fileId",file));
	}
	@Test void databaseOutageKeepsLivenessButRejectsAdmissionAndRecoversReadiness()throws Exception {
		// given: 인증 Redis는 정상이며 실제 MySQL만 중단한다. 시험 pool 접속 제한은 2초다.
		start();login();mysqlStopped=true;control("mysql-stop");int alive,readyCode,postCode;
		try {
			// when
			alive=get("/actuator/health/liveness").statusCode();readyCode=get("/actuator/health/apiReady").statusCode();
			var response=http.send(HttpRequest.newBuilder(uri("/api/companies/"+company+"/custom-report-requests")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Idempotency-Key","db-down-"+UUID.randomUUID()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"additionalInstructions\":\"DB 장애 접수 검증\"}")).build(),HttpResponse.BodyHandlers.discarding());postCode=response.statusCode();
			// then
			assertThat(alive).isEqualTo(200);assertThat(readyCode).isEqualTo(503);assertThat(postCode).isBetween(500,599);
		}finally{control("mysql-start");mysqlStopped=false;}
		assertThat(get("/actuator/health/apiReady").statusCode()).isEqualTo(200);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=?",Integer.class,users.getFirst())).isZero();
		Files.writeString(output().resolve(label()+"-database-readiness.json"),mapper.writeValueAsString(Map.of("livenessHttp",alive,"apiReadyHttp",readyCode,"admissionHttp",postCode,"accepted",0,"hikariConnectionTimeoutMs",2000)),StandardOpenOption.CREATE_NEW);
		var repair=new ProcessBuilder("python3","scripts/reliability/configure_cdc.py","recover").redirectErrorStream(true).redirectOutput(output().resolve(label()+"-database-cdc-repair.log").toFile()).start();assertThat(repair.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(repair.exitValue()).isZero();
	}
	@Test void permanentInvalidPdfFailsOnceAndReturnsAdmissionSlot()throws Exception {
		// given
		start();login();customControl(Map.of("response_mode","not_pdf"));UUID id=post("bad-pdf-"+UUID.randomUUID());
		// when
		await(()->status(id).equals("FAILED"),180,"영구 PDF 실패 종결");
		// then
		assertThat(calls(id)).isEqualTo(1);assertThat(countFor("report_artifacts",id)).isZero();assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isFalse();
		evidence("permanent-pdf",List.of(id),Map.of("expectedFailed",1,"externalCalls",1));
	}
	@Test void temporaryHttpFailuresRecoverWithinThreeAttemptBudget()throws Exception {
		// given
		start();login();mock(0,2);UUID id=post("temporary-"+UUID.randomUUID());
		// when
		awaitSuccess(List.of(id));
		// then
		assertReconciled(List.of(id));assertThat(calls(id)).isEqualTo(3);evidence("temporary-http",List.of(id),Map.of("externalCalls",3,"expectedFailed",0));
	}
	void customControl(Map<String,Object> config)throws Exception {
		var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(config))).build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).isEqualTo(200);
	}
	void acceptedCheckpoint(String name,List<UUID> ids)throws Exception {
		assertThat(new HashSet<>(ids)).hasSize(ids.size());for(UUID id:ids)assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE id=?",Integer.class,id.toString())).isEqualTo(1);
		Files.writeString(output().resolve(label()+"-"+name+"-accepted.json"),mapper.writeValueAsString(Map.of("case",name,"requestIds",ids,"accepted",ids.size(),"dbCommittedConfirmed",true,"recordedEpochMs",System.currentTimeMillis())),StandardOpenOption.CREATE_NEW);
	}
	void start()throws Exception {
		Files.deleteIfExists(ready);String classpath=Files.readString(Path.of("build/portfolio-report-classpath.txt")).strip();var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xms64m","-Xmx384m","-Dspring.devtools.restart.enabled=false","--class-path",classpath,"src/test/java/com/aivle/project/reliability/ReportDeliveryApplicationProcess.java",ready.toString(),group,root.resolve("uploads").toString(),nonce));var builder=new ProcessBuilder(command);var env=builder.environment();env.clear();for(String name:List.of("PATH","HOME","USER","LANG","JAVA_HOME","SENTINEL_RUNTIME_NAMESPACE"))if(System.getenv(name)!=null)env.put(name,System.getenv(name));env.put("PORTFOLIO_SECRET_DIR",System.getenv().getOrDefault("PORTFOLIO_SECRET_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio/secrets").toString()));env.put("PORTFOLIO_DATA_DIR",System.getenv().getOrDefault("PORTFOLIO_DATA_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio").toString()));
		if(ackBoundary){env.put("PORTFOLIO_TEST_ACK_GATE",root.resolve("ack-gate.json").toString());env.put("PORTFOLIO_TEST_ACK_TARGET",ackTarget.toString());}
		if(artifactBoundary)env.put("PORTFOLIO_TEST_ARTIFACT_HALT","true");
		child=builder.redirectErrorStream(true).redirectOutput(output().resolve(label()+"-app-"+UUID.randomUUID()+".log").toFile()).start();await(()->{if(!child.isAlive()){if(ackBoundary&&child.exitValue()==95&&Files.exists(ready))return true;throw new IllegalStateException("소유 시험 앱 종료; 전용 로그 확인 필요");}return Files.exists(ready);},90,"소유 앱 시작");var identity=mapper.readTree(Files.readString(ready));assertThat(identity.path("pid").asLong()).isEqualTo(child.pid());assertThat(identity.path("nonce").asText()).isEqualTo(nonce);port=identity.path("port").asInt();
	}
	void stop(boolean force)throws Exception {if(child==null)return;if(child.isAlive()){if(force)child.destroyForcibly();else child.destroy();if(!child.waitFor(35,TimeUnit.SECONDS)){child.destroyForcibly();assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();}}child=null;}
	void login()throws Exception {String email=jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.getFirst());var response=http.send(HttpRequest.newBuilder(uri("/api/auth/login")).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("email",email,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as("실제 로그인").isEqualTo(200);token=mapper.readTree(response.body()).path("data").path("accessToken").asText();assertThat(token).isNotBlank();}
	UUID post(String key)throws Exception {var response=http.send(HttpRequest.newBuilder(uri("/api/companies/"+company+"/custom-report-requests")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Idempotency-Key",key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"additionalInstructions\":\"통합 복구 시험\"}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as("요청 접수 HTTP").isEqualTo(202);return UUID.fromString(mapper.readTree(response.body()).path("data").path("requestId").asText());}
	List<UUID> postMany(int count,boolean same)throws Exception {String key="batch-"+UUID.randomUUID();var pool=Executors.newFixedThreadPool(10);try{var work=new ArrayList<Future<UUID>>();for(int i=0;i<count;i++){String selected=same?key:key+"-"+i;work.add(pool.submit(()->post(selected)));}var ids=new ArrayList<UUID>();for(var item:work)ids.add(item.get(45,TimeUnit.SECONDS));return ids;}finally{pool.shutdown();assertThat(pool.awaitTermination(45,TimeUnit.SECONDS)).isTrue();}}
	HttpResponse<byte[]> get(String path)throws Exception {var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10));if(token!=null)builder.header("Authorization","Bearer "+token);return http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
	URI uri(String path){return URI.create("http://127.0.0.1:"+port+path);}
	String status(UUID id){return jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString());}
	long countFor(String table,UUID id){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE request_id=?",Long.class,id.toString());}
	void awaitSuccess(List<UUID> ids)throws Exception {await(()->ids.stream().allMatch(id->status(id).equals("SUCCEEDED")),180,"보고서 완료");}
	com.aivle.project.company.reportjob.service.ReportReconciliationService.Result reconcile(List<UUID> ids){return new com.aivle.project.company.reportjob.service.ReportReconciliationService(jdbc,root.resolve("uploads").toString()).reconcile(ids);}
	void assertReconciled(List<UUID> ids){assertReconciled(ids,0);}
	void assertReconciled(List<UUID> ids,int expectedOrphans){for(UUID id:ids){assertThat(status(id)).isEqualTo("SUCCEEDED");assertThat(countFor("report_jobs",id)).isEqualTo(1);assertThat(countFor("report_artifacts",id)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isFalse();}var result=reconcile(ids);assertThat(result.accepted()).isEqualTo(ids.size());assertThat(result.succeeded()).isEqualTo(ids.size());assertThat(result.missingRequests()+result.missingJobs()+result.duplicateArtifacts()+result.missingArtifacts()+result.missingFiles()+result.invalidArtifacts()).isZero();assertThat(result.unlinkedFiles()).isEqualTo(expectedOrphans);assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isZero();}
	void assertPdf(UUID id)throws Exception {var state=get("/api/report-requests/"+id);assertThat(state.statusCode()).isEqualTo(200);assertThat(mapper.readTree(state.body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");var response=get("/api/report-requests/"+id+"/file");assertThat(response.statusCode()).isEqualTo(200);assertThat(new String(response.body(),0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");String sha=com.aivle.project.dataimport.service.ImportPreflightService.sha256(response.body());assertThat(jdbc.queryForObject("SELECT sha256 FROM report_artifacts WHERE request_id=?",String.class,id.toString())).isEqualTo(sha);}
	long calls(UUID id)throws Exception {var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/journal")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());long count=0;for(var row:mapper.readTree(response.body()))if(row.path("requestId").asText().equals(id.toString()))count++;return count;}
	void mock(int delay,int failures)throws Exception {var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("delay_ms",delay,"fail_first",failures,"response_mode","valid")))).build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).isEqualTo(200);}
	void holdResponse(boolean hold)throws Exception {var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("hold_response",hold)))).build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).as("모의 응답 보류 제어").isEqualTo(200);}
	long awaitInflight(UUID id)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/inflight/custom-reports")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);for(var call:mapper.readTree(response.body()))if(call.path("requestId").asText().equals(id.toString()))return call.path("sequence").asLong();Thread.sleep(50);}throw new AssertionError("실제 모의 HTTP 요청 진입 관찰 한도 초과");}
	Map<String,Object> kafka(){return Map.of("bootstrap.servers","127.0.0.1:19092","default.api.timeout.ms",10000,"request.timeout.ms",5000);}
	Properties producerConfig(){var p=new Properties();p.putAll(kafka());p.put("key.serializer",ByteArraySerializer.class.getName());p.put("value.serializer",ByteArraySerializer.class.getName());return p;}
	void anchorGroup()throws Exception {var spec=new HashMap<TopicPartition,OffsetSpec>();for(int i=0;i<3;i++)spec.put(new TopicPartition(CustomReportTopics.REQUEST,i),OffsetSpec.latest());var ends=admin.listOffsets(spec).all().get(10,TimeUnit.SECONDS);var offsets=new HashMap<TopicPartition,OffsetAndMetadata>();ends.forEach((p,o)->offsets.put(p,new OffsetAndMetadata(o.offset())));admin.alterConsumerGroupOffsets(group,offsets).all().get(10,TimeUnit.SECONDS);}
	long committed(RecordMetadata record){try{var offsets=admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);var offset=offsets.get(new TopicPartition(record.topic(),record.partition()));return offset==null?0:offset.offset();}catch(Exception failure){throw new IllegalStateException("offset 조회 실패");}}
	void control(String action)throws Exception {var process=new ProcessBuilder("python3","scripts/reliability/cdc_runtime.py",action).redirectErrorStream(true).redirectOutput(output().resolve(label()+"-"+action+"-"+UUID.randomUUID()+".log").toFile()).start();try{assertThat(process.waitFor(150,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}}
	void await(BooleanSupplier condition,int seconds,String name)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}throw new AssertionError(name+" 관찰 한도 "+seconds+"초 초과");}
	long elapsed(long started){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);}
	String label(){return System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual");}
	Path output()throws Exception {Path path=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/delivery"));Files.createDirectories(path);return path;}
	List<Map<String,Object>> wireEvidence(List<UUID> ids)throws Exception {
		var expected=new HashMap<String,String>();for(UUID id:ids)expected.put(jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString()),id.toString());
		var config=new Properties();config.putAll(kafka());config.put("group.id","sentinel-delivery-observer-"+UUID.randomUUID());config.put("enable.auto.commit","false");config.put("auto.offset.reset","earliest");config.put("key.deserializer",org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());config.put("value.deserializer",org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());var observed=new LinkedHashMap<String,Map<String,Object>>();
		try(var consumer=new org.apache.kafka.clients.consumer.KafkaConsumer<byte[],byte[]>(config)){
			consumer.subscribe(List.of(CustomReportTopics.REQUEST));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
			while(observed.size()<expected.size()&&System.nanoTime()<end)for(var record:consumer.poll(Duration.ofMillis(200)))if(record.value()!=null){
				com.fasterxml.jackson.databind.JsonNode body;try{body=mapper.readTree(record.value());}catch(com.fasterxml.jackson.core.JsonProcessingException unrelated){continue;}
				String event=body.path("eventId").asText();if(!expected.containsKey(event))continue;String request=expected.get(event);assertThat(new String(record.key(),StandardCharsets.UTF_8)).isEqualTo(request);assertThat(new String(record.headers().lastHeader("id").value(),StandardCharsets.UTF_8)).isEqualTo(event);assertThat(body.path("requestId").asText()).isEqualTo(request);
				observed.putIfAbsent(event,Map.of("requestId",request,"eventId",event,"topic",record.topic(),"partition",record.partition(),"offset",record.offset(),"keyAndHeaderMatch",true));
			}
		}assertThat(observed.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());return List.copyOf(observed.values());
	}
	List<Map<String,Object>> journalEvidence(List<UUID> ids)throws Exception {
		var expected=new HashMap<String,Map<String,Object>>();var checksums=new HashMap<String,String>();for(UUID id:ids){var row=jdbc.queryForMap("SELECT snapshot_id,company_id,target_quarter_key,input_hash FROM report_requests WHERE id=?",id.toString());expected.put(id.toString(),row);String source=row.get("snapshot_id")+":"+row.get("company_id")+":"+row.get("target_quarter_key");checksums.computeIfAbsent(source,key->sources.load(UUID.fromString(row.get("snapshot_id").toString()),((Number)row.get("company_id")).longValue(),((Number)row.get("target_quarter_key")).intValue()).checksum());}
		var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/journal")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());var selected=new ArrayList<Map<String,Object>>();for(var row:mapper.readTree(response.body())){var fixed=expected.get(row.path("requestId").asText());if(fixed==null)continue;assertThat(row.path("sourceSnapshot").asText()).isEqualTo(fixed.get("snapshot_id"));assertThat(row.path("inputHash").asText()).isEqualTo(fixed.get("input_hash"));String source=fixed.get("snapshot_id")+":"+fixed.get("company_id")+":"+fixed.get("target_quarter_key");assertThat(row.path("sourceChecksum").asText()).isEqualTo(checksums.get(source));selected.add(mapper.convertValue(row,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));}
		for(UUID id:ids)assertThat(selected.stream().anyMatch(row->id.toString().equals(row.get("requestId")))).isTrue();return selected;
	}
	void evidence(String name,List<UUID> ids,Map<String,Object> details)throws Exception {var result=new LinkedHashMap<String,Object>(details);result.put("case",name);result.put("requestIds",ids);result.put("reconciliation",reconcile(ids));result.put("wire",wireEvidence(ids));result.put("mockJournal",journalEvidence(ids));result.put("observationLimitSeconds",180);result.put("group",group);Files.writeString(output().resolve(label()+"-"+name+"-"+UUID.randomUUID()+".json"),mapper.writeValueAsString(result),StandardOpenOption.CREATE_NEW);}
}
