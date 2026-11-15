package com.aivle.project.reporting.internal.company.reportjob.entity;
import java.util.UUID;
public record ReportRecoveryCommandEntity(UUID id,UUID requestId,long actorUserId,String reason,int targetGeneration) { }
