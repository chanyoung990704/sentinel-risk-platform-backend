package com.aivle.project.finance.api;

import com.aivle.project.finance.api.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WatchlistDashboardRiskRow(
	Long watchlistId,
	Long companyId,
	String corpName,
	BigDecimal riskScore,
	RiskLevel riskLevel,
	BigDecimal riskMetricsAvg,
	LocalDateTime lastRefreshedAt
) {
}
