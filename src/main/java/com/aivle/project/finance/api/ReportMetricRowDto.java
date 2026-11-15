package com.aivle.project.finance.api;

import com.aivle.project.finance.api.MetricValueType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 보고서 지표 응답 DTO.
 */
public record ReportMetricRowDto(
	String corpName,
	String stockCode,
	String metricCode,
	String metricNameKo,
	BigDecimal metricValue,
	MetricValueType valueType,
	int quarterKey,
	int versionNo,
	LocalDateTime generatedAt
) {
}
