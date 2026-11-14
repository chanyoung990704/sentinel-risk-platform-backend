package com.aivle.project.company.reportjob.entity;
import java.util.UUID;
public record ReportArtifactEntity(UUID id,UUID requestId,UUID attemptId,long fileId,String storageKey,long fileSize,String sha256) { }
