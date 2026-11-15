package com.aivle.project.reporting.internal.messaging;
import com.aivle.project.reporting.api.CustomReportTopics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.reporting.internal.messaging.consumer.ReportDeliveryObserver;
import com.aivle.project.reporting.internal.company.reportjob.repository.ReportJobRepository;
import com.aivle.project.reporting.api.CustomReportCommand;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("realinfra") @ActiveProfiles("local") @Import(ReportConsumerAckIntegrationTest.Hooks.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.messaging.enabled=true","custom-report.messaging.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportConsumerAckIntegrationTest extends ReportFlowIntegrationSupport {
	static final String GROUP="sentinel-ack-test-"+UUID.randomUUID();
	@DynamicPropertySource static void group(DynamicPropertyRegistry properties){properties.add("custom-report.messaging.group-id",()->GROUP);}
	@Autowired KafkaListenerEndpointRegistry registry;@Autowired Barrier hook;@MockitoSpyBean ReportJobRepository jobs;
	MessageListenerContainer container;AdminClient admin;boolean mysqlStopped,brokerStopped,connectStopped,cdcRecoveryNeeded;Set<String> baseline;
	CountDownLatch enteredDb=new CountDownLatch(1),releaseDb=new CountDownLatch(1);AtomicBoolean blockDb=new AtomicBoolean();
	@BeforeEach void startPaused()throws Exception {
		container=registry.getListenerContainer("customReportRequests");assertThat(container).as("실제 업무 listener 등록").isNotNull();hook.reset();
		admin=AdminClient.create(kafkaProperties());baseline=new HashSet<>(jdbc.queryForList("SELECT id FROM quarantined_messages",String.class));
		var spec=new HashMap<TopicPartition,OffsetSpec>();for(int i=0;i<3;i++)spec.put(new TopicPartition(CustomReportTopics.REQUEST,i),OffsetSpec.latest());var ends=admin.listOffsets(spec).all().get(10,TimeUnit.SECONDS);var offsets=new HashMap<TopicPartition,OffsetAndMetadata>();ends.forEach((part,end)->offsets.put(part,new OffsetAndMetadata(end.offset())));admin.alterConsumerGroupOffsets(GROUP,offsets).all().get(10,TimeUnit.SECONDS);
		container.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<deadline){var groups=admin.describeConsumerGroups(List.of(GROUP)).all().get(10,TimeUnit.SECONDS);if(groups.get(GROUP).members().stream().mapToInt(m->m.assignment().topicPartitions().size()).sum()==3)break;Thread.sleep(100);}container.pause();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!container.isContainerPaused()&&System.nanoTime()<deadline)Thread.sleep(50);assertThat(container.isContainerPaused()).isTrue();
		doAnswer(call->{boolean result=(Boolean)call.callRealMethod();if(blockDb.compareAndSet(true,false)){enteredDb.countDown();if(!releaseDb.await(15,TimeUnit.SECONDS))throw new IllegalStateException("DB barrier timeout");}return result;}).when(jobs).createIfAbsent(any());
	}
	@AfterEach void restoreAndStop()throws Exception {
		releaseDb.countDown();hook.release.countDown();
		try{if(mysqlStopped)control("mysql-start");if(brokerStopped)control("broker-start");if(connectStopped)control("up");if(cdcRecoveryNeeded)recoverCdc();}
		finally{if(container!=null)container.stop();if(admin!=null)admin.close();}
		if(baseline!=null)for(var pos:hook.positions)for(String id:jdbc.queryForList("SELECT id FROM quarantined_messages WHERE source_topic=? AND source_partition=? AND source_offset=?",String.class,pos.topic(),pos.partition(),pos.offset()))if(!baseline.contains(id)){jdbc.update("DELETE FROM quarantine_replay_commands WHERE quarantine_id=?",id);jdbc.update("DELETE FROM outbox_events WHERE aggregateid=?",id);jdbc.update("DELETE FROM quarantined_messages WHERE id=?",id);}
	}
	UUID prepare()throws Exception {
		UUID id=requests.accept(users.getFirst(),company,"ack-"+UUID.randomUUID(),new CustomReportCommand(null,"실제 offset 검증")).requestId();hook.target=id.toString();
		// CDC의 DB 재연결 시간과 listener 내부 경계의 대기 시간을 구분한다.
		String event=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString());long started=System.nanoTime();
		try(var observer=observer(CustomReportTopics.REQUEST)){awaitEvent(observer,UUID.fromString(event));}
		evidence("cdc-arrival",Map.of("requestId",id,"eventId",event,"elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)));return id;
	}
	long committed(Pos pos)throws Exception {var offsets=admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);var value=offsets.get(new TopicPartition(pos.topic(),pos.partition()));return value==null?0:value.offset();}
	void awaitAck(Pos pos)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);while(System.nanoTime()<deadline&&committed(pos)<pos.offset()+1)Thread.sleep(100);assertThat(committed(pos)).isGreaterThanOrEqualTo(pos.offset()+1);}
	@Test void commitAndAckBoundariesNeverAdvanceOffsetBeforeDatabaseCommit()throws Exception {
		// given
		blockDb.set(true);hook.block.set(true);UUID id=prepare();container.resume();
		try {
			// when: Inbox/job transaction 내부를 멈춘다.
			assertThat(enteredDb.await(15,TimeUnit.SECONDS)).isTrue();Pos pos=hook.last.get();assertThat(committed(pos)).isLessThanOrEqualTo(pos.offset());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isZero();releaseDb.countDown();
			// then: DB commit 이후 ACK 직전에도 offset은 그대로다.
			assertThat(hook.entered.await(15,TimeUnit.SECONDS)).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(committed(pos)).isLessThanOrEqualTo(pos.offset());hook.release.countDown();awaitAck(pos);evidence("commit-before-ack",Map.of("requestId",id,"position",pos,"committed",committed(pos)));
		}finally{releaseDb.countDown();hook.release.countDown();}
	}
	@Test void databaseCommitThenAckFailureRedeliversOneInboxAndJob()throws Exception {
		// given
		hook.throwOnce.set(true);hook.block.set(true);UUID id=prepare();container.resume();
		try {
			// when
			assertThat(hook.entered.await(20,TimeUnit.SECONDS)).isTrue();Pos pos=hook.last.get();assertThat(hook.calls.get()).isGreaterThanOrEqualTo(2);assertThat(committed(pos)).isLessThanOrEqualTo(pos.offset());
			// then
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_events WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);hook.release.countDown();awaitAck(pos);evidence("ack-window-redelivery",Map.of("requestId",id,"position",pos,"listenerCalls",hook.calls.get(),"committed",committed(pos)));
		}finally{hook.release.countDown();}
	}
	@Test void realMysqlOutageDoesNotCommitOffsetAndRecoversAfterDatabaseRestart()throws Exception {
		// given
		UUID id=prepare();mysqlStopped=true;cdcRecoveryNeeded=true;control("mysql-stop");
		try {
			// when
			container.resume();assertThat(hook.failed.await(45,TimeUnit.SECONDS)).isTrue();Pos pos=hook.last.get();assertThat(committed(pos)).isLessThanOrEqualTo(pos.offset());
			// then
			control("mysql-start");mysqlStopped=false;recoverCdc();cdcRecoveryNeeded=false;awaitAck(pos);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
			UUID canary=prepare();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,canary.toString())==0&&System.nanoTime()<deadline)Thread.sleep(100);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,canary.toString())).isEqualTo(1);evidence("mysql-outage",Map.of("requestId",id,"position",pos,"committed",committed(pos),"newCdcRequestId",canary));
		}finally{if(mysqlStopped){control("mysql-start");mysqlStopped=false;}}
	}
	@Test void dltBrokerOutageKeepsRawJournalAndOutboxUntilDeliveryResumes()throws Exception {
		// given: 정상 원본 event를 먼저 확인하고 수신 위치를 그 뒤로 고정한다.
		UUID id=prepare();String original=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString());
		try(var observer=observer(CustomReportTopics.REQUEST)){
			awaitEvent(observer,UUID.fromString(original));container.stop();var specs=new HashMap<TopicPartition,OffsetSpec>();for(int i=0;i<3;i++)specs.put(new TopicPartition(CustomReportTopics.REQUEST,i),OffsetSpec.latest());var ends=admin.listOffsets(specs).all().get(10,TimeUnit.SECONDS);var next=new HashMap<TopicPartition,OffsetAndMetadata>();ends.forEach((p,o)->next.put(p,new OffsetAndMetadata(o.offset())));admin.alterConsumerGroupOffsets(GROUP,next).all().get(10,TimeUnit.SECONDS);container.start();container.pause();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!container.isContainerPaused()&&System.nanoTime()<end)Thread.sleep(50);
			connectStopped=true;control("connect-stop");hook.block.set(true);hook.entered=new CountDownLatch(1);hook.last.set(null);var producerConfig=new Properties();producerConfig.putAll(kafkaProperties());producerConfig.put("key.serializer",ByteArraySerializer.class.getName());producerConfig.put("value.serializer",ByteArraySerializer.class.getName());
			try(var producer=new KafkaProducer<byte[],byte[]>(producerConfig)){var poison=new ProducerRecord<byte[],byte[]>(CustomReportTopics.REQUEST,id.toString().getBytes(StandardCharsets.UTF_8),"{비공개원문".getBytes(StandardCharsets.UTF_8));poison.headers().add("id",UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));producer.send(poison).get(15,TimeUnit.SECONDS);}
			container.resume();assertThat(hook.entered.await(15,TimeUnit.SECONDS)).isTrue();Pos pos=hook.last.get();String q=jdbc.queryForObject("SELECT id FROM quarantined_messages WHERE source_topic=? AND source_partition=? AND source_offset=?",String.class,pos.topic(),pos.partition(),pos.offset());String dlt=jdbc.queryForObject("SELECT dlt_event_id FROM quarantined_messages WHERE id=?",String.class,q);
			// when
			brokerStopped=true;control("broker-stop");hook.release.countDown();assertThat(hook.failed.await(30,TimeUnit.SECONDS)).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=?",Integer.class,q)).isEqualTo(1);
			// then
			control("broker-start");brokerStopped=false;control("up");connectStopped=false;awaitAck(pos);try(var dead=observer(CustomReportTopics.DLT)){JsonNodeValue value=awaitEvent(dead,UUID.fromString(dlt));assertThat(value.text()).doesNotContain("비공개원문");}assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantined_messages WHERE id=?",Integer.class,q)).isEqualTo(1);evidence("dlt-broker-outage",Map.of("quarantineId",q,"dltEventId",dlt,"position",pos,"committed",committed(pos)));
		}finally{hook.release.countDown();}
	}
	KafkaConsumer<byte[],byte[]> observer(String topic){var p=new Properties();p.putAll(kafkaProperties());p.put("key.deserializer",ByteArrayDeserializer.class.getName());p.put("value.deserializer",ByteArrayDeserializer.class.getName());p.put("group.id","observer-"+UUID.randomUUID());p.put("enable.auto.commit","false");p.put("auto.offset.reset","earliest");var consumer=new KafkaConsumer<byte[],byte[]>(p);consumer.subscribe(List.of(topic));return consumer;}
	record JsonNodeValue(String text) { }
	JsonNodeValue awaitEvent(KafkaConsumer<byte[],byte[]> observer,UUID id)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);while(System.nanoTime()<end)for(var r:observer.poll(Duration.ofMillis(200))){if(r.value()==null)continue;try{var value=mapper.readTree(r.value());if(value.path("eventId").asText().equals(id.toString()))return new JsonNodeValue(new String(r.value(),StandardCharsets.UTF_8));}catch(com.fasterxml.jackson.core.JsonProcessingException unrelated){/* 별도 시험의 불량 event는 건너뛴다. */}}throw new IllegalStateException("CDC event observation timeout");}
	void control(String action)throws Exception {Path log=output().resolve("control-"+action+"-"+UUID.randomUUID()+".log");var process=new ProcessBuilder("python3","scripts/reliability/cdc_runtime.py",action).redirectErrorStream(true).redirectOutput(log.toFile()).start();assertThat(process.waitFor(150,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).as("owned component %s",action).isZero();}
	void recoverCdc()throws Exception {Path log=output().resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-cdc-recover-"+UUID.randomUUID()+".json");var process=new ProcessBuilder("python3","scripts/reliability/configure_cdc.py","recover").redirectErrorStream(true).redirectOutput(log.toFile()).start();try{assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}}
	Path output()throws Exception {Path p=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/ack"));Files.createDirectories(p);return p;}
	void evidence(String name,Object body)throws Exception {Files.writeString(output().resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-"+name+"-"+UUID.randomUUID()+".json"),mapper.writeValueAsString(body),StandardOpenOption.CREATE_NEW);}
	record Pos(String topic,int partition,long offset) { }
	@TestConfiguration(proxyBeanMethods=false) static class Hooks {@Bean Barrier barrier(){return new Barrier();}}
	static class Barrier implements ReportDeliveryObserver {
		volatile String target;AtomicBoolean block=new AtomicBoolean(),throwOnce=new AtomicBoolean();AtomicInteger calls=new AtomicInteger();AtomicReference<Pos> last=new AtomicReference<>();Set<Pos> positions=ConcurrentHashMap.newKeySet();volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),failed=new CountDownLatch(1);
		void reset(){target=null;block.set(false);throwOnce.set(false);calls.set(0);last.set(null);positions.clear();entered=new CountDownLatch(1);release=new CountDownLatch(1);failed=new CountDownLatch(1);}
		boolean ours(ConsumerRecord<byte[],byte[]> r){return r.key()!=null&&new String(r.key(),StandardCharsets.UTF_8).equals(target);}
		public void onReceived(ConsumerRecord<byte[],byte[]> r){if(ours(r)){var p=new Pos(r.topic(),r.partition(),r.offset());last.set(p);positions.add(p);}}
		public void beforeAck(ConsumerRecord<byte[],byte[]> r){if(!ours(r))return;calls.incrementAndGet();if(throwOnce.compareAndSet(true,false))throw new IllegalStateException("ACK window fixture");if(block.get()){entered.countDown();try{if(!release.await(30,TimeUnit.SECONDS))throw new IllegalStateException("ACK barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("ACK interrupted");}}}
		public void onRetry(ConsumerRecord<byte[],byte[]> r,String code){if(ours(r))failed.countDown();}
	}
}
