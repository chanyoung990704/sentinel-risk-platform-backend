package com.aivle.project.messaging.quarantine;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.company.reportrequest.model.*;
import com.aivle.project.company.reportjob.repository.*;
import com.aivle.project.messaging.outbox.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
@Service public class QuarantineReplayService {
	private final QuarantineRepository repository;private final ReportRequestRepository requests;private final ReportJobRepository jobs;private final ReportRecoveryRepository admins;private final OutboxWriter outbox;private final TransactionTemplate transaction;
	public QuarantineReplayService(QuarantineRepository repository,ReportRequestRepository requests,ReportJobRepository jobs,ReportRecoveryRepository admins,OutboxWriter outbox,PlatformTransactionManager manager){this.repository=repository;this.requests=requests;this.jobs=jobs;this.admins=admins;this.outbox=outbox;transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
	public record Result(UUID quarantineId,UUID commandId,UUID requestId,String outcome,UUID eventId) { }
	public Result repairAndReplay(Long actor,UUID quarantine,UUID command,UUID request,String reason){
		admins.requireAdmin(actor);if(quarantine==null||command==null||request==null||reason==null)throw error(ReportRequestException.Code.INVALID);String normalized=reason.replace("\r\n","\n").strip();if(normalized.isEmpty()||normalized.codePointCount(0,normalized.length())>1000||!java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(normalized))throw error(ReportRequestException.Code.INVALID);
		try{return transaction.execute(status->{
			var source=repository.lock(quarantine);if(!repository.isRequestSource(quarantine))throw error(ReportRequestException.Code.CONFLICT);var previous=repository.findCommand(command);if(previous.isPresent())return repeated(previous.get(),actor,quarantine,request,normalized);
			var r=requests.lock(request);var job=jobs.findByRequest(request);String outcome;UUID emitted=null;
			if(job.isPresent())outcome=job.get().state().name().equals("FAILED")?"MANUAL_RECOVERY_REQUIRED":"ALREADY_REGISTERED";
			else{
				if(r.status()!=ReportRequestStatus.ACCEPTED||r.stateVersion()!=1||!r.admissionSlotHeld())throw error(ReportRequestException.Code.CONFLICT);
				var event=outbox.event(r,ReportEventType.REQUEST_ACCEPTED);emitted=event.eventId();outbox.appendReplay(event,source.claimedEventId());outcome="REPLAYED";
			}
			var audit=new QuarantineRepository.Replay(command,quarantine,actor,request,normalized,outcome,emitted);repository.recordReplay(audit);return new Result(quarantine,command,request,outcome,emitted);
		});}catch(DuplicateKeyException race){return repeated(repository.findCommand(command).orElseThrow(()->race),actor,quarantine,request,normalized);}
	}
	private Result repeated(QuarantineRepository.Replay row,long actor,UUID quarantine,UUID request,String reason){if(row.actor()!=actor||!row.quarantine().equals(quarantine)||!row.request().equals(request)||!row.reason().equals(reason))throw error(ReportRequestException.Code.CONFLICT);return new Result(quarantine,row.id(),request,row.outcome(),row.event());}
	private ReportRequestException error(ReportRequestException.Code code){return new ReportRequestException(code);}
}
