package com.aivle.project.app.internal.messaging;
import com.aivle.project.aiintegration.api.AiJobMessage;

import com.aivle.project.finance.api.AiCommentOperations;
import com.aivle.project.finance.api.LegacyFinancialAi;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * AI 작업 Kafka 컨슈머.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJobKafkaConsumer {

	private final ObjectMapper objectMapper;
	private final LegacyFinancialAi companyAiService;
	private final AiCommentOperations companyAiCommentService;

	@KafkaListener(
		topics = "${app.ai.job.request-topic:ai-job-request}",
		groupId = "${APP_AI_JOB_KAFKA_GROUP_ID:ai-job-consumer}"
	)
	public void consume(String payload) {
		try {
			AiJobMessage message = objectMapper.readValue(payload, AiJobMessage.class);
			process(message);
		} catch (Exception e) {
			log.error("Failed to consume AI job payload: {}", payload, e);
			throw new RuntimeException("Failed to process AI job", e);
		}
	}

	private void process(AiJobMessage message) {
		switch (message.type()) {
			case AI_REPORT -> companyAiService.processReportGeneration(
				message.requestId(),
				message.companyId(),
				message.year(),
				message.quarter()
			);
			case AI_COMMENT_WARMUP -> companyAiCommentService.ensureAiCommentCached(
				message.companyId(),
				message.period()
			);
			default -> log.warn("Unsupported AI job type: {}", message.type());
		}
	}
}
