package com.aivle.project.app.api;

/**
 * 위험 상태 분포 비율 DTO.
 */
public record RiskStatusDistributionPercentDto(
	double NORMAL,
	double CAUTION,
	double RISK
) {
}
