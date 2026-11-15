package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.WatchlistMetricAverageRow;

import java.util.List;

public record WatchlistMetricAveragesResponse(
	int year,
	int quarter,
	List<WatchlistMetricAverageRow> metrics
) {
}
