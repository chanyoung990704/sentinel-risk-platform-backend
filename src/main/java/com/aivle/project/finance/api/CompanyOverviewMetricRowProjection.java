package com.aivle.project.finance.api;

import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.api.SignalColor;
import java.math.BigDecimal;

/**
 * 기업 개요 지표 조회 프로젝션.
 */
public interface CompanyOverviewMetricRowProjection {

	String getMetricCode();

	String getMetricNameKo();

	String getUnit();

	BigDecimal getMetricValue();

	MetricValueType getValueType();

	int getQuarterKey();

	SignalColor getSignalColor();

	String getDescription();

	String getInterpretation();

	String getActionHint();
}
