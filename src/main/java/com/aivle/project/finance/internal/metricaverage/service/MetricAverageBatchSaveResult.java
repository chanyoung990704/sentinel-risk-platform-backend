package com.aivle.project.finance.internal.metricaverage.service;

/**
 * 전체 분기 metric_averages 저장 결과.
 */
public record MetricAverageBatchSaveResult(
	int processedQuarterCount,
	int insertedCount,
	int skippedCount,
	String triggerType,
	String executionId
) {
}
