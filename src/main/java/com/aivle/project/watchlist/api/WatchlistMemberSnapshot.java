package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.CompanyView;
import com.aivle.project.finance.api.CompanyIdentity;
import java.time.LocalDateTime;
public record WatchlistMemberSnapshot(Long id, CompanyView company, String note, LocalDateTime createdAt) implements WatchlistMember {
	public Long getId() { return id; }
	public CompanyIdentity getCompany() { return company; }
	public String getNote() { return note; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
