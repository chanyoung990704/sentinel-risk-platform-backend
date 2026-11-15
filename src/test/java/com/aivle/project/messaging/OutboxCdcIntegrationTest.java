package com.aivle.project.reporting.internal.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.aivle.project.reporting.api.ReportEventType;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("cdc") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false","custom-report.admission.user-limit=200"})
class OutboxCdcIntegrationTest extends ReportFlowIntegrationSupport {
	static final String NAME="sentinel-custom-reports-v1",REQUEST="sentinel.custom-report.request.v1",STATUS="sentinel.custom-report.status.v1",DLT="sentinel.custom-report.dlt.v1";
	@MockitoSpyBean OutboxWriter outbox;
	final Set<UUID> owned=new HashSet<>(),rolledBack=new HashSet<>();
	final Map<UUID,ReportEvent> received=new HashMap<>();
	final List<Map<String,Object>> wire=new ArrayList<>();
	final AtomicBoolean rollback=new AtomicBoolean();boolean stopped;
	@BeforeEach void configure()throws Exception {
		assertThat(Path.of("infra/cdc/portfolio-outbox.json")).exists();
		run("configure", "scripts/reliability/configure_cdc.py","configure");
		doAnswer(call->{ReportEvent event=call.getArgument(0);owned.add(event.requestId());call.callRealMethod();if(rollback.get()){rolledBack.add(event.eventId());throw new IllegalStateException("CDC rollback fixture");}return null;}).when(outbox).append(any());
	}
	@AfterEach void restoreWorker()throws Exception {rollback.set(false);if(stopped)run("restore","scripts/reliability/cdc_runtime.py","up");}
	UUID accept(){return requests.accept(users.getFirst(),company,"cdc-"+UUID.randomUUID(),new CustomReportCommand(null,"CDC 입력 보존 시험")).requestId();}
	KafkaConsumer<String,String> consumer(){Properties p=new Properties();p.putAll(kafkaProperties());p.put("group.id","cdc-wire-"+UUID.randomUUID());p.put("enable.auto.commit","false");p.put("auto.offset.reset","earliest");p.put("key.deserializer",StringDeserializer.class.getName());p.put("value.deserializer",StringDeserializer.class.getName());var c=new KafkaConsumer<String,String>(p);c.subscribe(List.of(REQUEST,STATUS,DLT));return c;}
	void collect(KafkaConsumer<String,String> consumer)throws Exception {
		for(var record:consumer.poll(Duration.ofMillis(200))){
			if(record.key()==null||record.value()==null)continue;String candidate=record.key().replace("\"","");UUID request;try{request=UUID.fromString(candidate);}catch(IllegalArgumentException unrelated){continue;}if(!owned.contains(request))continue;
			assertThat(record.key()).isEqualTo(request.toString());var json=mapper.readTree(record.value());assertThat(json.isObject()).isTrue();var event=mapper.treeToValue(json,ReportEvent.class);assertThat(event.requestId()).isEqualTo(request);assertThat(event.schemaVersion()).isEqualTo(1);
			var header=record.headers().lastHeader("id");assertThat(header).isNotNull();assertThat(new String(header.value(),StandardCharsets.UTF_8)).isEqualTo(event.eventId().toString());
			assertThat(record.topic()).isEqualTo(event.eventType()==ReportEventType.REQUEST_ACCEPTED?REQUEST:event.eventType()==ReportEventType.STATUS_CHANGED?STATUS:DLT);
			received.put(event.eventId(),event);wire.add(Map.of("eventId",event.eventId(),"requestId",request,"topic",record.topic(),"partition",record.partition(),"offset",record.offset(),"stateVersion",event.payload().stateVersion()));
		}
	}
	void await(KafkaConsumer<String,String> consumer,Set<UUID> ids)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(!received.keySet().containsAll(ids)&&System.nanoTime()<deadline)collect(consumer);assertThat(received.keySet()).containsAll(ids);}
	Set<UUID> initialIds(){return new HashSet<>(jdbc.queryForList("SELECT o.id FROM outbox_events o JOIN report_requests r ON r.id=o.aggregateid WHERE r.user_id=? AND o.type='REQUEST_ACCEPTED'",String.class,users.getFirst()).stream().map(UUID::fromString).toList());}
	@Test void committedEnvelopeAndStatusReachKafkaAndRollbackNeverAppears()throws Exception {
		// given
		try(var consumer=consumer()){
			for(int i=0;i<10;i++)accept();for(int i=0;i<10;i++){rollback.set(true);assertThatThrownBy(this::accept).isInstanceOf(IllegalStateException.class);rollback.set(false);}var expected=initialIds();
			// when
			await(consumer,expected);for(UUID id:expected)inbox.accept(received.get(id));
			var statuses=new HashSet<>(jdbc.queryForList("SELECT o.id FROM outbox_events o JOIN report_requests r ON r.id=o.aggregateid WHERE r.user_id=? AND o.type='STATUS_CHANGED'",String.class,users.getFirst()).stream().map(UUID::fromString).toList());await(consumer,statuses);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(System.nanoTime()<until)collect(consumer);
			// then
			assertThat(expected).hasSize(10);assertThat(rolledBack).hasSize(10);assertThat(received.keySet()).doesNotContainAnyElementsOf(rolledBack);assertThat(statuses).allSatisfy(id->assertThat(received.get(id).payload().stateVersion()).isEqualTo(2));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs j JOIN report_requests r ON r.id=j.request_id WHERE r.user_id=?",Integer.class,users.getFirst())).isEqualTo(10);evidence("commit-rollback",Map.of("committed",10,"rolledBack",10,"rollbackObserved",0,"wire",wire));
		}
	}
	@Test void hundredAcceptedDuringConnectStopResumeFromPreservedCheckpoint()throws Exception {
		// given
		try(var consumer=consumer()){
			accept();await(consumer,initialIds());JsonNode before=rest("/connectors/"+NAME+"/offsets","GET",null);stopped=true;run("stop","scripts/reliability/cdc_runtime.py","connect-stop");
			// when
			for(int i=0;i<100;i++)accept();var expected=initialIds();run("recreate","scripts/reliability/cdc_runtime.py","connect-recreate");stopped=false;running(NAME);await(consumer,expected);
			// then
			assertThat(expected).hasSize(101);assertThat(received.keySet()).containsAll(expected);for(UUID id:expected)inbox.accept(received.get(id));assertThat(rest("/connectors/"+NAME+"/offsets","GET",null).path("offsets").size()).isPositive();evidence("connect-resume",Map.of("initial",1,"backlog",100,"uniqueObserved",expected.size(),"beforeCheckpoint",before,"afterCheckpoint",rest("/connectors/"+NAME+"/offsets","GET",null),"wire",wire));
		}
	}
	@Test void existingOutboxSnapshotKeepsEventIdAndDoesNotDuplicateJob()throws Exception {
		// given: 별도 소유 connector의 최초 snapshot만 시험하고 정상 connector 위치는 초기화하지 않는다.
		String name="sentinel-outbox-snapshot-"+UUID.randomUUID().toString().replace("-","");UUID request=accept();var expected=initialIds();
		try(var consumer=consumer()){
			await(consumer,expected);var config=mapper.readTree(Files.readString(Path.of("infra/cdc/portfolio-outbox.json"))).deepCopy();var object=(com.fasterxml.jackson.databind.node.ObjectNode)config;object.put("database.server.id","223347");object.put("topic.prefix",name);object.put("schema.history.internal.kafka.topic",name+"-history");object.put("predicates.isOutbox.pattern",name+"\\.sentinel_portfolio\\.outbox_events");
			// when
			try(var admin=org.apache.kafka.clients.admin.AdminClient.create(kafkaProperties())){admin.createTopics(List.of(new org.apache.kafka.clients.admin.NewTopic(name+"-history",1,(short)1).configs(Map.of("cleanup.policy","delete","retention.ms","-1","retention.bytes","-1")))).all().get(15,TimeUnit.SECONDS);}
			rest("/connectors/"+name+"/config","PUT",object);running(name);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(wire.stream().filter(row->row.get("eventId").equals(expected.iterator().next())).count()<2&&System.nanoTime()<deadline)collect(consumer);
			// then
			assertThat(wire.stream().filter(row->row.get("eventId").equals(expected.iterator().next())).count()).isGreaterThanOrEqualTo(2);for(int i=0;i<2;i++)inbox.accept(received.get(expected.iterator().next()));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,request.toString())).isEqualTo(1);evidence("snapshot",Map.of("eventIds",expected,"wire",wire));
		}finally{rest("/connectors/"+name,"DELETE",null);}
	}
	@Test void connectorRestartRetainsExistingRowsAndContinuesWithoutMetadataOnBusinessTopics()throws Exception {
		// given
		try(var consumer=consumer()){
			UUID first=accept();await(consumer,initialIds());String payload=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,first.toString());var before=rest("/connectors/"+NAME+"/offsets","GET",null);
			// when
			rest("/connectors/"+NAME+"/restart?includeTasks=true&onlyFailed=false","POST",null);running(NAME);accept();await(consumer,initialIds());
			// then
			assertThat(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,first.toString())).isEqualTo(payload);assertThat(wire).allSatisfy(row->assertThat(row.get("eventId")).isNotNull());evidence("task-restart",Map.of("beforeCheckpoint",before,"afterCheckpoint",rest("/connectors/"+NAME+"/offsets","GET",null),"wire",wire));
		}
	}
	JsonNode rest(String path,String method,JsonNode body)throws Exception {var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:18083"+path)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString())).build();var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());if(method.equals("DELETE")&&response.statusCode()==404)return mapper.nullNode();assertThat(response.statusCode()).as("Connect endpoint status").isBetween(200,299);return response.body().isBlank()?mapper.nullNode():mapper.readTree(response.body());}
	void running(String name)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(System.nanoTime()<deadline){var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18083/connectors/"+name+"/status")).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()==200){var status=mapper.readTree(response.body());if(status.path("connector").path("state").asText().equals("FAILED")||status.path("tasks").findValuesAsText("state").contains("FAILED"))throw new IllegalStateException("CDC task failed; inspect owned worker");if(status.path("connector").path("state").asText().equals("RUNNING")&&status.path("tasks").size()==1&&status.path("tasks").get(0).path("state").asText().equals("RUNNING"))return;}Thread.sleep(250);}throw new IllegalStateException("CDC ready timeout");}
	void run(String label,String script,String action)throws Exception {Path log=output().resolve("cdc-"+label+"-"+UUID.randomUUID()+".log");var process=new ProcessBuilder("python3",script,action).redirectErrorStream(true).redirectOutput(log.toFile()).start();assertThat(process.waitFor(150,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as("CDC command %s",label).isZero();}
	Path output()throws Exception {Path root=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/custom-cdc"));Files.createDirectories(root);return root;}
	void evidence(String name,Object data)throws Exception {Files.writeString(output().resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-"+name+"-"+UUID.randomUUID()+".json"),mapper.writeValueAsString(data),StandardOpenOption.CREATE_NEW);}
}
