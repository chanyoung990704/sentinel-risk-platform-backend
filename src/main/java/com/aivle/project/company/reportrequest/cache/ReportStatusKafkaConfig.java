package com.aivle.project.company.reportrequest.cache;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="custom-report.status-cache.enabled",havingValue="true")
public class ReportStatusKafkaConfig {
    @Bean public ConcurrentKafkaListenerContainerFactory<byte[],byte[]> reportStatusKafkaListenerContainerFactory(KafkaProperties kafka,ObjectProvider<SslBundles> bundles,
        @Value("${custom-report.status-cache.auto-startup:${custom-report.messaging.auto-startup:false}}") boolean startup) {
        var props=kafka.buildConsumerProperties(bundles.getIfAvailable());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"latest");props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,1);props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,180000);
        var factory=new ConcurrentKafkaListenerContainerFactory<byte[],byte[]>();factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));factory.setAutoStartup(startup);factory.setConcurrency(1);
        var container=factory.getContainerProperties();container.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);container.setAsyncAcks(false);container.setSyncCommits(true);container.setSyncCommitTimeout(Duration.ofSeconds(5));container.setPollTimeout(250);container.setMissingTopicsFatal(false);
        var errors=new DefaultErrorHandler(new FixedBackOff(1000,FixedBackOff.UNLIMITED_ATTEMPTS));errors.setAckAfterHandle(false);errors.setCommitRecovered(false);factory.setCommonErrorHandler(errors);
        return factory;
    }
}
