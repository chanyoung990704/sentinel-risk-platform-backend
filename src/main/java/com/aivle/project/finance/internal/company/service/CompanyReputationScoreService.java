package com.aivle.project.finance.internal.company.service;

import com.aivle.project.finance.internal.company.keymetric.entity.CompanyKeyMetricEntity;
import com.aivle.project.finance.internal.company.keymetric.repository.CompanyKeyMetricRepository;
import com.aivle.project.finance.api.ExternalReputationScores;
import com.aivle.project.finance.internal.quarter.entity.QuartersEntity;
import com.aivle.project.finance.internal.quarter.repository.QuartersRepository;
import com.aivle.project.finance.internal.report.repository.CompanyReportMetricValuesRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 건강도(평판) 점수 동기화 서비스.
 */
@Service
@RequiredArgsConstructor
public class CompanyReputationScoreService implements com.aivle.project.finance.api.ReputationOperations {

	private final CompanyReportMetricValuesRepository companyReportMetricValuesRepository;
	private final QuartersRepository quartersRepository;
	private final CompanyKeyMetricRepository companyKeyMetricRepository;
	private final ExternalReputationScores externalScores;

	@Transactional
	public void syncExternalHealthScoreIfPresent(Long companyId, String stockCode) {
		Integer latestActualQuarterKey = companyReportMetricValuesRepository.findMaxActualQuarterKeyByStockCode(stockCode)
			.orElse(null);
		if (latestActualQuarterKey == null) {
			return;
		}

		QuartersEntity quarter = quartersRepository.findByQuarterKey(latestActualQuarterKey)
			.orElse(null);
		if (quarter == null) {
			return;
		}

		CompanyKeyMetricEntity keyMetric = companyKeyMetricRepository
			.findByCompanyIdAndQuarterId(companyId, quarter.getId())
			.orElse(null);
		if (keyMetric == null) {
			return;
		}

		BigDecimal averageScore = resolveLatestAverageScore(companyId, stockCode, true);
		if (averageScore == null) {
			return;
		}

		keyMetric.applyExternalHealthScore(averageScore);
	}

	@Transactional(readOnly = true)
	public BigDecimal resolveLatestAverageScore(Long companyId, String stockCode) {
		return resolveLatestAverageScore(companyId, stockCode, false);
	}

	private BigDecimal resolveLatestAverageScore(Long companyId, String stockCode, boolean fetchIfMissing) {
		return externalScores.latest(companyId,stockCode,fetchIfMissing).orElse(null);
	}
}
