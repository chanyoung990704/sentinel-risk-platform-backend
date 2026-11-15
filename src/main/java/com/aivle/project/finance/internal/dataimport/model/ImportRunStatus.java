package com.aivle.project.finance.internal.dataimport.model;

import java.util.UUID;

public record ImportRunStatus(UUID runId, UUID snapshotId, String runState, String snapshotState,
	long expectedCells, long stagedCells, int totalChunks, int committedChunks, int retryCount) { }
