package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.WatchlistDashboardRiskRow;
import com.aivle.project.finance.api.WatchlistDashboardMetricRow;

import java.util.List;

public record WatchlistDashboardResponse(
	int year,
	int quarter,
	List<WatchlistDashboardMetricRow> metrics,
	List<WatchlistDashboardRiskRow> risks
) {
}
