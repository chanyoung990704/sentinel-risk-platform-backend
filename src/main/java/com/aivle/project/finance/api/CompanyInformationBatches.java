package com.aivle.project.finance.api;

import java.util.List;

/** 입력 ID의 순서를 보존해 기업별 최신 ACTUAL 분기의 기본 정보를 일괄 반환한다. */
public interface CompanyInformationBatches {
	List<CompanyInfoDto> latest(List<Long> companyIds);
}
