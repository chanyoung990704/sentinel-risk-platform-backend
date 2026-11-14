package com.aivle.project.messaging.outbox;
import java.util.UUID;
public record OutboxEventEntity(UUID id,String aggregatetype,UUID aggregateid,ReportEventType type,String destination,String payload) { }
