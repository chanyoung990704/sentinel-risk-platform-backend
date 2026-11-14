package com.aivle.project.messaging.inbox;
import java.util.UUID;
public record InboxEventEntity(String consumer,UUID eventId,UUID requestId,String payloadHash) { }
