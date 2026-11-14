package com.aivle.project.company.reportjob.service;
import com.aivle.project.company.reportjob.model.JobLease;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.aivle.project.company.reportjob.config.ReportWorkerProperties;
import com.aivle.project.company.reportjob.repository.ReportJobRepository;
import com.aivle.project.company.reportjob.entity.ReportJobEntity;
import com.aivle.project.company.reportjob.model.ReportJobStatus;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.company.reportrequest.service.ReportAdmissionService;
import com.aivle.project.company.reportrequest.model.ReportRequestStatus;
import com.aivle.project.messaging.outbox.*;
import java.util.*;
@Service public class ReportJobClaimService {
	private final ReportJobRepository jobs;private final ReportRequestRepository requests;private final ReportAdmissionService admission;private final OutboxWriter outbox;private final ReportWorkerProperties properties;private final ReportClock clock;
	public ReportJobClaimService(ReportJobRepository jobs,ReportRequestRepository requests,ReportAdmissionService admission,OutboxWriter outbox,ReportWorkerProperties properties,ReportClock clock){this.jobs=jobs;this.requests=requests;this.admission=admission;this.outbox=outbox;this.properties=properties;this.clock=clock;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public List<JobLease> claimReady(int limit,String owner,Instant now){
		if(limit<=0)return List.of();if(owner==null||owner.isBlank()||owner.length()>128)throw new IllegalArgumentException("Invalid worker owner");
		var result=new ArrayList<JobLease>();for(var j:jobs.ready(Math.min(properties.getWorkers(),limit),fresh(now))){
			requests.lock(j.requestId());Instant claimedAt=fresh(now);
			var lease=new JobLease(j.id(),j.requestId(),UUID.randomUUID(),j.version()+1,j.generation(),j.attemptNo()+1,claimedAt.plusSeconds(properties.getLeaseSeconds()),UUID.randomUUID());
			jobs.claim(lease,owner,claimedAt);publish(j.requestId(),ReportRequestStatus.PROCESSING,null,null);result.add(lease);
		}return List.copyOf(result);
	}
	public static boolean owns(ReportJobEntity j,JobLease l,Instant now){return j.state()==ReportJobStatus.RUNNING&&j.requestId().equals(l.requestId())&&j.version()==l.version()&&j.generation()==l.generation()&&Objects.equals(j.leaseToken(),l.leaseToken())&&Objects.equals(j.attemptId(),l.attemptId())&&j.leaseUntil()!=null&&j.leaseUntil().isAfter(now);}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public boolean heartbeat(JobLease lease,Instant now){var j=jobs.lock(lease.jobId());Instant current=fresh(now);if(!owns(j,lease,current))return false;jobs.heartbeat(j.id(),current.plusSeconds(properties.getLeaseSeconds()));return true;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public boolean failOrRetry(JobLease lease,String code,boolean retryable,Instant now){var j=jobs.lock(lease.jobId());Instant current=fresh(now);if(!owns(j,lease,current))return false;end(j,code,retryable,current,"FAILED");return true;}
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public int recover(Instant now){var expired=jobs.expired(fresh(now));for(var j:expired)end(j,"LEASE_EXPIRED",true,fresh(now),"LEASE_EXPIRED");return expired.size();}
	/** 명시적 시험 시각은 허용하되 잠금 대기 전의 과거 표본으로 되돌리지 않는다. */
	private Instant fresh(Instant hint){Instant database=clock.now();return hint.isAfter(database)?hint:database;}
	private void end(ReportJobEntity j,String code,boolean retryable,Instant now,String attemptState){
		String safe=code!=null&&code.matches("[A-Z0-9_]{1,64}")?code:"INTERNAL_ERROR";var r=requests.lock(j.requestId());boolean retry=retryable&&j.attemptNo()<properties.getMaxAttempts();
		int jitter=properties.getRetryJitterMs();long extra=jitter==0?0:Math.floorMod(Objects.hash(j.id(),j.generation(),j.attemptNo(),properties.getJitterSeed()),(long)jitter+1);
		Instant due=retry?now.plusMillis((j.attemptNo()==1?1000:5000)+extra):null;
		jobs.finishAttempt(j.attemptId(),attemptState,safe,now);jobs.finish(j.id(),retry?"RETRY_WAIT":"FAILED",safe,due,now);
		if(!retry)admission.release(r.id(),r.userId());publish(r.id(),retry?ReportRequestStatus.RETRY_WAIT:ReportRequestStatus.FAILED,safe,due);
	}
	private void publish(UUID id,ReportRequestStatus state,String code,Instant retry){outbox.append(outbox.event(requests.transition(id,state,code,retry),ReportEventType.STATUS_CHANGED));}
}
