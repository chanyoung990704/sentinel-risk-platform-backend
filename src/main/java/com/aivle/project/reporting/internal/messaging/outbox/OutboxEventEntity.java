package com.aivle.project.reporting.internal.messaging.outbox;
import com.aivle.project.reporting.api.ReportEventType;
import java.util.UUID;
public record OutboxEventEntity(UUID id,String aggregatetype,UUID aggregateid,ReportEventType type,String destination,String payload) { }
