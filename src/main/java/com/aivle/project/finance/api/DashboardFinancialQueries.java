package com.aivle.project.finance.api;
import java.util.List;
import org.springframework.data.domain.Pageable;
public interface DashboardFinancialQueries {
	List<KeyMetricSnapshot> findByCompanyIdInAndQuarter_QuarterKey(List<Long> ids, int quarter);
	List<KeyMetricSnapshot> findRiskRecordsByCompanyIds(List<Long> ids, int quarter, Pageable pageable);
	List<KeyMetricSnapshot> findByCompanyIdInAndQuarterKeyIn(List<Long> ids, List<Integer> quarters);
	List<RiskHistoryValue> findRiskHistoryByCompanyIds(List<Long> ids, int quarter);
}
