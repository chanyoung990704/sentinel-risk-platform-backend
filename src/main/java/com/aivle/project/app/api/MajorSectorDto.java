package com.aivle.project.app.api;

/**
 * 주요 섹터 요약 DTO.
 */
public record MajorSectorDto(
	String name,
	int riskCompanyCount,
	int totalCompanyCount,
	double riskRatio,
	double riskIndex
) {
}
