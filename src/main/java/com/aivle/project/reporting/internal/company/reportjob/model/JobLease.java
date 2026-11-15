package com.aivle.project.reporting.internal.company.reportjob.model;
import java.time.Instant;
import java.util.UUID;
/** attemptId도 고정해 옛 worker가 새 시도의 파일 경로를 사용하지 못하게 한다. */
public record JobLease(UUID jobId,UUID requestId,UUID leaseToken,long version,int generation,int attemptNo,Instant leaseUntil,UUID attemptId) { }
