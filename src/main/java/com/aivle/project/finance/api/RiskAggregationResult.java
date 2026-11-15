package com.aivle.project.finance.api;

import com.aivle.project.finance.api.RiskLevel;
import java.math.BigDecimal;

/**
 * 위험도 계산 결과 DTO.
 */
public record RiskAggregationResult(
	Long companyId,
	Long quarterId,
	Long reportVersionId,
	BigDecimal riskScore,
	RiskLevel riskLevel,
	int riskMetricsCount,
	BigDecimal riskMetricsAvg
) {
}
