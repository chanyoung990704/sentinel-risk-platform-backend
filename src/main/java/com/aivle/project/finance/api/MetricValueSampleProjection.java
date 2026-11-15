package com.aivle.project.finance.api;

import java.math.BigDecimal;

/**
 * 지표 집계용 값 샘플 프로젝션.
 */
public interface MetricValueSampleProjection {
	Long getMetricId();

	BigDecimal getMetricValue();
}
