package com.aivle.project.company.reportrequest.model;
import java.util.UUID;
public record AcceptedReportRequest(UUID requestId,ReportRequestStatus status,String statusUrl) { }
