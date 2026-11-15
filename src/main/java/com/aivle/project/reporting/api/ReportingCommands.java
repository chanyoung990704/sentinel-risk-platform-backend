package com.aivle.project.reporting.api;
public interface ReportingCommands {
	AcceptedReportRequest accept(Long userId,Long companyId,String key,CustomReportCommand command);
}
