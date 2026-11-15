package com.aivle.project.watchlist.internal.repository;

import com.aivle.project.finance.api.RiskLevel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface WatchlistDashboardRiskProjection {
	Long getWatchlistId();
	Long getCompanyId();
	String getCorpName();
	BigDecimal getRiskScore();
	RiskLevel getRiskLevel();
	BigDecimal getRiskMetricsAvg();
	LocalDateTime getLastRefreshedAt();
}
