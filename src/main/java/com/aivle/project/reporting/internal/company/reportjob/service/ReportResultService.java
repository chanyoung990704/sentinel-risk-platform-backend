package com.aivle.project.reporting.internal.company.reportjob.service;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.api.CompletionDecision;
import com.aivle.project.reporting.internal.company.reportjob.port.ReportResultPort;
import org.springframework.stereotype.Service;
@Service public class ReportResultService implements ReportResultPort {
	private final com.aivle.project.reporting.internal.company.reportjob.adapter.LocalReportArtifactStore store;private final ReportCompletionService completion;
	public ReportResultService(com.aivle.project.reporting.internal.company.reportjob.adapter.LocalReportArtifactStore store,ReportCompletionService completion){this.store=store;this.completion=completion;}
	public CompletionDecision storeAndComplete(JobLease lease,GeneratedReport report){return completion.complete(lease,store.storeAttempt(lease,report));}
}
