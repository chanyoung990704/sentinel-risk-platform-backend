package com.aivle.project.reporting.internal.company.reportjob.port;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import java.util.UUID;
public interface ReportGenerationPort {GeneratedReport generate(ReportSourceData source,CustomReportInput input,UUID requestId);}
