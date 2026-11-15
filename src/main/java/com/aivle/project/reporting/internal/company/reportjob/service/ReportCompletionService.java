package com.aivle.project.reporting.internal.company.reportjob.service;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.api.ReportJobStatus;
import com.aivle.project.reporting.api.CompletionDecision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.aivle.project.reporting.internal.company.reportjob.repository.*;
import com.aivle.project.reporting.internal.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportAdmissionService;
import com.aivle.project.reporting.api.ReportRequestStatus;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.aivle.project.reporting.api.ReportEventType;
@Service public class ReportCompletionService {
	private final ReportJobRepository jobs;private final ReportArtifactRepository artifacts;private final ReportRequestRepository requests;private final ReportAdmissionService admission;private final OutboxWriter outbox;private final ReportClock clock;private final LocalReportArtifactStore store;
	private final com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties properties;
	public ReportCompletionService(ReportJobRepository jobs,ReportArtifactRepository artifacts,ReportRequestRepository requests,ReportAdmissionService admission,OutboxWriter outbox,ReportClock clock,LocalReportArtifactStore store,com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties properties){this.jobs=jobs;this.artifacts=artifacts;this.requests=requests;this.admission=admission;this.outbox=outbox;this.clock=clock;this.store=store;this.properties=properties;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public CompletionDecision complete(JobLease lease,StoredReport report){
		if(!lease.requestId().equals(report.requestId())||lease.generation()!=report.generation()||!lease.attemptId().equals(report.attemptId())||!LocalReportArtifactStore.key(lease).equals(report.key())||report.size()<1||report.size()>10*1024*1024||!report.sha256().matches("[0-9a-f]{64}"))throw new ReportGenerationFailure("ARTIFACT_IDENTITY",false);
		var job=jobs.lock(lease.jobId());var now=clock.now();
		if(job.state()==ReportJobStatus.SUCCEEDED&&job.version()==lease.version()&&job.generation()==lease.generation()&&java.util.Objects.equals(job.leaseToken(),lease.leaseToken())&&artifacts.find(lease.requestId()).filter(report::equals).isPresent())return CompletionDecision.DUPLICATE;
		if(!ReportJobClaimService.owns(job,lease,now))return CompletionDecision.STALE;
		var deadline=jobs.attemptStartedAt(lease.attemptId()).plusMillis(properties.getAttemptTimeoutMillis());
		if(!now.isBefore(deadline))return CompletionDecision.STALE;
		var request=requests.lock(lease.requestId());artifacts.insert(report,store.root().resolve(report.key()).toString());jobs.finishAttempt(lease.attemptId(),"SUCCEEDED",null,now);jobs.finish(job.id(),"SUCCEEDED",null,null,now);admission.release(request.id(),request.userId());outbox.append(outbox.event(requests.transition(request.id(),ReportRequestStatus.SUCCEEDED,null,null),ReportEventType.STATUS_CHANGED));
		// 마지막 쓰기까지 기다린 뒤 다시 검사한다. 만료됐다면 슬롯/파일 메타/성공 이벤트도 함께 되돌린다.
		var finalTime=clock.now();
		if(!ReportJobClaimService.owns(job,lease,finalTime)||!finalTime.isBefore(deadline)){
			org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			return CompletionDecision.STALE;
		}
		return CompletionDecision.COMPLETED;
	}
}
