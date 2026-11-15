package com.aivle.project.reporting.internal.messaging.inbox;
import com.aivle.project.reporting.api.InboxAcceptance;
import com.aivle.project.reporting.internal.messaging.outbox.ReportEvent;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.aivle.project.reporting.api.ReportEventType;

import com.aivle.project.reporting.api.ReportRequestStatus;
import com.aivle.project.reporting.api.ReportRequestException;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.reporting.internal.company.reportjob.repository.ReportJobRepository;
import com.aivle.project.foundation.api.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service public class ReportJobInbox {
	private static final String CONSUMER="custom-report-jobs-v1";
	private final InboxEventRepository inbox;private final ReportJobRepository jobs;private final ReportRequestRepository requests;private final OutboxWriter outbox;private final ObjectMapper mapper;
	public ReportJobInbox(InboxEventRepository inbox,ReportJobRepository jobs,ReportRequestRepository requests,OutboxWriter outbox,ObjectMapper mapper){this.inbox=inbox;this.jobs=jobs;this.requests=requests;this.outbox=outbox;this.mapper=mapper;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public InboxAcceptance accept(ReportEvent event){
		if(event==null||event.eventId()==null||event.requestId()==null||event.occurredAt()==null||event.schemaVersion()!=1||event.eventType()!=ReportEventType.REQUEST_ACCEPTED||event.payload()==null)throw invalid();
		var r=requests.lock(event.requestId());var p=event.payload();
		if(p.companyId()!=r.companyId()||p.targetQuarterKey()!=r.targetQuarterKey()||!r.snapshotId().equals(p.snapshotId())||!r.inputHash().equals(p.inputHash())||!r.instructions().equals(p.additionalInstructions())||p.stateVersion()!=1||p.status()!=ReportRequestStatus.ACCEPTED)throw invalid();
		String hash;try{hash=Hashing.sha256(mapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw invalid();}
		boolean inserted=inbox.insertIfAbsent(new InboxEventEntity(CONSUMER,event.eventId(),event.requestId(),hash));
		var stored=inbox.lock(CONSUMER,event.eventId());if(!stored.requestId().equals(event.requestId())||!stored.payloadHash().equals(hash))throw new ReportRequestException(ReportRequestException.Code.CONFLICT);
		if(!inserted)return InboxAcceptance.DUPLICATE;
		if(!jobs.createIfAbsent(event.requestId()))return InboxAcceptance.DUPLICATE;
		if(r.status()!=ReportRequestStatus.ACCEPTED)throw new ReportRequestException(ReportRequestException.Code.CONFLICT);
		var updated=requests.transition(r.id(),ReportRequestStatus.ENQUEUED,null,null);outbox.append(outbox.event(updated,ReportEventType.STATUS_CHANGED));return InboxAcceptance.ACCEPTED;
	}
	private ReportRequestException invalid(){return new ReportRequestException(ReportRequestException.Code.INVALID);}
}
