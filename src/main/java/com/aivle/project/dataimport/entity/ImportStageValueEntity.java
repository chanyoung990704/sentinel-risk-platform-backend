package com.aivle.project.dataimport.entity;
import com.aivle.project.dataimport.model.CanonicalMetricCell;
import java.util.UUID;
public record ImportStageValueEntity(UUID snapshotId, CanonicalMetricCell cell) { }
