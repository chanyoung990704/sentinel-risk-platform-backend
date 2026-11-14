package com.aivle.project.company.reportjob.entity;
import java.util.UUID;
public record ReportAttemptEntity(UUID id,UUID jobId,int generation,int attemptNo,String state,String errorCode) { }
