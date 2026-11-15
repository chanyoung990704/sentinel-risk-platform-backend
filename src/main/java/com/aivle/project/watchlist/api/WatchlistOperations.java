package com.aivle.project.watchlist.api;
import com.aivle.project.finance.api.RiskLevel;
import com.aivle.project.watchlist.api.WatchlistDashboardResponse;
import com.aivle.project.watchlist.api.WatchlistMetricAveragesResponse;
import com.aivle.project.watchlist.api.WatchlistMetricValuesResponse;
import com.aivle.project.watchlist.api.WatchlistResponse;
import java.util.List;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface WatchlistOperations {
	void addWatchlist(Long userId, Long companyId, String note);
	void removeWatchlist(Long userId, Long companyId);
	WatchlistResponse getWatchlist(Long userId);
	List<com.aivle.project.finance.api.CompanyInfoDto> getWatchlistCompanies(Long userId);
	WatchlistDashboardResponse getDashboard(Long userId, int year, int quarter, List<String> metricCodes, RiskLevel riskLevel);
	WatchlistMetricAveragesResponse getWatchlistMetricAverages(Long userId, int year, int quarter, List<String> metricCodes);
	WatchlistMetricValuesResponse getWatchlistMetricValuesByQuarter(Long userId, int year, int quarter);
	WatchlistMetricValuesResponse getWatchlistMetricValuesByQuarterRange(Long userId, int fromYear, int fromQuarter, int toYear, int toQuarter);
}
