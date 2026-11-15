package com.aivle.project.watchlist.api;
import java.util.List;
public interface WatchlistMemberships {
	List<WatchlistMember> findActiveByUserId(Long userId);
}
