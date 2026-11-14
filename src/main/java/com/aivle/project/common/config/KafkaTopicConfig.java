package com.aivle.project.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * AI 작업 토픽 생성 설정.
 */
@Configuration
public class KafkaTopicConfig {
	@Bean
	@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="custom-report.messaging.enabled",havingValue="true")
	public org.springframework.kafka.core.KafkaAdmin.NewTopics customReportTopics(){
		return new org.springframework.kafka.core.KafkaAdmin.NewTopics(
			TopicBuilder.name(com.aivle.project.messaging.CustomReportTopics.REQUEST).partitions(3).replicas(1).build(),
			TopicBuilder.name(com.aivle.project.messaging.CustomReportTopics.STATUS).partitions(3).replicas(1).build(),
			TopicBuilder.name(com.aivle.project.messaging.CustomReportTopics.DLT).partitions(3).replicas(1).build());
	}

	@Bean
	public NewTopic aiJobRequestTopic(@Value("${app.ai.job.request-topic:ai-job-request}") String requestTopic) {
		return TopicBuilder.name(requestTopic)
			.partitions(3)
			.replicas(1)
			.build();
	}
}
