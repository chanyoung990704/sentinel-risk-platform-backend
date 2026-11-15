package com.aivle.project.reporting.internal.company.reportjob.entity;
import java.util.UUID;
public record ReportAttemptEntity(UUID id,UUID jobId,int generation,int attemptNo,String state,String errorCode) { }
