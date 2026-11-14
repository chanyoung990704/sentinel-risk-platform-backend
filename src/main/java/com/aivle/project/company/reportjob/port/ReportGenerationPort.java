package com.aivle.project.company.reportjob.port;
import com.aivle.project.company.reportjob.model.*;
import java.util.UUID;
public interface ReportGenerationPort {GeneratedReport generate(ReportSourceData source,CustomReportInput input,UUID requestId);}
