package com.aivle.project.finance.api;
import com.aivle.project.finance.api.CompanyOverviewMetricDto;
import com.aivle.project.finance.api.CompanyOverviewKeyMetricDto;
import com.aivle.project.finance.api.CompanyOverviewForecastDto;
import com.aivle.project.finance.api.CompanyInfoDto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업 개요 응답 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyOverviewResponseDto {
	private CompanyInfoDto company;
	private CompanyOverviewForecastDto forecast;
	private List<CompanyOverviewKeyMetricDto> keyMetrics;
	private List<CompanyOverviewMetricDto> signals;
	private String aiComment;
}
