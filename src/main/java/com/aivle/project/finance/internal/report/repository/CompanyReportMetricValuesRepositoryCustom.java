package com.aivle.project.finance.internal.report.repository;

import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.api.MetricValueSampleProjection;
import com.aivle.project.finance.api.ReportMetricRowProjection;
import com.aivle.project.finance.api.ReportPredictMetricRowProjection;
import java.math.BigDecimal;
import java.util.List;

public interface CompanyReportMetricValuesRepositoryCustom {

	List<ReportMetricRowProjection> findLatestMetricsByStockCodeAndQuarterRange(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey
	);

	List<ReportMetricRowProjection> findLatestMetricsByStockCodeAndQuarterRangeAndMetricCodes(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey,
		List<String> metricCodes
	);

	List<ReportPredictMetricRowProjection> findLatestMetricsByStockCodeAndQuarterKeyAndType(
		String stockCode,
		int quarterKey,
		MetricValueType valueType
	);

	List<ReportPredictMetricRowProjection> findLatestMetricsByCompanyIdAndQuarterKeyAndType(
		Long companyId,
		int quarterKey,
		MetricValueType valueType
	);

	List<com.aivle.project.finance.api.CompanyOverviewMetricRowProjection> findLatestOverviewMetricsByCompanyQuarter(
		Long companyId,
		Long quarterId,
		MetricValueType valueType,
		String locale
	);

	List<com.aivle.project.finance.api.CompanyOverviewMetricRowProjection> findLatestOverviewMetricsByStockCodeAndQuarterRange(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey,
		String locale
	);

	List<BigDecimal> findRiskMetricValuesByCompanyQuarterAndVersion(
		Long companyId,
		Long quarterId,
		Long reportVersionId,
		MetricValueType valueType
	);

	List<MetricValueSampleProjection> findNonRiskActualMetricSamplesByQuarterId(
		Long quarterId,
		MetricValueType valueType
	);

	List<com.aivle.project.finance.internal.report.entity.CompanyReportMetricValuesEntity> findLatestActualValuesByCompanyAndQuarter(
		Long companyId,
		int quarterKey
	);

	java.util.Optional<Integer> findMaxActualQuarterKeyByStockCodes(List<String> stockCodes);
}
