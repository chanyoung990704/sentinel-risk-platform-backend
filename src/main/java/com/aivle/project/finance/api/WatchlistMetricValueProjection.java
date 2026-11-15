package com.aivle.project.finance.api;

import java.math.BigDecimal;

public interface WatchlistMetricValueProjection {
	Long getWatchlistId();
	Long getCompanyId();
	String getCorpName();
	String getCorpCode();
	String getMetricCode();
	String getMetricNameKo();
	BigDecimal getMetricValue();
	Integer getYear();
	Integer getQuarter();
}
