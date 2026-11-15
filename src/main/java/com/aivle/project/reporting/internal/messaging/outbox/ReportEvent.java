package com.aivle.project.reporting.internal.messaging.outbox;
import com.aivle.project.reporting.api.ReportEventType;
import com.aivle.project.reporting.api.ReportRequestStatus;
import java.time.Instant;
import java.util.UUID;
/** 로그에는 원문을 쓰지 않고, 재실행에 필요한 입력은 DB payload에 보존한다. */
public record ReportEvent(UUID eventId,ReportEventType eventType,int schemaVersion,UUID requestId,Instant occurredAt,Payload payload) {
	public record Payload(long companyId,int targetQuarterKey,UUID snapshotId,String inputHash,String additionalInstructions,long stateVersion,ReportRequestStatus status) { }
}
