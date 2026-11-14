package com.aivle.project.metricaverage.repository;

import com.aivle.project.metricaverage.entity.MetricAverageEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/**
 * 분기별 지표 평균 집계 리포지토리.
 */
public interface MetricAverageRepository extends JpaRepository<MetricAverageEntity, Long> {

	@Query("""
		select m from MetricAverageEntity m where m.quarter.id=:quarterId and m.metric.id=:metricId
		and (m.dataSnapshotId=(select a.snapshotId from ActiveDataSnapshotEntity a where a.family='FINANCIAL')
		or (m.dataSnapshotId is null and not exists(select a.family from ActiveDataSnapshotEntity a where a.family='FINANCIAL' and a.snapshotId is not null)))
		""")
	Optional<MetricAverageEntity> findByQuarterIdAndMetricId(@Param("quarterId") Long quarterId,@Param("metricId") Long metricId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from MetricAverageEntity m where m.quarter.id=:quarterId and m.metric.id=:metricId")
	Optional<MetricAverageEntity> findAnyByQuarterIdAndMetricIdForUpdate(@Param("quarterId") Long quarterId,@Param("metricId") Long metricId);

	default boolean existsByQuarterIdAndMetricId(Long quarterId,Long metricId) {return findByQuarterIdAndMetricId(quarterId,metricId).isPresent();}

	@Query("select a.snapshotId from ActiveDataSnapshotEntity a where a.family='FINANCIAL'")
	Optional<String> findActiveSourceSnapshot();
}
