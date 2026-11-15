package com.aivle.project.watchlist.internal.repository;

import java.math.BigDecimal;

public interface WatchlistMetricAverageProjection {
	String getMetricCode();
	String getMetricNameKo();
	BigDecimal getAvgValue();
	Long getSampleCompanyCount();
}
