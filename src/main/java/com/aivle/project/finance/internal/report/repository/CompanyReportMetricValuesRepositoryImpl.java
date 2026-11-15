package com.aivle.project.finance.internal.report.repository;

import com.aivle.project.finance.internal.dataimport.repository.SnapshotQueryScope;

import com.aivle.project.finance.internal.company.entity.QCompaniesEntity;

import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.internal.metric.entity.QMetricDescriptionEntity;
import com.aivle.project.finance.internal.metric.entity.QMetricsEntity;
import com.aivle.project.finance.internal.quarter.entity.QQuartersEntity;
import com.aivle.project.finance.api.CompanyOverviewMetricRowProjection;
import com.aivle.project.finance.api.MetricValueSampleProjection;
import com.aivle.project.finance.api.ReportMetricRowProjection;
import com.aivle.project.finance.api.ReportPredictMetricRowProjection;
import com.aivle.project.finance.internal.report.entity.CompanyReportMetricValuesEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportMetricValuesEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportVersionsEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportsEntity;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompanyReportMetricValuesRepositoryImpl implements CompanyReportMetricValuesRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<ReportMetricRowProjection> findLatestMetricsByStockCodeAndQuarterRange(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(ReportMetricRowDto.class,
				c.corpName,
				c.stockCode,
				m.metricCode,
				m.metricNameKo,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				rv.versionNo,
				rv.generatedAt
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			.where(
				SnapshotQueryScope.forValue(rv, v.valueType),
				c.stockCode.eq(stockCode),
				q.quarterKey.between(fromQuarterKey, toQuarterKey),
				rv.versionNo.eq(
					JPAExpressions.select(rv2.versionNo.max())
						.from(rv2)
						.where(rv2.companyReport.eq(cr),
							SnapshotQueryScope.forValue(rv2, v.valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(rv2),
									v2.valueType.eq(MetricValueType.ACTUAL),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(q.quarterKey.asc(), m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (ReportMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<ReportMetricRowProjection> findLatestMetricsByStockCodeAndQuarterRangeAndMetricCodes(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey,
		List<String> metricCodes
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(ReportMetricRowDto.class,
				c.corpName,
				c.stockCode,
				m.metricCode,
				m.metricNameKo,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				rv.versionNo,
				rv.generatedAt
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			.where(
				SnapshotQueryScope.forValue(rv, v.valueType),
				c.stockCode.eq(stockCode),
				q.quarterKey.between(fromQuarterKey, toQuarterKey),
				m.metricCode.in(metricCodes),
				rv.versionNo.eq(
					JPAExpressions.select(rv2.versionNo.max())
						.from(rv2)
						.where(rv2.companyReport.eq(cr),
							SnapshotQueryScope.forValue(rv2, v.valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(rv2),
									v2.valueType.eq(MetricValueType.ACTUAL),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(q.quarterKey.asc(), m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (ReportMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<ReportPredictMetricRowProjection> findLatestMetricsByStockCodeAndQuarterKeyAndType(
		String stockCode,
		int quarterKey,
		MetricValueType valueType
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(ReportPredictMetricRowDto.class,
				c.corpName,
				c.stockCode,
				m.metricCode,
				m.metricNameKo,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				rv.versionNo,
				rv.generatedAt,
				rv.pdfFileId,
				com.querydsl.core.types.dsl.Expressions.nullExpression(String.class),
				com.querydsl.core.types.dsl.Expressions.nullExpression(String.class)
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			
			.where(
				SnapshotQueryScope.forType(rv, valueType),
				c.stockCode.eq(stockCode),
				cr.quarter.quarterKey.eq(quarterKey),
				v.valueType.eq(valueType),
				rv.versionNo.eq(
					JPAExpressions.select(rv2.versionNo.max())
						.from(rv2)
						.where(rv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(rv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(rv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (ReportPredictMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<ReportPredictMetricRowProjection> findLatestMetricsByCompanyIdAndQuarterKeyAndType(
		Long companyId,
		int quarterKey,
		MetricValueType valueType
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(ReportPredictMetricRowDto.class,
				c.corpName,
				c.stockCode,
				m.metricCode,
				m.metricNameKo,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				rv.versionNo,
				rv.generatedAt,
				rv.pdfFileId,
				com.querydsl.core.types.dsl.Expressions.nullExpression(String.class),
				com.querydsl.core.types.dsl.Expressions.nullExpression(String.class)
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			
			.where(
				SnapshotQueryScope.forType(rv, valueType),
				c.id.eq(companyId),
				cr.quarter.quarterKey.eq(quarterKey),
				v.valueType.eq(valueType),
				rv.versionNo.eq(
					JPAExpressions.select(rv2.versionNo.max())
						.from(rv2)
						.where(rv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(rv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(rv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (ReportPredictMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<CompanyOverviewMetricRowProjection> findLatestOverviewMetricsByCompanyQuarter(
		Long companyId,
		Long quarterId,
		MetricValueType valueType,
		String locale
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QMetricDescriptionEntity md = QMetricDescriptionEntity.metricDescriptionEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");
		QMetricsEntity m2 = new QMetricsEntity("m2");
		QCompanyReportVersionsEntity rv3 = new QCompanyReportVersionsEntity("rv3");
		QCompanyReportMetricValuesEntity v3 = new QCompanyReportMetricValuesEntity("v3");
		QMetricsEntity m3 = new QMetricsEntity("m3");

		var latestRiskVersion = JPAExpressions.select(rv2.versionNo.max())
			.from(rv2)
			.where(
				rv2.companyReport.eq(cr),
				SnapshotQueryScope.forType(rv2, valueType),
				JPAExpressions.selectOne()
					.from(v2)
					.join(v2.metric, m2)
					.where(
						v2.reportVersion.eq(rv2),
						v2.valueType.eq(valueType),
						v2.metricValue.isNotNull(),
						m2.isRiskIndicator.isTrue()
					)
					.exists()
			);

		var latestNonRiskVersion = JPAExpressions.select(rv3.versionNo.max())
			.from(rv3)
			.where(
				rv3.companyReport.eq(cr),
				SnapshotQueryScope.forType(rv3, valueType),
				JPAExpressions.selectOne()
					.from(v3)
					.join(v3.metric, m3)
					.where(
						v3.reportVersion.eq(rv3),
						v3.valueType.eq(valueType),
						v3.metricValue.isNotNull(),
						m3.isRiskIndicator.isFalse()
					)
					.exists()
			);

		return queryFactory
			.select(Projections.constructor(CompanyOverviewMetricRowDto.class,
				m.metricCode,
				m.metricNameKo,
				m.unit,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				v.signalColor,
				md.description,
				md.interpretation,
				md.actionHint
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			.leftJoin(md).on(md.metric.eq(m).and(md.locale.eq(locale)))
			.where(
				SnapshotQueryScope.forType(rv, valueType),
				c.id.eq(companyId),
				q.id.eq(quarterId),
				v.valueType.eq(valueType),
				v.metricValue.isNotNull(),
				m.isRiskIndicator.isTrue().and(rv.versionNo.eq(latestRiskVersion))
					.or(m.isRiskIndicator.isFalse().and(rv.versionNo.eq(latestNonRiskVersion)))
			)
			.orderBy(m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (CompanyOverviewMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<CompanyOverviewMetricRowProjection> findLatestOverviewMetricsByStockCodeAndQuarterRange(
		String stockCode,
		int fromQuarterKey,
		int toQuarterKey,
		String locale
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QMetricDescriptionEntity md = QMetricDescriptionEntity.metricDescriptionEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");
		QMetricsEntity m2 = new QMetricsEntity("m2");

		// 현재 non-NULL 행도 유효 후보이므로, 같은 분류의 더 높은 유효 버전만 없으면 최신이다.
		var newerEligibleVersion = JPAExpressions.selectOne()
			.from(rv2)
			.where(
				rv2.companyReport.eq(cr),
				rv2.versionNo.gt(rv.versionNo),
				SnapshotQueryScope.forValue(rv2, v.valueType),
				JPAExpressions.selectOne()
					.from(v2)
					.join(v2.metric, m2)
					.where(
						v2.reportVersion.eq(rv2),
						v2.metricValue.isNotNull(),
						m2.isRiskIndicator.eq(m.isRiskIndicator)
					)
					.exists()
			);

		return queryFactory
			.select(Projections.constructor(CompanyOverviewMetricRowDto.class,
				m.metricCode,
				m.metricNameKo,
				m.unit,
				v.metricValue,
				v.valueType,
				q.quarterKey,
				v.signalColor,
				md.description,
				md.interpretation,
				md.actionHint
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m)
			.join(v.quarter, q)
			.leftJoin(md).on(md.metric.eq(m).and(md.locale.eq(locale)))
			.where(
				SnapshotQueryScope.forValue(rv, v.valueType),
				c.stockCode.eq(stockCode),
				q.quarterKey.between(fromQuarterKey, toQuarterKey),
				v.metricValue.isNotNull(),
				m.isRiskIndicator.isTrue().or(m.isRiskIndicator.isFalse()),
				newerEligibleVersion.notExists()
			)
			.orderBy(q.quarterKey.asc(), m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (CompanyOverviewMetricRowProjection) dto)
			.toList();
	}

	@Override
	public List<BigDecimal> findRiskMetricValuesByCompanyQuarterAndVersion(
		Long companyId,
		Long quarterId,
		Long reportVersionId,
		MetricValueType valueType
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;

		return queryFactory
			.select(v.metricValue)
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(v.metric, m)
			.where(
				SnapshotQueryScope.fixedVersion(rv),
				cr.company.id.eq(companyId),
				cr.quarter.id.eq(quarterId),
				rv.id.eq(reportVersionId),
				v.quarter.id.eq(quarterId),
				v.valueType.eq(valueType),
				m.isRiskIndicator.isTrue(),
				v.metricValue.isNotNull()
			)
			.fetch();
	}

	@Override
	public List<MetricValueSampleProjection> findNonRiskActualMetricSamplesByQuarterId(
		Long quarterId,
		MetricValueType valueType
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(MetricValueSampleDto.class,
				m.id,
				v.metricValue
			))
			.from(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(v.metric, m)
			.where(
				SnapshotQueryScope.forType(rv, valueType),
				cr.quarter.id.eq(quarterId),
				v.quarter.id.eq(quarterId),
				v.valueType.eq(valueType),
				m.isRiskIndicator.isFalse(),
				v.metricValue.isNotNull(),
				rv.versionNo.eq(
					JPAExpressions.select(rv2.versionNo.max())
						.from(rv2)
						.where(rv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(rv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(rv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.fetch()
			.stream()
			.map(dto -> (MetricValueSampleProjection) dto)
			.toList();
	}

	@Override
	public List<CompanyReportMetricValuesEntity> findLatestActualValuesByCompanyAndQuarter(
		Long companyId,
		int quarterKey
	) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;

		QCompanyReportVersionsEntity rv2 = new QCompanyReportVersionsEntity("rv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");
		QMetricsEntity m2 = new QMetricsEntity("m2");
		QCompanyReportVersionsEntity rv3 = new QCompanyReportVersionsEntity("rv3");
		QCompanyReportMetricValuesEntity v3 = new QCompanyReportMetricValuesEntity("v3");
		QMetricsEntity m3 = new QMetricsEntity("m3");

		var latestRiskVersion = JPAExpressions.select(rv2.versionNo.max())
			.from(rv2)
			.where(
				rv2.companyReport.eq(cr),
				SnapshotQueryScope.actual(rv2),
				JPAExpressions.selectOne()
					.from(v2)
					.join(v2.metric, m2)
					.where(
						v2.reportVersion.eq(rv2),
						v2.valueType.eq(MetricValueType.ACTUAL),
						v2.metricValue.isNotNull(),
						m2.isRiskIndicator.isTrue()
					)
					.exists()
			);

		var latestNonRiskVersion = JPAExpressions.select(rv3.versionNo.max())
			.from(rv3)
			.where(
				rv3.companyReport.eq(cr),
				SnapshotQueryScope.actual(rv3),
				JPAExpressions.selectOne()
					.from(v3)
					.join(v3.metric, m3)
					.where(
						v3.reportVersion.eq(rv3),
						v3.valueType.eq(MetricValueType.ACTUAL),
						v3.metricValue.isNotNull(),
						m3.isRiskIndicator.isFalse()
					)
					.exists()
			);

		return queryFactory
			.selectFrom(v)
			.join(v.reportVersion, rv)
			.join(rv.companyReport, cr)
			.join(cr.company, c)
			.join(v.metric, m).fetchJoin()
			.join(v.quarter, q)
			.where(
				SnapshotQueryScope.actual(rv),
				c.id.eq(companyId),
				q.quarterKey.eq(quarterKey),
				v.valueType.eq(MetricValueType.ACTUAL),
				v.metricValue.isNotNull(),
				m.isRiskIndicator.isTrue().and(rv.versionNo.eq(latestRiskVersion))
					.or(m.isRiskIndicator.isFalse().and(rv.versionNo.eq(latestNonRiskVersion)))
			)
			.fetch();
	}

	@Override
	public java.util.Optional<Integer> findMaxActualQuarterKeyByStockCodes(List<String> stockCodes) {
		QCompanyReportMetricValuesEntity v = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QCompanyReportVersionsEntity rv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;

		return java.util.Optional.ofNullable(
			queryFactory.select(q.quarterKey.max())
				.from(v)
				.join(v.reportVersion, rv)
				.join(rv.companyReport, cr)
				.join(cr.company, c)
				.join(v.quarter, q)
				.where(
					SnapshotQueryScope.actual(rv),
					c.stockCode.in(stockCodes),
					v.valueType.eq(MetricValueType.ACTUAL),
					v.metricValue.isNotNull()
				)
				.fetchOne()
		);
	}

	@Getter
	@RequiredArgsConstructor
	public static class ReportMetricRowDto implements ReportMetricRowProjection {
		private final String corpName;
		private final String stockCode;
		private final String metricCode;
		private final String metricNameKo;
		private final BigDecimal metricValue;
		private final MetricValueType valueType;
		private final int quarterKey;
		private final int versionNo;
		private final LocalDateTime generatedAt;
	}

	@Getter
	@RequiredArgsConstructor
	public static class ReportPredictMetricRowDto implements ReportPredictMetricRowProjection {
		private final String corpName;
		private final String stockCode;
		private final String metricCode;
		private final String metricNameKo;
		private final BigDecimal metricValue;
		private final MetricValueType valueType;
		private final int quarterKey;
		private final int versionNo;
		private final LocalDateTime generatedAt;
		private final Long pdfFileId;
		private final String pdfFileName;
		private final String pdfContentType;
	}

	@Getter
	@RequiredArgsConstructor
	public static class MetricValueSampleDto implements MetricValueSampleProjection {
		private final Long metricId;
		private final BigDecimal metricValue;
	}

	@Getter
	@RequiredArgsConstructor
	public static class CompanyOverviewMetricRowDto implements CompanyOverviewMetricRowProjection {
		private final String metricCode;
		private final String metricNameKo;
		private final String unit;
		private final BigDecimal metricValue;
		private final MetricValueType valueType;
		private final int quarterKey;
		private final com.aivle.project.finance.api.SignalColor signalColor;
		private final String description;
		private final String interpretation;
		private final String actionHint;
	}
}
