package com.aivle.project.finance.api;
import java.math.BigDecimal;
public interface KeyMetricSnapshot {
	CompanyIdentity getCompany();
	QuarterIdentity getQuarter();
	BigDecimal getInternalHealthScore();
	CompanyKeyMetricRiskLevel getRiskLevel();
}
