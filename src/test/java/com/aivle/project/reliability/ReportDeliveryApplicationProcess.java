package com.aivle.project.reliability;
import com.aivle.project.ProjectApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
/** main 전용 classpath로 실행하는 소유 시험 JVM. 시험 설정 bean을 검색하지 않는다. */
public final class ReportDeliveryApplicationProcess {
	public static void main(String[] args)throws Exception {
		if(args.length!=4)throw new IllegalArgumentException("소유 JVM 인수 확인 필요");
		var app=new SpringApplication(ProjectApplication.class);
		app.setDefaultProperties(Map.of("spring.datasource.hikari.connection-timeout","2000"));
		String gate=System.getenv("PORTFOLIO_TEST_ACK_GATE");
		if(gate!=null)app.addInitializers(context->context.getBeanFactory().registerSingleton("ownedAckBoundary",new AckBoundary(Path.of(gate),Path.of(args[0]),java.util.UUID.fromString(System.getenv("PORTFOLIO_TEST_ACK_TARGET")).toString())));
		if("true".equals(System.getenv("PORTFOLIO_TEST_ARTIFACT_HALT")))app.addInitializers(context->{
			var definition=new org.springframework.beans.factory.support.RootBeanDefinition(ArtifactBoundary.class);definition.setPrimary(true);definition.setAutowireMode(org.springframework.beans.factory.support.AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
			((org.springframework.beans.factory.support.BeanDefinitionRegistry)context).registerBeanDefinition("ownedArtifactBoundary",definition);
		});
		var context=(ServletWebServerApplicationContext)app.run("--spring.profiles.active=local","--server.port=0","--server.address=127.0.0.1","--spring.kafka.listener.auto-startup=false","--custom-report.messaging.enabled=true","--custom-report.messaging.auto-startup=true","--custom-report.messaging.group-id="+args[1],"--custom-report.worker.enabled=true","--custom-report.worker.retry-jitter-ms=0","--custom-report.admission.user-limit=200","--app.file.upload.upload-dir="+args[2],"--logging.level.root=WARN","--spring.main.banner-mode=off");
		publishReady(Path.of(args[0]),new ObjectMapper().writeValueAsString(Map.of("pid",ProcessHandle.current().pid(),"port",context.getWebServer().getPort(),"nonce",args[3])),created->{});
	}
	static void publishReady(Path ready,String identity,java.util.function.Consumer<Path> afterCreate)throws Exception {
		if(Files.exists(ready))throw new FileAlreadyExistsException(ready.toString());
		Path temporary=Files.createTempFile(ready.getParent(),".ready-",".tmp");
		// 존재 여부를 보는 부모에게 완성된 식별자만 공개한다.
		try{afterCreate.accept(temporary);Files.writeString(temporary,identity);Files.move(temporary,ready,StandardCopyOption.ATOMIC_MOVE);}
		finally{Files.deleteIfExists(temporary);}
	}
	static final class AckBoundary implements com.aivle.project.messaging.consumer.ReportDeliveryObserver {
		private final Path gate,ready;private final String target;private final ObjectMapper mapper=new ObjectMapper();
		AckBoundary(Path gate,Path ready,String target){this.gate=gate;this.ready=ready;this.target=target;}
		boolean ours(org.apache.kafka.clients.consumer.ConsumerRecord<byte[],byte[]> record){return record.key()!=null && target.equals(new String(record.key(),java.nio.charset.StandardCharsets.UTF_8)) && record.value()!=null;}
		public synchronized void onReceived(org.apache.kafka.clients.consumer.ConsumerRecord<byte[],byte[]> record){
			if(!ours(record))return;
			try{Files.writeString(gate.resolveSibling("ack-received.jsonl"),mapper.writeValueAsString(Map.of("key",new String(record.key(),java.nio.charset.StandardCharsets.UTF_8),"partition",record.partition(),"offset",record.offset()))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception failure){throw new IllegalStateException("ACK 수신 증거 기록 실패");}
		}
		public synchronized void beforeAck(org.apache.kafka.clients.consumer.ConsumerRecord<byte[],byte[]> record){
			if(!ours(record)||Files.exists(gate))return;
			try{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(!Files.exists(ready)&&System.nanoTime()<end)Thread.sleep(10);if(!Files.exists(ready))throw new IllegalStateException("ready 경계 관찰 실패");}catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
			try{Files.writeString(gate,mapper.writeValueAsString(Map.of("key",new String(record.key(),java.nio.charset.StandardCharsets.UTF_8),"partition",record.partition(),"offset",record.offset())),StandardOpenOption.CREATE_NEW);}catch(Exception failure){throw new IllegalStateException("ACK 경계 증거 기록 실패");}
			Runtime.getRuntime().halt(95);
		}
	}
	public static final class ArtifactBoundary extends com.aivle.project.messaging.outbox.OutboxWriter {
		public ArtifactBoundary(org.springframework.jdbc.core.JdbcTemplate jdbc,ObjectMapper mapper){super(jdbc,mapper);}
		@Override public void append(com.aivle.project.messaging.outbox.ReportEvent event){
			super.append(event);
			if(event.eventType()==com.aivle.project.messaging.outbox.ReportEventType.STATUS_CHANGED && event.payload().status()==com.aivle.project.company.reportrequest.model.ReportRequestStatus.SUCCEEDED)Runtime.getRuntime().halt(96);
		}
	}
}
