package com.aivle.project.watchlist.internal.repository;

import java.math.BigDecimal;

public interface WatchlistDashboardMetricProjection {
	Long getWatchlistId();
	Long getCompanyId();
	String getCorpName();
	String getCorpCode();
	String getMetricCode();
	String getMetricNameKo();
	BigDecimal getMetricValue();
	BigDecimal getMarketAvg();
	Integer getMarketN();
}
