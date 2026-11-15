package com.aivle.project.finance.api;
import java.util.Optional;
public interface FinancialReferenceQueries {
	Optional<QuarterIdentity> findByYearAndQuarter(short year, byte quarter);
	Optional<Integer> findMaxActualQuarterKeyByCompanyId(Long companyId);
	Optional<Integer> findMaxActualQuarterKeyByStockCode(String stockCode);
	Optional<Integer> findMaxActualQuarterKeyByStockCodes(java.util.List<String> stockCodes);
}
