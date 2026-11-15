package com.aivle.project.reporting.api;
import com.aivle.project.reporting.api.ReportRequestStatus;
import java.util.UUID;
public record AcceptedReportRequest(UUID requestId,ReportRequestStatus status,String statusUrl) { }
