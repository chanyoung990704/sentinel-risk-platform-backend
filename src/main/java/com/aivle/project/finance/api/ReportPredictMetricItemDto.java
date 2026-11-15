package com.aivle.project.finance.api;

import java.math.BigDecimal;

/**
 * 예측 지표 항목 DTO.
 */
public record ReportPredictMetricItemDto(
	String metricCode,
	String metricNameKo,
	BigDecimal metricValue,
	int quarterKey
) {
}
