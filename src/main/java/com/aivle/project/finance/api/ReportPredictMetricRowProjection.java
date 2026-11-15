package com.aivle.project.finance.api;

import com.aivle.project.finance.api.MetricValueType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 최신 예측 지표 조회 프로젝션.
 */
public interface ReportPredictMetricRowProjection {

	String getCorpName();

	String getStockCode();

	String getMetricCode();

	String getMetricNameKo();

	BigDecimal getMetricValue();

	MetricValueType getValueType();

	int getQuarterKey();

	int getVersionNo();

	LocalDateTime getGeneratedAt();

	Long getPdfFileId();

	String getPdfFileName();

	String getPdfContentType();
}
