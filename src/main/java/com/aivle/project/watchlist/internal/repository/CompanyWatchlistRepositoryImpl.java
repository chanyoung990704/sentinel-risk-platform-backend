package com.aivle.project.watchlist.internal.repository;
import com.aivle.project.finance.api.*;
import com.aivle.project.watchlist.internal.entity.QCompanyWatchlistEntity;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.*;
import lombok.RequiredArgsConstructor;
/** 관심 관계는 소유 모듈에서 읽고 재무 모듈에는 ID 묶음만 전달한다. */
@RequiredArgsConstructor
public class CompanyWatchlistRepositoryImpl implements CompanyWatchlistRepositoryCustom {
    private final JPAQueryFactory queryFactory;
    private final FinancialWatchlistReads reads;
    private Map<Long,Long> links(Long userId) {
        var cw=QCompanyWatchlistEntity.companyWatchlistEntity;
        var result=new LinkedHashMap<Long,Long>();
        queryFactory.select(cw.companyId,cw.id).from(cw).where(cw.userId.eq(userId),cw.deletedAt.isNull()).fetch()
            .forEach(tuple -> result.put(tuple.get(cw.companyId),tuple.get(cw.id)));
        return result;
    }
@Override public List<WatchlistDashboardMetricRow> findDashboardMetrics(Long userId,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	) { return reads.findDashboardMetrics(links(userId), year, quarter, valueType, metricCodes, metricCodesEmpty); }
@Override public List<WatchlistDashboardRiskRow> findDashboardRisks(Long userId,
		Long quarterId,
		RiskLevel riskLevel
	) { return reads.findDashboardRisks(links(userId), quarterId, riskLevel); }
@Override public List<WatchlistMetricValueProjection> findWatchlistMetricValues(Long userId,
		short year,
		byte quarter,
		MetricValueType valueType
	) { return reads.findWatchlistMetricValues(links(userId), year, quarter, valueType); }
@Override public List<WatchlistMetricValueProjection> findWatchlistMetricValuesInRange(Long userId,
		short fromYear,
		byte fromQuarter,
		short toYear,
		byte toQuarter,
		MetricValueType valueType
	) { return reads.findWatchlistMetricValuesInRange(links(userId), fromYear, fromQuarter, toYear, toQuarter, valueType); }
@Override public List<WatchlistMetricAverageRow> findWatchlistMetricAverages(Long userId,
		short year,
		byte quarter,
		MetricValueType valueType,
		Collection<String> metricCodes,
		boolean metricCodesEmpty
	) { return reads.findWatchlistMetricAverages(links(userId), year, quarter, valueType, metricCodes, metricCodesEmpty); }
}
