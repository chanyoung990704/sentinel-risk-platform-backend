package com.aivle.project.watchlist.api;
import com.aivle.project.watchlist.api.WatchlistQuarterMetricValues;

import java.util.List;

public record WatchlistMetricValuesResponse(
	List<WatchlistQuarterMetricValues> quarters
) {
}
