package com.aivle.project.company.reportjob.port;
import com.aivle.project.company.reportjob.model.*;
public interface ReportResultPort {CompletionDecision storeAndComplete(JobLease lease,GeneratedReport report);}
