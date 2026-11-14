package com.aivle.project.company.reportjob.service;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.aivle.project.company.reportjob.repository.*;
import com.aivle.project.company.reportjob.entity.ReportRecoveryCommandEntity;
import com.aivle.project.company.reportjob.model.ReportJobStatus;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.company.reportrequest.service.ReportAdmissionService;
import com.aivle.project.company.reportrequest.model.*;
import com.aivle.project.messaging.outbox.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
@Service public class ReportRecoveryService {
	private final ReportJobRepository jobs;
	private final ReportRecoveryRepository commands;
	private final ReportRequestRepository requests;
	private final ReportAdmissionService admission;
	private final OutboxWriter outbox;
	private final TransactionTemplate transaction;
	public ReportRecoveryService(ReportJobRepository jobs,ReportRecoveryRepository commands,ReportRequestRepository requests,ReportAdmissionService admission,OutboxWriter outbox,PlatformTransactionManager manager){
		this.jobs=jobs;this.commands=commands;this.requests=requests;this.admission=admission;this.outbox=outbox;
		transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}
	public record RecoveryResult(UUID requestId,UUID commandId,int generation) { }
	public RecoveryResult recover(Long actor,UUID request,UUID command,String reason){
		commands.requireAdmin(actor);
		if(request==null||command==null||reason==null)throw invalid();
		String normalized=reason.replace("\r\n","\n").strip();
		if(normalized.isEmpty()||normalized.codePointCount(0,normalized.length())>1000)throw invalid();
		for(int i=0;i<normalized.length();i++)if(Character.isSurrogate(normalized.charAt(i))){if(!Character.isHighSurrogate(normalized.charAt(i))||i+1>=normalized.length()||!Character.isLowSurrogate(normalized.charAt(++i)))throw invalid();}
		try{return transaction.execute(status->{
			// job→request→GLOBAL→USER 순서. 첫 consistent read는 job 잠금 뒤에 둔다.
			var job=jobs.lockByRequest(request).orElseThrow(()->new ReportRequestException(ReportRequestException.Code.NOT_FOUND));
			var row=requests.lock(request);
			var previous=commands.find(command);if(previous.isPresent())return replay(previous.get(),actor,request,normalized);
			if(job.state()!=ReportJobStatus.FAILED||row.status()!=ReportRequestStatus.FAILED||job.generation()>=999999999||row.admissionSlotHeld())throw conflict();
			requests.requireUser(row.userId());admission.acquire(row.userId(),admission.lock(row.userId()));
			// 이전 FAILED에서 반납했던 슬롯을 새 관리 의도에 귀속시킨다.
			requests.holdSlot(request);jobs.reopen(job.id());
			var audit=new ReportRecoveryCommandEntity(command,request,actor,normalized,job.generation()+1);commands.insert(audit);
			outbox.append(outbox.event(requests.transition(request,ReportRequestStatus.ENQUEUED,null,null),ReportEventType.STATUS_CHANGED));
			return new RecoveryResult(request,command,audit.targetGeneration());
		});}catch(DuplicateKeyException race){return replay(commands.find(command).orElseThrow(()->race),actor,request,normalized);}
	}
	private RecoveryResult replay(ReportRecoveryCommandEntity audit,long actor,UUID request,String reason){if(!audit.requestId().equals(request)||audit.actorUserId()!=actor||!audit.reason().equals(reason))throw conflict();return new RecoveryResult(request,audit.id(),audit.targetGeneration());}
	private ReportRequestException conflict(){return new ReportRequestException(ReportRequestException.Code.CONFLICT);}
	private ReportRequestException invalid(){return new ReportRequestException(ReportRequestException.Code.INVALID);}
}
