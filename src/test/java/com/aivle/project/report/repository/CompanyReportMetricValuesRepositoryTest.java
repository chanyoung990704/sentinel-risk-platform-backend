package com.aivle.project.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.project.common.config.QuerydslConfig;
import com.aivle.project.company.entity.CompaniesEntity;
import com.aivle.project.company.repository.CompaniesRepository;
import com.aivle.project.metric.entity.MetricValueType;
import com.aivle.project.metric.entity.MetricsEntity;
import com.aivle.project.metric.repository.MetricsRepository;
import com.aivle.project.quarter.entity.QuartersEntity;
import com.aivle.project.quarter.repository.QuartersRepository;
import com.aivle.project.report.dto.ReportMetricRowProjection;
import com.aivle.project.report.entity.CompanyReportMetricValuesEntity;
import com.aivle.project.report.entity.CompanyReportVersionsEntity;
import com.aivle.project.report.entity.CompanyReportsEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(QuerydslConfig.class)
class CompanyReportMetricValuesRepositoryTest {

	@Autowired
	private CompanyReportMetricValuesRepository companyReportMetricValuesRepository;

	@Autowired
	private CompanyReportVersionsRepository companyReportVersionsRepository;

	@Autowired
	private CompanyReportsRepository companyReportsRepository;

	@Autowired
	private CompaniesRepository companiesRepository;

	@Autowired
	private QuartersRepository quartersRepository;

	@Autowired
	private MetricsRepository metricsRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("시드된 metrics 데이터는 isRiskIndicator 기본값 false를 가진다")
	void seededMetricHasDefaultRiskIndicatorFalse() {
		// given
		MetricsEntity metric = metricsRepository.findByMetricCode("ROA").orElseThrow();

		// when & then
		assertThat(metric.isRiskIndicator()).isFalse();
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.EnumSource(MetricValueType.class)
	void overviewPreservesCategoryLatestNullAndOutOfRangeSemantics(MetricValueType type) {
		// given: 위험/일반 지표의 최신 버전은 서로 다르며 NULL 전용 버전은 제외한다.
		var company=companiesRepository.save(CompaniesEntity.create("00000002","개요 비교 기업","PARITY","008888",LocalDate.of(2025,1,1)));
		var quarter=quartersRepository.save(QuartersEntity.create(2025,3,20253,LocalDate.of(2025,7,1),LocalDate.of(2025,9,30)));
		var future=quartersRepository.save(QuartersEntity.create(2025,4,20254,LocalDate.of(2025,10,1),LocalDate.of(2025,12,31)));
		var report=companyReportsRepository.save(CompanyReportsEntity.create(company,quarter,null));
		var risk=metricsRepository.save(MetricsEntity.create("PARITY_RISK","비교 위험 지표","parity risk",true));
		var normal=metricsRepository.findByMetricCode("ROA").orElseThrow();
		var versions=new java.util.ArrayList<CompanyReportVersionsEntity>();
		for(int number=1;number<=4;number++)versions.add(companyReportVersionsRepository.save(CompanyReportVersionsEntity.create(report,number,LocalDateTime.now(),false,null)));
		companyReportMetricValuesRepository.saveAll(List.of(
			CompanyReportMetricValuesEntity.create(versions.get(0),risk,quarter,new BigDecimal("10"),type),
			CompanyReportMetricValuesEntity.create(versions.get(0),normal,quarter,new BigDecimal("100"),type),
			CompanyReportMetricValuesEntity.create(versions.get(1),risk,quarter,new BigDecimal("20"),type),
			CompanyReportMetricValuesEntity.create(versions.get(2),normal,quarter,new BigDecimal("300"),type),
			CompanyReportMetricValuesEntity.create(versions.get(3),normal,quarter,null,type)));
		// when / then
		var rows=companyReportMetricValuesRepository.findLatestOverviewMetricsByStockCodeAndQuarterRange("008888",20253,20253,"ko");
		assertThat(rows).hasSize(2);
		assertThat(rows).filteredOn(row->row.getMetricCode().equals("PARITY_RISK")).extracting(row->row.getMetricValue().intValue()).containsExactly(20);
		assertThat(rows).filteredOn(row->row.getMetricCode().equals("ROA")).extracting(row->row.getMetricValue().intValue()).containsExactly(300);
		// given: 더 최신 일반 지표가 조회 범위 밖에 있으면 과거 범위의 값으로 되돌아가지 않는다.
		var latest=companyReportVersionsRepository.save(CompanyReportVersionsEntity.create(report,5,LocalDateTime.now(),false,null));
		companyReportMetricValuesRepository.save(CompanyReportMetricValuesEntity.create(latest,normal,future,new BigDecimal("500"),type));
		// when / then
		var narrowed=companyReportMetricValuesRepository.findLatestOverviewMetricsByStockCodeAndQuarterRange("008888",20253,20253,"ko");
		assertThat(narrowed).extracting(row->row.getMetricCode()).containsExactly("PARITY_RISK");
		var expanded=companyReportMetricValuesRepository.findLatestOverviewMetricsByStockCodeAndQuarterRange("008888",20253,20254,"ko");
		assertThat(expanded).hasSize(2).filteredOn(row->row.getMetricCode().equals("ROA")).extracting(row->row.getMetricValue().intValue()).containsExactly(500);
	}

	@Test
	@DisplayName("metrics 삽입 시 is_risk_indicator 미지정이면 DB 기본값 0이 저장된다")
	void insertMetricWithoutRiskIndicatorStoresDefaultZero() {
		// given
		jdbcTemplate.update(
			"INSERT INTO metrics (metric_code, metric_name_ko, metric_name_en, unit) VALUES (?, ?, ?, ?)",
			"TEST_RISK_DEFAULT",
			"테스트 리스크 기본값",
			"test_risk_default",
			"%"
		);

		// when
		MetricsEntity metric = metricsRepository.findByMetricCode("TEST_RISK_DEFAULT").orElseThrow();

		// then
		assertThat(metric.isRiskIndicator()).isFalse();
	}

	@Test
	@DisplayName("최신 버전 기준으로 분기 범위 지표를 조회한다")
	void findLatestMetricsByStockCodeAndQuarterRange() {
		// given
		CompaniesEntity company = companiesRepository.save(CompaniesEntity.create(
			"00000001",
			"테스트기업",
			"TEST_CO",
			"000020",
			LocalDate.of(2025, 1, 1)
		));
		QuartersEntity q20244 = quartersRepository.save(QuartersEntity.create(
			2024,
			4,
			20244,
			LocalDate.of(2024, 10, 1),
			LocalDate.of(2024, 12, 31)
		));
		QuartersEntity q20253 = quartersRepository.save(QuartersEntity.create(
			2025,
			3,
			20253,
			LocalDate.of(2025, 7, 1),
			LocalDate.of(2025, 9, 30)
		));
		MetricsEntity metric = metricsRepository.findByMetricCode("ROA").orElseThrow();

		CompanyReportsEntity report = companyReportsRepository.save(
			CompanyReportsEntity.create(company, q20253, null)
		);
		CompanyReportVersionsEntity oldVersion = companyReportVersionsRepository.save(
			CompanyReportVersionsEntity.create(report, 1, LocalDateTime.now().minusDays(1), false, null)
		);
		CompanyReportVersionsEntity latestVersion = companyReportVersionsRepository.save(
			CompanyReportVersionsEntity.create(report, 2, LocalDateTime.now(), false, null)
		);

		companyReportMetricValuesRepository.save(CompanyReportMetricValuesEntity.create(
			oldVersion,
			metric,
			q20253,
			new BigDecimal("1.11"),
			MetricValueType.ACTUAL
		));
		companyReportMetricValuesRepository.save(CompanyReportMetricValuesEntity.create(
			latestVersion,
			metric,
			q20244,
			new BigDecimal("2.22"),
			MetricValueType.ACTUAL
		));
		companyReportMetricValuesRepository.save(CompanyReportMetricValuesEntity.create(
			latestVersion,
			metric,
			q20253,
			new BigDecimal("3.33"),
			MetricValueType.ACTUAL
		));

		// when
		List<ReportMetricRowProjection> rows = companyReportMetricValuesRepository
			.findLatestMetricsByStockCodeAndQuarterRange("000020", 20244, 20253);

		// then
		assertThat(rows).hasSize(2);
		assertThat(rows).allMatch(row -> row.getVersionNo() == 2);
		assertThat(rows).allMatch(row -> "테스트기업".equals(row.getCorpName()));
		assertThat(rows).allMatch(row -> "000020".equals(row.getStockCode()));
		assertThat(rows).allMatch(row -> "ROA".equals(row.getMetricCode()));
		assertThat(rows).extracting(ReportMetricRowProjection::getQuarterKey)
			.containsExactly(20244, 20253);
	}
}
