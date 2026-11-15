package com.aivle.project.finance.api;
public interface CompanyInformation {
	CompanyInfoDto getCompanyInfo(Long companyId, String quarterKey);
	CompanyInfoDto getCompanyInfo(Long companyId, Integer quarterKey);
}
