package com.aivle.project.messaging.consumer;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.*;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

/** 새 durable 요청 전용 factory. 기존 AiJob listener 설정은 바꾸지 않는다. */
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="custom-report.messaging.enabled",havingValue="true")
public class CustomReportKafkaConfig {
	@Bean
	public ConcurrentKafkaListenerContainerFactory<byte[],byte[]> customReportKafkaListenerContainerFactory(KafkaProperties kafka,ObjectProvider<SslBundles> bundles,@Value("${custom-report.messaging.relay-mode:cdc}") String mode,@Value("${custom-report.messaging.polling-enabled:false}") boolean polling,@Value("${custom-report.messaging.auto-startup:${spring.kafka.listener.auto-startup:true}}") boolean startup){
		if(!mode.equals("cdc")||polling)throw new IllegalStateException("선택한 CDC와 다른 발행기를 동시에 실행할 수 없습니다");
		var props=kafka.buildConsumerProperties(bundles.getIfAvailable());
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,1);props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG,10000);props.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG,10000);props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,180000);
		var factory=new ConcurrentKafkaListenerContainerFactory<byte[],byte[]>();factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));factory.setConcurrency(1);factory.setAutoStartup(startup);
		var container=factory.getContainerProperties();container.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);container.setAsyncAcks(false);container.setSyncCommits(true);container.setSyncCommitTimeout(Duration.ofSeconds(5));container.setPollTimeout(250);container.setMissingTopicsFatal(false);
		// DB/ACK의 일시 장애를 조용히 버리지 않는다. AI 실행 재시도 예산과는 별개다.
		var errors=new DefaultErrorHandler(new FixedBackOff(1000,FixedBackOff.UNLIMITED_ATTEMPTS));errors.setAckAfterHandle(false);errors.setCommitRecovered(false);errors.setLogLevel(KafkaException.Level.WARN);factory.setCommonErrorHandler(errors);
		return factory;
	}
}
