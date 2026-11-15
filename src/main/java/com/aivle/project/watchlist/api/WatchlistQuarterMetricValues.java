package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.WatchlistMetricValueRow;

import java.util.List;

public record WatchlistQuarterMetricValues(
	int year,
	int quarter,
	List<WatchlistMetricValueRow> items
) {
}
