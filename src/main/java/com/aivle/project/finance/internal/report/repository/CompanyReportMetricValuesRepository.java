package com.aivle.project.finance.internal.report.repository;

import com.aivle.project.finance.api.MetricValueType;
import com.aivle.project.finance.internal.report.entity.CompanyReportMetricValuesEntity;
import com.aivle.project.finance.internal.report.entity.CompanyReportVersionsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 보고서 지표 값 조회/저장 리포지토리.
 */
public interface CompanyReportMetricValuesRepository extends JpaRepository<CompanyReportMetricValuesEntity, Long>, CompanyReportMetricValuesRepositoryCustom {

	boolean existsByReportVersionAndValueTypeAndMetricValueIsNotNull(
		CompanyReportVersionsEntity reportVersion,
		MetricValueType valueType
	);

	@Query("""
		select max(q.quarterKey)
		from CompanyReportMetricValuesEntity v
		join v.quarter q
		join v.reportVersion rv
		join rv.companyReport cr
		join cr.company c
		where c.stockCode = :stockCode
		  and v.valueType = com.aivle.project.finance.api.MetricValueType.ACTUAL
		  and (rv.dataSnapshotId = (select a.snapshotId from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL')
		       or (rv.dataSnapshotId is null and not exists (select a.family from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL' and a.snapshotId is not null)))
		""")
	Optional<Integer> findMaxActualQuarterKeyByStockCode(@Param("stockCode") String stockCode);

	@Query("""
		select max(q.quarterKey)
		from CompanyReportMetricValuesEntity v
		join v.quarter q
		join v.reportVersion rv
		join rv.companyReport cr
		where cr.company.id = :companyId
		  and v.valueType = com.aivle.project.finance.api.MetricValueType.ACTUAL
		  and (rv.dataSnapshotId = (select a.snapshotId from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL')
		       or (rv.dataSnapshotId is null and not exists (select a.family from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL' and a.snapshotId is not null)))
		""")
	Optional<Integer> findMaxActualQuarterKeyByCompanyId(@Param("companyId") Long companyId);
}
