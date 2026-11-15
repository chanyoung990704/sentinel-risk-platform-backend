package com.aivle.project.reporting.internal.messaging;
import com.aivle.project.reporting.api.CustomReportTopics;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.messaging.consumer.CustomReportKafkaConsumer;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.aivle.project.reporting.internal.messaging.quarantine.QuarantineReplayService;
import com.aivle.project.finance.internal.dataimport.service.ImportPreflightService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.messaging.enabled=true","custom-report.messaging.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportQuarantineIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired CustomReportKafkaConsumer consumer;@Autowired QuarantineReplayService replay;@MockitoSpyBean OutboxWriter outbox;
	final Set<Long> offsets=new HashSet<>();final long seed=ThreadLocalRandom.current().nextLong(1_000_000_000L,Long.MAX_VALUE-100);
	@BeforeEach void admin(){jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_ADMIN'",users.getLast());}
	@AfterEach void cleanupQuarantines()throws Exception {
		for(long offset:offsets)for(String id:jdbc.queryForList("SELECT id FROM quarantined_messages WHERE source_topic=? AND source_partition=0 AND source_offset=?",String.class,CustomReportTopics.REQUEST,offset)){
			System.out.println("QUARANTINE_EVIDENCE "+mapper.writeValueAsString(jdbc.queryForMap("SELECT id,source_topic,source_partition,source_offset,body_size,body_sha256,body_truncated,reason_code,dlt_event_id,replay_event_id FROM quarantined_messages WHERE id=?",id)));
			jdbc.update("DELETE FROM quarantine_replay_commands WHERE quarantine_id=?",id);jdbc.update("DELETE FROM outbox_events WHERE aggregateid=?",id);jdbc.update("DELETE FROM quarantined_messages WHERE id=?",id);
		}for(long user:users)jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);
	}
	UUID accepted(){return requests.accept(users.getFirst(),company,"quarantine-"+UUID.randomUUID(),new CustomReportCommand(null,"격리 시험 입력")).requestId();}
	ReportEvent event(UUID request)throws Exception{return mapper.readValue(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,request.toString()),ReportEvent.class);}
	ConsumerRecord<byte[],byte[]> record(UUID request,byte[] body,long offset,UUID header){offsets.add(offset);var r=new ConsumerRecord<byte[],byte[]>(CustomReportTopics.REQUEST,0,offset,request.toString().getBytes(StandardCharsets.UTF_8),body);if(header!=null)r.headers().add("id",header.toString().getBytes(StandardCharsets.UTF_8));return r;}
	UUID quarantine(long offset){return UUID.fromString(jdbc.queryForObject("SELECT id FROM quarantined_messages WHERE source_topic=? AND source_partition=0 AND source_offset=?",String.class,CustomReportTopics.REQUEST,offset));}
	@Test void invalidUtf8JsonSchemaHashKeyAndHeaderAreDurableWithoutFailingClaimedRequest()throws Exception {
		// given
		UUID id=accepted();var original=event(id);var bodies=new ArrayList<byte[]>();bodies.add(new byte[]{(byte)0xc3,0x28});bodies.add("{비공개원문".getBytes(StandardCharsets.UTF_8));var schema=(ObjectNode)mapper.valueToTree(original);schema.put("schemaVersion",99);bodies.add(mapper.writeValueAsBytes(schema));var hash=(ObjectNode)mapper.valueToTree(original);((ObjectNode)hash.get("payload")).put("inputHash","0".repeat(64));bodies.add(mapper.writeValueAsBytes(hash));bodies.add(mapper.writeValueAsBytes(original));bodies.add(mapper.writeValueAsBytes(original));bodies.add(new byte[70000]);
		for(int i=0;i<bodies.size();i++){
			var ack=mock(Acknowledgment.class);byte[] body=bodies.get(i);var r=record(i==4?UUID.randomUUID():id,body,seed+i,i==5?UUID.randomUUID():original.eventId());
			// when
			consumer.onRequest(r,ack);
			// then
			verify(ack).acknowledge();UUID q=quarantine(seed+i);var stored=jdbc.queryForMap("SELECT * FROM quarantined_messages WHERE id=?",q.toString());assertThat(stored.get("body_sha256")).isEqualTo(ImportPreflightService.sha256(body));assertThat((byte[])stored.get("body_prefix")).containsExactly(Arrays.copyOf(body,Math.min(body.length,65536)));assertThat(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=?",String.class,q.toString())).doesNotContain("비공개원문","additionalInstructions");
		}assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString())).isEqualTo("ACCEPTED");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isZero();
	}
	@Test void sameOffsetHundredTimesHasOneJournalAndDltAndPartialFailureRollsBack()throws Exception {
		// given
		UUID id=accepted();var r=record(id,"{".getBytes(StandardCharsets.UTF_8),seed,UUID.randomUUID());var ack=mock(Acknowledgment.class);doThrow(new org.springframework.dao.DataIntegrityViolationException("fixture")).when(outbox).appendQuarantine(any(),any(),any());
		// when / then
		assertThatThrownBy(()->consumer.onRequest(r,ack)).hasMessageContaining("DELIVERY_RETRY");verifyNoInteractions(ack);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantined_messages WHERE source_offset=?",Integer.class,seed)).isZero();reset(outbox);
		for(int i=0;i<100;i++)consumer.onRequest(r,ack);verify(ack,times(100)).acknowledge();UUID q=quarantine(seed);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=?",Integer.class,q.toString())).isEqualTo(1);
		var changed=record(id,"[".getBytes(StandardCharsets.UTF_8),seed,UUID.randomUUID());assertThatThrownBy(()->consumer.onRequest(changed,ack)).hasMessageContaining("DELIVERY_RETRY");verify(ack,times(100)).acknowledge();
	}
	@Test void adminRepairIsIdempotentAndKeepsOriginalReferenceAndCanonicalInput()throws Exception {
		// given
		UUID id=accepted();var original=event(id);consumer.onRequest(record(id,"{".getBytes(StandardCharsets.UTF_8),seed,original.eventId()),mock(Acknowledgment.class));UUID q=quarantine(seed),command=UUID.randomUUID();var pool=Executors.newFixedThreadPool(10);
		try {
			// when
			var calls=new ArrayList<Future<QuarantineReplayService.Result>>();for(int i=0;i<10;i++)calls.add(pool.submit(()->replay.repairAndReplay(users.getLast(),q,command,id,"원문 계약 복구")));var ids=new HashSet<UUID>();for(var call:calls){var result=call.get(15,TimeUnit.SECONDS);assertThat(result.outcome()).isEqualTo("REPLAYED");ids.add(result.eventId());}
			// then
			assertThat(ids).hasSize(1).doesNotContain(original.eventId());UUID emitted=ids.iterator().next();assertThat(jdbc.queryForObject("SELECT original_event_id FROM outbox_events WHERE id=?",String.class,emitted.toString())).isEqualTo(original.eventId().toString());var repaired=mapper.readValue(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE id=?",String.class,emitted.toString()),ReportEvent.class);assertThat(repaired.payload()).isEqualTo(original.payload());consumer.onRequest(record(id,mapper.writeValueAsBytes(repaired),seed+1,emitted),mock(Acknowledgment.class));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(replay.repairAndReplay(users.getLast(),q,command,id,"원문 계약 복구").eventId()).isEqualTo(emitted);assertThatThrownBy(()->replay.repairAndReplay(users.getLast(),q,command,id,"다른 사유")).hasMessageContaining("CONFLICT");
		}finally{pool.shutdown();assertThat(pool.awaitTermination(20,TimeUnit.SECONDS)).isTrue();}
	}
	@Test void repairCannotRestartFailedJobAndNonAdminCannotRepair()throws Exception {
		// given
		UUID id=accepted();var original=event(id);consumer.onRequest(record(id,mapper.writeValueAsBytes(original),seed,original.eventId()),mock(Acknowledgment.class));var lease=claim();claims.failOrRetry(lease,"PDF_INVALID",false,clock.now());consumer.onRequest(record(id,"{".getBytes(StandardCharsets.UTF_8),seed+1,original.eventId()),mock(Acknowledgment.class));UUID q=quarantine(seed+1);
		// when / then
		assertThatThrownBy(()->replay.repairAndReplay(users.getFirst(),q,UUID.randomUUID(),id,"재처리")).hasMessageContaining("FORBIDDEN");assertThat(replay.repairAndReplay(users.getLast(),q,UUID.randomUUID(),id,"재처리").outcome()).isEqualTo("MANUAL_RECOVERY_REQUIRED");assertThat(jdbc.queryForObject("SELECT state FROM report_jobs WHERE request_id=?",String.class,id.toString())).isEqualTo("FAILED");assertThat(jdbc.queryForObject("SELECT generation FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
	}
	@Test void separateReplayCliProcessAuditsOneCorrectionCommand()throws Exception {
		// given
		UUID id=accepted();var original=event(id);consumer.onRequest(record(id,"{".getBytes(StandardCharsets.UTF_8),seed,original.eventId()),mock(Acknowledgment.class));UUID q=quarantine(seed),command=UUID.randomUUID();String classpath=java.nio.file.Files.readString(java.nio.file.Path.of("build/portfolio-report-classpath.txt")).strip();
		for(int i=0;i<2;i++){
			java.nio.file.Path log=java.nio.file.Files.createTempFile("sentinel-replay-cli-", ".log");Process process=null;
			try{
				// when
				var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-cp",classpath,"com.aivle.project.reporting.internal.messaging.quarantine.QuarantineReplayCommand",q.toString(),command.toString(),users.getLast().toString(),id.toString(),"격리 원인 제거 후 재전달");var env=builder.environment();env.clear();for(String name:List.of("PATH","HOME","USER","LANG","JAVA_HOME"))if(System.getenv(name)!=null)env.put(name,System.getenv(name));env.put("SPRING_PROFILES_ACTIVE","local");env.put("PORTFOLIO_SECRET_DIR",com.aivle.project.support.PortfolioSecretPaths.directory().toString());process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
				// then
				assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).withFailMessage(java.nio.file.Files.readString(log)).isZero();assertThat(java.nio.file.Files.readString(log)).contains("REPLAYED").doesNotContain("격리 원인 제거 후 재전달");
			}finally{if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}java.nio.file.Files.deleteIfExists(log);}
		}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantine_replay_commands WHERE quarantine_id=?",Integer.class,q.toString())).isEqualTo(1);
	}
	@Test void repairedEventTraversesActualCdcWithOriginalIdHeader()throws Exception {
		// given: 기존 offset/history를 유지한 설정 갱신이다.
		var configure=new ProcessBuilder("python3","scripts/reliability/configure_cdc.py","configure").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();try{assertThat(configure.waitFor(150,TimeUnit.SECONDS)).isTrue();assertThat(configure.exitValue()).isZero();}finally{if(configure.isAlive()){configure.destroyForcibly();configure.waitFor(5,TimeUnit.SECONDS);}}
		UUID id=accepted();var original=event(id);consumer.onRequest(record(id,"{".getBytes(StandardCharsets.UTF_8),seed,original.eventId()),mock(Acknowledgment.class));var result=replay.repairAndReplay(users.getLast(),quarantine(seed),UUID.randomUUID(),id,"계약 확인 후 재전달");
		var properties=new Properties();properties.put("bootstrap.servers","127.0.0.1:19092");properties.put("group.id","repair-wire-"+UUID.randomUUID());properties.put("enable.auto.commit","false");properties.put("auto.offset.reset","earliest");properties.put("key.deserializer",org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());properties.put("value.deserializer",org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName());
		try(var wire=new org.apache.kafka.clients.consumer.KafkaConsumer<byte[],byte[]>(properties)){
			wire.subscribe(List.of(CustomReportTopics.REQUEST));ConsumerRecord<byte[],byte[]> found=null;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
			// when
			while(found==null&&System.nanoTime()<end)for(var message:wire.poll(java.time.Duration.ofMillis(200)))if(message.value()!=null){try{if(mapper.readTree(message.value()).path("eventId").asText().equals(result.eventId().toString())){found=message;break;}}catch(com.fasterxml.jackson.core.JsonProcessingException unrelated){/* 다른 시험의 불량 event는 건너뛴다. */}}
			// then
			assertThat(found).isNotNull();assertThat(new String(found.key(),StandardCharsets.UTF_8)).isEqualTo(id.toString());assertThat(new String(found.headers().lastHeader("id").value(),StandardCharsets.UTF_8)).isEqualTo(result.eventId().toString());assertThat(new String(found.headers().lastHeader("originalEventId").value(),StandardCharsets.UTF_8)).isEqualTo(original.eventId().toString());consumer.onRequest(found,mock(Acknowledgment.class));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
			System.out.println("REPLAY_WIRE_EVIDENCE "+mapper.writeValueAsString(Map.of("eventId",result.eventId(),"originalEventId",original.eventId(),"topic",found.topic(),"partition",found.partition(),"offset",found.offset())));
		}
	}
}
