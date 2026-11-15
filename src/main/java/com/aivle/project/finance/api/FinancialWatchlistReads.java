package com.aivle.project.finance.api;
import com.aivle.project.finance.api.WatchlistMetricValueProjection;

import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.api.RiskLevel;
import com.aivle.project.finance.api.WatchlistDashboardMetricRow;
import com.aivle.project.finance.api.WatchlistDashboardRiskRow;
import com.aivle.project.finance.api.WatchlistMetricAverageRow;
import com.aivle.project.finance.api.WatchlistMetricValueRow;
import java.util.Collection;
import java.util.List;

public interface FinancialWatchlistReads {

	List<WatchlistDashboardMetricRow> findDashboardMetrics(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	);

	List<WatchlistDashboardRiskRow> findDashboardRisks(
		java.util.Map<Long, Long> watchlists,
		Long quarterId,
		RiskLevel riskLevel
	);

	/**
	 * Service에서의 그룹화를 위해 year, quarter를 포함한 Projection 인터페이스를 유지하거나
	 * 전용 DTO를 사용해야 합니다. 여기서는 호환성을 위해 Projection 인터페이스를 반환합니다.
	 */
	List<WatchlistMetricValueProjection> findWatchlistMetricValues(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType
	);

	List<WatchlistMetricValueProjection> findWatchlistMetricValuesInRange(
		java.util.Map<Long, Long> watchlists,
		short fromYear,
		byte fromQuarter,
		short toYear,
		byte toQuarter,
		MetricValueType valueType
	);

	List<WatchlistMetricAverageRow> findWatchlistMetricAverages(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	);
}