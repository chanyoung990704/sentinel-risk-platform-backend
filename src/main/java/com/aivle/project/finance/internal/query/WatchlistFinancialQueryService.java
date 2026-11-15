package com.aivle.project.finance.internal.query;
import com.aivle.project.finance.api.WatchlistMetricValueProjection;

import com.aivle.project.finance.internal.dataimport.repository.SnapshotQueryScope;

import com.aivle.project.finance.internal.company.entity.QCompaniesEntity;
import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.internal.metric.entity.QMetricsEntity;
import com.aivle.project.finance.internal.metricaverage.entity.QMetricAverageEntity;
import com.aivle.project.finance.internal.quarter.entity.QQuartersEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportMetricValuesEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportVersionsEntity;
import com.aivle.project.finance.internal.report.entity.QCompanyReportsEntity;
import com.aivle.project.finance.internal.risk.entity.QRiskScoreSummaryEntity;
import com.aivle.project.finance.api.RiskLevel;
import com.aivle.project.finance.api.WatchlistDashboardMetricRow;
import com.aivle.project.finance.api.WatchlistDashboardRiskRow;
import com.aivle.project.finance.api.WatchlistMetricAverageRow;
import com.aivle.project.finance.api.FinancialWatchlistReads;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@org.springframework.stereotype.Repository
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class WatchlistFinancialQueryService implements FinancialWatchlistReads {

	private final JPAQueryFactory queryFactory;

	@Override
	public List<WatchlistDashboardMetricRow> findDashboardMetrics(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	) {
        if (watchlists.isEmpty()) return List.of();
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QCompanyReportVersionsEntity crv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportMetricValuesEntity crmv = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;
		QMetricAverageEntity ma = QMetricAverageEntity.metricAverageEntity;

		QCompanyReportVersionsEntity crv2 = new QCompanyReportVersionsEntity("crv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(WatchlistDashboardMetricRow.class,
				watchlistId(c.id, watchlists),
				c.id,
				c.corpName,
				c.corpCode,
				m.metricCode,
				m.metricNameKo,
				crmv.metricValue,
				ma.avgValue,
				ma.companyCount
			))
			.from(c)
			
			.join(cr).on(cr.company.eq(c))
			.join(cr.quarter, q)
			.join(crv).on(crv.companyReport.eq(cr))
			.join(crmv).on(crmv.reportVersion.eq(crv).and(crmv.quarter.eq(q)))
			.join(crmv.metric, m)
			.leftJoin(ma).on(ma.quarter.eq(q).and(ma.metric.eq(m)).and(SnapshotQueryScope.actualSource(ma.dataSnapshotId)))
			.where(
				c.id.in(watchlists.keySet()),
				
				q.year.eq(year),
				q.quarter.eq(quarter),
				m.isRiskIndicator.isFalse(),
				crmv.valueType.eq(valueType),
				SnapshotQueryScope.forType(crv, valueType),
				crmv.metricValue.isNotNull(),
				crv.versionNo.eq(
					JPAExpressions.select(crv2.versionNo.max())
						.from(crv2)
						.where(crv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(crv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(crv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				),
				metricCodesExpression(m, metricCodes, metricCodesEmpty)
			)
			.orderBy(c.corpName.asc(), m.metricCode.asc())
			.fetch();
	}

	@Override
	public List<WatchlistDashboardRiskRow> findDashboardRisks(
		java.util.Map<Long, Long> watchlists,
		Long quarterId,
		RiskLevel riskLevel
	) {
        if (watchlists.isEmpty()) return List.of();
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QRiskScoreSummaryEntity rss = QRiskScoreSummaryEntity.riskScoreSummaryEntity;
		QCompanyReportVersionsEntity riskVersion=new QCompanyReportVersionsEntity("riskVersion");
		QCompanyReportVersionsEntity riskVersion2=new QCompanyReportVersionsEntity("riskVersion2");
		QRiskScoreSummaryEntity rss2=new QRiskScoreSummaryEntity("riskSummary2");

		return queryFactory
			.select(Projections.constructor(WatchlistDashboardRiskRow.class,
				watchlistId(c.id, watchlists),
				c.id,
				c.corpName,
				rss.riskScore,
				rss.riskLevel,
				rss.riskMetricsAvg,
				rss.updatedAt
			))
			.from(c)
			
			.join(rss).on(rss.company.eq(c))
			.join(rss.reportVersion,riskVersion)
			.where(
				c.id.in(watchlists.keySet()),
				
				rss.quarter.id.eq(quarterId),
				SnapshotQueryScope.actual(riskVersion),
				riskVersion.versionNo.eq(JPAExpressions.select(riskVersion2.versionNo.max()).from(rss2).join(rss2.reportVersion,riskVersion2).where(rss2.company.eq(c),rss2.quarter.id.eq(quarterId),SnapshotQueryScope.actual(riskVersion2))),
				riskLevelEq(rss, riskLevel)
			)
			.orderBy(rss.riskScore.desc())
			.fetch();
	}

	@Override
	public List<WatchlistMetricValueProjection> findWatchlistMetricValues(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType
	) {
        if (watchlists.isEmpty()) return List.of();
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QCompanyReportVersionsEntity crv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportMetricValuesEntity crmv = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;

		QCompanyReportVersionsEntity crv2 = new QCompanyReportVersionsEntity("crv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(WatchlistMetricValueDto.class,
				watchlistId(c.id, watchlists),
				c.id,
				c.corpName,
				c.corpCode,
				m.metricCode,
				m.metricNameKo,
				crmv.metricValue,
				q.year.castToNum(Integer.class),
				q.quarter.castToNum(Integer.class)
			))
			.from(c)
			
			.join(cr).on(cr.company.eq(c))
			.join(cr.quarter, q)
			.join(crv).on(crv.companyReport.eq(cr))
			.join(crmv).on(crmv.reportVersion.eq(crv).and(crmv.quarter.eq(q)))
			.join(crmv.metric, m)
			.where(
				c.id.in(watchlists.keySet()),
				
				q.year.eq(year),
				q.quarter.eq(quarter),
				crmv.valueType.eq(valueType),
				SnapshotQueryScope.forType(crv, valueType),
				crmv.metricValue.isNotNull(),
				crv.versionNo.eq(
					JPAExpressions.select(crv2.versionNo.max())
						.from(crv2)
						.where(crv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(crv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(crv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(c.corpName.asc(), m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (WatchlistMetricValueProjection) dto)
			.toList();
	}

	@Override
	public List<WatchlistMetricValueProjection> findWatchlistMetricValuesInRange(
		java.util.Map<Long, Long> watchlists,
		short fromYear,
		byte fromQuarter,
		short toYear,
		byte toQuarter,
		MetricValueType valueType
	) {
        if (watchlists.isEmpty()) return List.of();
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QCompanyReportVersionsEntity crv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportMetricValuesEntity crmv = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;

		QCompanyReportVersionsEntity crv2 = new QCompanyReportVersionsEntity("crv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(WatchlistMetricValueDto.class,
				watchlistId(c.id, watchlists),
				c.id,
				c.corpName,
				c.corpCode,
				m.metricCode,
				m.metricNameKo,
				crmv.metricValue,
				q.year.castToNum(Integer.class),
				q.quarter.castToNum(Integer.class)
			))
			.from(c)
			
			.join(cr).on(cr.company.eq(c))
			.join(cr.quarter, q)
			.join(crv).on(crv.companyReport.eq(cr))
			.join(crmv).on(crmv.reportVersion.eq(crv).and(crmv.quarter.eq(q)))
			.join(crmv.metric, m)
			.where(
				c.id.in(watchlists.keySet()),
				
				q.year.gt(fromYear).or(q.year.eq(fromYear).and(q.quarter.goe(fromQuarter))),
				q.year.lt(toYear).or(q.year.eq(toYear).and(q.quarter.loe(toQuarter))),
				crmv.valueType.eq(valueType),
				SnapshotQueryScope.forType(crv, valueType),
				crmv.metricValue.isNotNull(),
				crv.versionNo.eq(
					JPAExpressions.select(crv2.versionNo.max())
						.from(crv2)
						.where(crv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(crv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(crv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				)
			)
			.orderBy(q.year.asc(), q.quarter.asc(), c.corpName.asc(), m.metricCode.asc())
			.fetch()
			.stream()
			.map(dto -> (WatchlistMetricValueProjection) dto)
			.toList();
	}

	@Override
	public List<WatchlistMetricAverageRow> findWatchlistMetricAverages(
		java.util.Map<Long, Long> watchlists,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	) {
        if (watchlists.isEmpty()) return List.of();
		QCompaniesEntity c = QCompaniesEntity.companiesEntity;
		QCompanyReportsEntity cr = QCompanyReportsEntity.companyReportsEntity;
		QQuartersEntity q = QQuartersEntity.quartersEntity;
		QCompanyReportVersionsEntity crv = QCompanyReportVersionsEntity.companyReportVersionsEntity;
		QCompanyReportMetricValuesEntity crmv = QCompanyReportMetricValuesEntity.companyReportMetricValuesEntity;
		QMetricsEntity m = QMetricsEntity.metricsEntity;

		QCompanyReportVersionsEntity crv2 = new QCompanyReportVersionsEntity("crv2");
		QCompanyReportMetricValuesEntity v2 = new QCompanyReportMetricValuesEntity("v2");

		return queryFactory
			.select(Projections.constructor(WatchlistMetricAverageRow.class,
				m.metricCode,
				m.metricNameKo,
				Expressions.numberTemplate(BigDecimal.class, "cast({0} as bigdecimal)", crmv.metricValue.avg()),
				c.id.countDistinct()
			))
			.from(c)
			
			.join(cr).on(cr.company.eq(c))
			.join(cr.quarter, q)
			.join(crv).on(crv.companyReport.eq(cr))
			.join(crmv).on(crmv.reportVersion.eq(crv).and(crmv.quarter.eq(q)))
			.join(crmv.metric, m)
			.where(
				c.id.in(watchlists.keySet()),
				
				q.year.eq(year),
				q.quarter.eq(quarter),
				crmv.valueType.eq(valueType),
				SnapshotQueryScope.forType(crv, valueType),
				m.isRiskIndicator.isFalse(),
				crmv.metricValue.isNotNull(),
				crv.versionNo.eq(
					JPAExpressions.select(crv2.versionNo.max())
						.from(crv2)
						.where(crv2.companyReport.eq(cr),
							SnapshotQueryScope.forType(crv2, valueType),
							JPAExpressions.selectOne()
								.from(v2)
								.where(v2.reportVersion.eq(crv2),
									v2.valueType.eq(valueType),
									v2.metricValue.isNotNull()
								).exists()
						)
				),
				metricCodesExpression(m, metricCodes, metricCodesEmpty)
			)
			.groupBy(m.id, m.metricCode, m.metricNameKo)
			.orderBy(m.metricCode.asc())
			.fetch();
	}

	private BooleanExpression metricCodesExpression(QMetricsEntity m, Collection<String> metricCodes, boolean metricCodesEmpty) {
		if (metricCodesEmpty) {
			return null;
		}
		return m.metricCode.in(metricCodes);
	}

	private BooleanExpression riskLevelEq(QRiskScoreSummaryEntity rss, RiskLevel riskLevel) {
		if (riskLevel == null) {
			return null;
		}
		return rss.riskLevel.eq(riskLevel);
	}

	@Getter
	@RequiredArgsConstructor
	public static class WatchlistMetricValueDto implements WatchlistMetricValueProjection {
		private final Long watchlistId;
		private final Long companyId;
		private final String corpName;
		private final String corpCode;
		private final String metricCode;
		private final String metricNameKo;
		private final BigDecimal metricValue;
		private final Integer year;
		private final Integer quarter;
	}

    private com.querydsl.core.types.dsl.NumberExpression<Long> watchlistId(
        com.querydsl.core.types.dsl.NumberPath<Long> companyId, java.util.Map<Long, Long> links) {
        var entries=links.entrySet().iterator();
        var first=entries.next();
        var cases=new com.querydsl.core.types.dsl.CaseBuilder().when(companyId.eq(first.getKey())).then(first.getValue());
        while(entries.hasNext()) {
            var entry=entries.next();
            cases=cases.when(companyId.eq(entry.getKey())).then(entry.getValue());
        }
        return cases.otherwise(0L);
    }
}
