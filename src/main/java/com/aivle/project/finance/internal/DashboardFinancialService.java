package com.aivle.project.finance.internal;
import com.aivle.project.finance.api.*;
import com.aivle.project.finance.internal.company.keymetric.entity.CompanyKeyMetricEntity;
import com.aivle.project.finance.internal.company.keymetric.repository.CompanyKeyMetricRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DashboardFinancialService implements DashboardFinancialQueries {
	private final CompanyKeyMetricRepository metrics;
	private KeyMetricSnapshot snapshot(CompanyKeyMetricEntity row) {
		var company=row.getCompany();var quarter=row.getQuarter();
		return new KeyMetricView(new CompanyView(company.getId(),company.getCorpName(),company.getCorpCode(),company.getStockCode()),
			new QuarterView(quarter.getId(),quarter.getYear(),quarter.getQuarter(),quarter.getQuarterKey()),row.getInternalHealthScore(),row.getRiskLevel());
	}
	public List<KeyMetricSnapshot> findByCompanyIdInAndQuarter_QuarterKey(List<Long> ids,int quarter) {
		return metrics.findByCompanyIdInAndQuarter_QuarterKey(ids,quarter).stream().map(this::snapshot).toList();
	}
	public List<KeyMetricSnapshot> findRiskRecordsByCompanyIds(List<Long> ids,int quarter,Pageable pageable) {
		return metrics.findRiskRecordsByCompanyIds(ids,quarter,pageable).stream().map(this::snapshot).toList();
	}
	public List<KeyMetricSnapshot> findByCompanyIdInAndQuarterKeyIn(List<Long> ids,List<Integer> quarters) {
		return metrics.findByCompanyIdInAndQuarterKeyIn(ids,quarters).stream().map(this::snapshot).toList();
	}
	public List<RiskHistoryValue> findRiskHistoryByCompanyIds(List<Long> ids,int quarter) {
		return metrics.findRiskHistoryByCompanyIds(ids,quarter).stream()
			.map(row -> new RiskHistoryValue(row.getCompanyId(),row.getQuarterKey(),row.getRiskLevel())).toList();
	}
}
