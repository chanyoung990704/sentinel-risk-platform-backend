package com.aivle.project.finance.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 지표 설명 툴팁 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyOverviewTooltipDto {
	private String description;
	private String interpretation;
	private String actionHint;
}
