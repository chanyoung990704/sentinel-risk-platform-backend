package com.aivle.project.finance.api;
import com.aivle.project.finance.api.CompanyOverviewDataType;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분기별 지표 값 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyOverviewDataPointDto {
	private String quarter;
	private Double value;
	private CompanyOverviewDataType type;
}
