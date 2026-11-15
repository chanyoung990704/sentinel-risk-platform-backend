package com.aivle.project.finance.internal.report.repository;

import com.aivle.project.finance.internal.report.entity.CompanyReportVersionsEntity;
import com.aivle.project.finance.internal.report.entity.CompanyReportsEntity;
import com.aivle.project.finance.api.RiskScoreBatchTargetProjection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 보고서 버전 조회/저장 리포지토리.
 */
public interface CompanyReportVersionsRepository extends JpaRepository<CompanyReportVersionsEntity, Long> {

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	Optional<CompanyReportVersionsEntity> findTopByCompanyReportOrderByVersionNoDesc(CompanyReportsEntity companyReport);

	Optional<CompanyReportVersionsEntity> findTopByCompanyReportAndDataSnapshotIdIsNullOrderByVersionNoDesc(CompanyReportsEntity companyReport);

	@Query("""
		select (count(rv) > 0) from CompanyReportVersionsEntity rv
		where rv.id = :versionId and rv.companyReport.company.id = :companyId
		  and rv.companyReport.quarter.id = :quarterId
		  and (rv.dataSnapshotId is null or exists (select s.id from DataSnapshotStateEntity s where s.id=rv.dataSnapshotId and s.state='VERIFIED'))
		""")
	boolean isReadableFixedVersion(@org.springframework.data.repository.query.Param("companyId") Long companyId,
		@org.springframework.data.repository.query.Param("quarterId") Long quarterId,
		@org.springframework.data.repository.query.Param("versionId") Long versionId);

	Optional<CompanyReportVersionsEntity> findTopByCompanyReportAndPdfFileIdIsNotNullOrderByVersionNoDesc(
		CompanyReportsEntity companyReport
	);

	@Query("""
		select rv.pdfFileId
		from CompanyReportVersionsEntity rv
		join rv.companyReport cr
		join cr.company c
		join cr.quarter q
		
		where c.id = :companyId
		  and q.year = :year
		  and q.quarter = :quarter
		  and rv.pdfFileId is not null
		  and rv.dataSnapshotId is null
		order by rv.versionNo desc
		""")
	java.util.List<Long> findLatestPdfFileIdsByCompanyIdAndYearAndQuarter(
		@org.springframework.data.repository.query.Param("companyId") Long companyId,
		@org.springframework.data.repository.query.Param("year") Short year,
		@org.springframework.data.repository.query.Param("quarter") Byte quarter,
		Pageable pageable
	);

	default Optional<CompanyReportVersionsEntity> findTopByCompanyReportAndPublishedFalseOrderByVersionNoDesc(CompanyReportsEntity companyReport) {
		return findTopByCompanyReportAndDataSnapshotIdIsNullAndPublishedFalseOrderByVersionNoDesc(companyReport);
	}
	Optional<CompanyReportVersionsEntity> findTopByCompanyReportAndDataSnapshotIdIsNullAndPublishedFalseOrderByVersionNoDesc(CompanyReportsEntity companyReport);

	@Query("""
		select cr.company.id as companyId,
			cr.quarter.id as quarterId,
			rv.id as reportVersionId
		from CompanyReportVersionsEntity rv
		join rv.companyReport cr
		where rv.versionNo = (
			select max(rv2.versionNo)
			from CompanyReportVersionsEntity rv2
			where rv2.companyReport = cr
			  and (rv2.dataSnapshotId = (select a.snapshotId from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL')
			       or (rv2.dataSnapshotId is null and not exists (select a.family from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL' and a.snapshotId is not null)))
		)
		and (rv.dataSnapshotId = (select a.snapshotId from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL')
		     or (rv.dataSnapshotId is null and not exists (select a.family from ActiveDataSnapshotEntity a where a.family = 'FINANCIAL' and a.snapshotId is not null)))
		order by cr.quarter.id, cr.company.id
		""")
	Page<RiskScoreBatchTargetProjection> findLatestRiskScoreTargets(Pageable pageable);

	@Query("""
		select rv
		from CompanyReportVersionsEntity rv
		join rv.companyReport cr
		join cr.company c
		join cr.quarter q
		where c.stockCode = :stockCode
		  and q.quarterKey = :quarterKey
		  and rv.pdfFileId is not null
		  and rv.dataSnapshotId is null
		order by rv.versionNo desc
		""")
	java.util.List<CompanyReportVersionsEntity> findLatestWithPdf(
		@org.springframework.data.repository.query.Param("stockCode") String stockCode,
		@org.springframework.data.repository.query.Param("quarterKey") int quarterKey,
		Pageable pageable
	);
}
