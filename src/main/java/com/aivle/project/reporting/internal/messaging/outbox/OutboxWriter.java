package com.aivle.project.reporting.internal.messaging.outbox;
import com.aivle.project.reporting.api.ReportEventType;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aivle.project.reporting.internal.company.reportrequest.entity.ReportRequestEntity;
import java.time.Instant;
import java.util.UUID;
@Component public class OutboxWriter {
	public void appendQuarantine(UUID quarantineId,UUID eventId,java.util.Map<String,Object> metadata){
		requireTransaction();var envelope=java.util.Map.of("eventId",eventId,"eventType","MESSAGE_QUARANTINED","schemaVersion",1,"requestId",quarantineId,"occurredAt",Instant.now(),"payload",metadata);
		try{jdbc.update("INSERT INTO outbox_events(id,aggregatetype,aggregateid,type,destination,payload) VALUES(?,'custom_report_quarantine',?,'MESSAGE_QUARANTINED','sentinel.custom-report.dlt.v1',?)",eventId.toString(),quarantineId.toString(),mapper.writeValueAsString(envelope));}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException("Invalid quarantine envelope");}
	}
	public void appendReplay(ReportEvent event,UUID originalEventId){
		requireTransaction();if(event.eventType()!=ReportEventType.REQUEST_ACCEPTED)throw new IllegalArgumentException("Invalid replay type");
		try{jdbc.update("INSERT INTO outbox_events(id,aggregatetype,aggregateid,type,destination,payload,original_event_id) VALUES(?,'custom_report',?,'REQUEST_ACCEPTED','sentinel.custom-report.request.v1',?,?)",event.eventId().toString(),event.requestId().toString(),mapper.writeValueAsString(event),originalEventId==null?null:originalEventId.toString());}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException("Invalid replay envelope");}
	}
	private void requireTransaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Outbox requires a participating transaction");}
	private final JdbcTemplate jdbc;private final ObjectMapper mapper;
	public OutboxWriter(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
	public ReportEvent event(ReportRequestEntity r,ReportEventType type){return new ReportEvent(UUID.randomUUID(),type,1,r.id(),Instant.now(),new ReportEvent.Payload(r.companyId(),r.targetQuarterKey(),r.snapshotId(),r.inputHash(),r.instructions(),r.stateVersion(),r.status()));}
	public void append(ReportEvent event) {
		if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Outbox requires a participating transaction");
		String destination=switch(event.eventType()){case REQUEST_ACCEPTED->"sentinel.custom-report.request.v1";case STATUS_CHANGED->"sentinel.custom-report.status.v1";case MESSAGE_QUARANTINED->"sentinel.custom-report.dlt.v1";};
		try{jdbc.update("INSERT INTO outbox_events(id,aggregatetype,aggregateid,type,destination,payload) VALUES(?,'custom_report',?,?,?,?)",event.eventId().toString(),event.requestId().toString(),event.eventType().name(),destination,mapper.writeValueAsString(event));}
		catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalArgumentException("Invalid event envelope",invalid);}
	}
}
