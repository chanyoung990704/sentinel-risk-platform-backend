package com.aivle.project.finance.internal.metricaverage.service;

/**
 * 분기 단위 metric_averages 저장 결과.
 */
public record QuarterMetricAverageSaveResult(
	int insertedCount,
	int skippedCount
) {
}
