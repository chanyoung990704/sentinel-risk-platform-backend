package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.CompanyIdentity;
import java.time.LocalDateTime;
public interface WatchlistMember {
	Long getId();
	CompanyIdentity getCompany();
	String getNote();
	LocalDateTime getCreatedAt();
}
