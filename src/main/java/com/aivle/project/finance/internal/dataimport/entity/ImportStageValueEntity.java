package com.aivle.project.finance.internal.dataimport.entity;
import com.aivle.project.finance.api.CanonicalMetricCell;
import java.util.UUID;
public record ImportStageValueEntity(UUID snapshotId, CanonicalMetricCell cell) { }
