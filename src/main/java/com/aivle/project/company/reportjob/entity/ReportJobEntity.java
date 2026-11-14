package com.aivle.project.company.reportjob.entity;
import com.aivle.project.company.reportjob.model.ReportJobStatus;
import java.time.Instant;
import java.util.UUID;
public record ReportJobEntity(UUID id,UUID requestId,ReportJobStatus state,long version,int generation,int attemptNo,UUID attemptId,UUID leaseToken,Instant leaseUntil,Instant retryAt,String lastErrorCode,String owner) { }
