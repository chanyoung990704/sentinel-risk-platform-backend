package com.aivle.project.finance.api;

import java.math.BigDecimal;

public record WatchlistMetricAverageRow(
	String metricCode,
	String metricNameKo,
	BigDecimal avgValue,
	Long sampleCompanyCount
) {
}
