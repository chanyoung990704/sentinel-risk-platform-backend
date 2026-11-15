package com.aivle.project.finance.internal;
import com.aivle.project.finance.api.*;
import com.aivle.project.finance.internal.quarter.repository.QuartersRepository;
import com.aivle.project.finance.internal.report.repository.CompanyReportMetricValuesRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FinancialReferenceService implements FinancialReferenceQueries {
	private final QuartersRepository quarters;
	private final CompanyReportMetricValuesRepository values;
	public Optional<QuarterIdentity> findByYearAndQuarter(short year, byte quarter) {
		return quarters.findByYearAndQuarter(year,quarter).map(q -> new QuarterView(q.getId(), q.getYear(), q.getQuarter(), q.getQuarterKey()));
	}
	public Optional<Integer> findMaxActualQuarterKeyByCompanyId(Long id) { return values.findMaxActualQuarterKeyByCompanyId(id); }
	public Optional<Integer> findMaxActualQuarterKeyByStockCode(String code) { return values.findMaxActualQuarterKeyByStockCode(code); }
	public Optional<Integer> findMaxActualQuarterKeyByStockCodes(java.util.List<String> codes) { return values.findMaxActualQuarterKeyByStockCodes(codes); }
}
