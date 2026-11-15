package com.aivle.project.watchlist.internal;

import com.aivle.project.finance.api.CompanyDirectory;
import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.finance.api.CompanyView;
import com.aivle.project.watchlist.api.WatchlistMember;
import com.aivle.project.watchlist.api.WatchlistMemberSnapshot;
import com.aivle.project.watchlist.api.WatchlistMemberships;
import com.aivle.project.watchlist.internal.repository.CompanyWatchlistRepository;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관심기업 읽기는 쓰기·AI 갱신 흐름과 독립적으로 조립한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class WatchlistMembershipService implements WatchlistMemberships {

	private final CompanyWatchlistRepository watchlists;
	private final CompanyDirectory companies;

	public List<WatchlistMember> findActiveByUserId(Long userId) {
		var rows = watchlists.findActiveByUserId(userId);
		var references = companies.findAllById(rows.stream().map(row -> row.getCompanyId()).toList())
			.stream().collect(Collectors.toMap(CompanyIdentity::getId, Function.identity()));
		return rows.stream().map(row -> {
			var company = references.get(row.getCompanyId());
			if (company == null) {
				throw new IllegalStateException("관심기업이 참조하는 기업이 없습니다: " + row.getCompanyId());
			}
			return (WatchlistMember) new WatchlistMemberSnapshot(row.getId(), CompanyView.copyOf(company),
				row.getNote(), row.getCreatedAt());
		}).toList();
	}
}
