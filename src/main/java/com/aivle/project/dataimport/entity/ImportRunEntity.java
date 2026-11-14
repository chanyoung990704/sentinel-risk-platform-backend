package com.aivle.project.dataimport.entity;
import java.util.UUID;
public record ImportRunEntity(UUID id, UUID snapshotId, String state, int retryCount) { }
