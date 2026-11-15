package com.aivle.project.watchlist.api;
import com.aivle.project.watchlist.api.WatchlistItem;

import java.util.List;

public record WatchlistResponse(
	List<WatchlistItem> items
) {
}
