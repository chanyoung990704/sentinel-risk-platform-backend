package com.aivle.project.finance.api;

import com.aivle.project.finance.api.MetricValueType;
import java.math.BigDecimal;

/**
 * 분기별 지표 항목 DTO.
 */
public record ReportMetricItemDto(
	String metricCode,
	String metricNameKo,
	BigDecimal metricValue,
	MetricValueType valueType
) {
}
