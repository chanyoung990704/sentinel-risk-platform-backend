package com.aivle.project.reporting.internal.messaging.inbox;
import java.util.UUID;
public record InboxEventEntity(String consumer,UUID eventId,UUID requestId,String payloadHash) { }
