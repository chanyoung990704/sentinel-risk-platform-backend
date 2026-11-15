package com.aivle.project.finance.api;
import com.aivle.project.finance.api.CompanyOverviewResponseDto;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface CompanyOverviewQueries {
	CompanyOverviewResponseDto getOverview(Long companyId, String quarterKey);
}
