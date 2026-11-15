package com.aivle.project.finance.api;
import java.math.BigDecimal;
public record KeyMetricView(CompanyView company, QuarterView quarter, BigDecimal internalHealthScore,
	CompanyKeyMetricRiskLevel riskLevel) implements KeyMetricSnapshot {
	public CompanyIdentity getCompany() { return company; }
	public QuarterIdentity getQuarter() { return quarter; }
	public BigDecimal getInternalHealthScore() { return internalHealthScore; }
	public CompanyKeyMetricRiskLevel getRiskLevel() { return riskLevel; }
}
