package com.aivle.project.finance.api;
public record RiskHistoryValue(Long companyId, Integer quarterKey, CompanyKeyMetricRiskLevel riskLevel) {
	public Long getCompanyId() { return companyId; }
	public Integer getQuarterKey() { return quarterKey; }
	public CompanyKeyMetricRiskLevel getRiskLevel() { return riskLevel; }
}
