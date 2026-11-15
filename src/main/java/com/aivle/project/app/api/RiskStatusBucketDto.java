package com.aivle.project.app.api;

/**
 * 분기별 위험 상태 버킷 DTO.
 */
public record RiskStatusBucketDto(
	String quarter,
	DataType dataType,
	int NORMAL,
	int CAUTION,
	int RISK
) {
	public enum DataType {
		ACTUAL,
		FORECAST
	}
}
