package com.aivle.project.reporting.internal.company.reportjob.service;
import com.aivle.project.reporting.internal.company.reportjob.entity.ReportAttemptEntity;
import com.aivle.project.reporting.internal.company.reportjob.repository.ReportJobRepository;
import java.util.*;
import org.springframework.stereotype.Service;
/** 운영 진단용 내부 조회. 원문이나 외부 오류 본문은 포함하지 않는다. */
@Service public class ReportAttemptService {
	private final ReportJobRepository jobs;public ReportAttemptService(ReportJobRepository jobs){this.jobs=jobs;}
	public List<ReportAttemptEntity> history(UUID request){return jobs.history(request);}
}
