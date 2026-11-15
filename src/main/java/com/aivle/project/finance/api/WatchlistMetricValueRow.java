package com.aivle.project.finance.api;

import java.math.BigDecimal;

public record WatchlistMetricValueRow(
	Long watchlistId,
	Long companyId,
	String corpName,
	String corpCode,
	String metricCode,
	String metricNameKo,
	BigDecimal metricValue
) {
}
