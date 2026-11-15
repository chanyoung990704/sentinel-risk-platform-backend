package com.aivle.project.finance.internal.dataimport.entity;
import java.util.UUID;
public record ImportRunEntity(UUID id, UUID snapshotId, String state, int retryCount) { }
