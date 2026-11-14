package com.aivle.project.company.reportrequest.service;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.company.reportrequest.model.ReportRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service public class ReportAdmissionService {
	private final ReportRequestRepository repository;private final int globalLimit,userLimit;
	public ReportAdmissionService(ReportRequestRepository repository,@Value("${custom-report.admission.global-limit:1000}") int globalLimit,@Value("${custom-report.admission.user-limit:20}") int userLimit){this.repository=repository;this.globalLimit=globalLimit;this.userLimit=userLimit;}
	public record Capacity(int global,int user) { }
	public Capacity lock(long user){int global=repository.lockCounter("GLOBAL");repository.ensureUserCounter(user);return new Capacity(global,repository.lockCounter("USER:"+user));}
	public void acquire(long user,Capacity c){if(c.user()>=userLimit)throw new ReportRequestException(ReportRequestException.Code.USER_CAPACITY);if(c.global()>=globalLimit)throw new ReportRequestException(ReportRequestException.Code.GLOBAL_CAPACITY);repository.changeCounter("GLOBAL",1);repository.changeCounter("USER:"+user,1);}
	public void release(java.util.UUID request,long user){lock(user);if(repository.releaseSlot(request)){repository.changeCounter("GLOBAL",-1);repository.changeCounter("USER:"+user,-1);}}
}
