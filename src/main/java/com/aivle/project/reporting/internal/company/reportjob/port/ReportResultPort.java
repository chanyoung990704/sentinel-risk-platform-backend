package com.aivle.project.reporting.internal.company.reportjob.port;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.api.CompletionDecision;
public interface ReportResultPort {CompletionDecision storeAndComplete(JobLease lease,GeneratedReport report);}
