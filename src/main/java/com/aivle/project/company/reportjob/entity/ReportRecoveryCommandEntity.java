package com.aivle.project.company.reportjob.entity;
import java.util.UUID;
public record ReportRecoveryCommandEntity(UUID id,UUID requestId,long actorUserId,String reason,int targetGeneration) { }
