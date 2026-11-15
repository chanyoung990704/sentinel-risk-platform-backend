package com.aivle.project.finance.internal.company.service;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.common.config.EnableJpaReadAdapters;
import com.aivle.project.finance.api.*;
import com.aivle.project.finance.internal.company.entity.CompaniesEntity;
import com.aivle.project.finance.internal.company.repository.CompaniesRepository;
import com.aivle.project.finance.internal.company.keymetric.entity.CompanyKeyMetricEntity;
import com.aivle.project.finance.internal.company.keymetric.repository.CompanyKeyMetricRepository;
import com.aivle.project.finance.internal.industry.entity.IndustryEntity;
import com.aivle.project.finance.internal.industry.entity.IndustryRepository;
import com.aivle.project.finance.internal.metric.repository.MetricsRepository;
import com.aivle.project.finance.internal.quarter.entity.QuartersEntity;
import com.aivle.project.finance.internal.quarter.repository.QuartersRepository;
import com.aivle.project.finance.internal.report.entity.*;
import com.aivle.project.finance.internal.report.repository.*;
import com.aivle.project.news.internal.company.news.entity.NewsAnalysisEntity;
import com.aivle.project.news.internal.company.news.repository.NewsAnalysisRepository;
import com.aivle.project.news.internal.company.news.service.NewsService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest @EnableJpaReadAdapters @ActiveProfiles("test")
@Import({LatestCompanyInformationService.class,CompanyInfoService.class,CompanySectorService.class,
	CompanyReputationScoreService.class,NewsReputationTestAdapter.class})
class LatestCompanyInformationServiceTest {
	@Autowired CompanyInformationBatches batch;
	@Autowired CompanyInfoService single;
	@Autowired CompaniesRepository companies;
	@Autowired IndustryRepository industries;
	@Autowired QuartersRepository quarters;
	@Autowired CompanyReportsRepository reports;
	@Autowired CompanyReportVersionsRepository versions;
	@Autowired CompanyReportMetricValuesRepository values;
	@Autowired MetricsRepository metrics;
	@Autowired CompanyKeyMetricRepository keyMetrics;
	@Autowired NewsAnalysisRepository news;
	@Autowired EntityManager entityManager;
	@MockitoBean NewsService externalNews;

	@Test void batchPreservesOrderDuplicatesNullQuarterAndSingleResponseValues() {
		// given
		var industry=industries.save(IndustryEntity.create("BAT01","시험업종"));
		var first=company("00000801","810001",industry);
		var empty=company("00000802","810002",null);
		var q=source(first,2026,1,MetricValueType.ACTUAL);
		keyMetrics.save(CompanyKeyMetricEntity.create(first,q,null,new BigDecimal("82.5"),null,
			new BigDecimal("70.1"),CompanyKeyMetricRiskLevel.SAFE,1,LocalDateTime.now()));
		news.save(NewsAnalysisEntity.create(first,"시험기업",1,new BigDecimal("0.254"),LocalDateTime.now()));
		entityManager.flush();entityManager.clear();
		var ids=List.of(empty.getId(),first.getId(),empty.getId());
		// when
		var result=batch.latest(ids);
		// then
		assertThat(result).usingRecursiveComparison().isEqualTo(List.of(
			single.getCompanyInfo(empty.getId(),(Integer)null),single.getCompanyInfo(first.getId(),20261),
			single.getCompanyInfo(empty.getId(),(Integer)null)));
		assertThat(result.get(1).getReputationScore()).isEqualTo(25.0);
		assertThat(result.get(0).getSector().getLabel()).isEmpty();
	}

	@Test void predictedQuarterAndNullStockCodeDoNotBecomeLatestActual() {
		// given
		var actual=company("00000803","810003",null);
		var noStock=company("00000804",null,null);
		source(actual,2026,1,MetricValueType.ACTUAL);
		source(actual,2026,2,MetricValueType.PREDICTED);
		source(noStock,2026,1,MetricValueType.ACTUAL);
		entityManager.flush();entityManager.clear();
		// when / then
		assertThat(batch.latest(List.of(actual.getId(),noStock.getId()))).usingRecursiveComparison().isEqualTo(List.of(
			single.getCompanyInfo(actual.getId(),20261),single.getCompanyInfo(noStock.getId(),(Integer)null)));
	}

	@Test void tiedNewsUsesLatestIdAndLatestNullDoesNotReviveOlderScore() {
		// given
		var company=company("00000805","810005",null);source(company,2026,1,MetricValueType.ACTUAL);
		var at=LocalDateTime.of(2026,1,1,0,0);
		news.save(NewsAnalysisEntity.create(company,"기업",1,new BigDecimal("0.9"),at));
		news.save(NewsAnalysisEntity.create(company,"기업",1,new BigDecimal("0.199"),at));
		entityManager.flush();entityManager.clear();
		// when / then
		assertThat(batch.latest(List.of(company.getId())).getFirst().getReputationScore()).isEqualTo(20.0);
		assertThat(single.getCompanyInfo(company.getId(),20261).getReputationScore()).isEqualTo(20.0);
		news.save(NewsAnalysisEntity.create(company,"기업",1,null,at));entityManager.flush();entityManager.clear();
		assertThat(batch.latest(List.of(company.getId())).getFirst().getReputationScore()).isNull();
		assertThat(single.getCompanyInfo(company.getId(),20261).getReputationScore()).isNull();
	}

	@Test void emptyAndMissingCompanyAreHandledExplicitly() {
		// given / when / then
		assertThat(batch.latest(List.of())).isEmpty();
		assertThatThrownBy(()->batch.latest(List.of(Long.MAX_VALUE))).isInstanceOf(IllegalArgumentException.class);
	}
	private CompaniesEntity company(String code,String stock,IndustryEntity industry) {
		return companies.save(CompaniesEntity.create(code,"기업"+code,null,stock,LocalDate.now(),industry));
	}
	private QuartersEntity source(CompaniesEntity company,int year,int quarter,MetricValueType type) {
		var q=quarters.findByYearAndQuarter((short)year,(byte)quarter).orElseGet(()->quarters.save(
			QuartersEntity.create(year,quarter,year*10+quarter,LocalDate.of(year,1,1),LocalDate.of(year,12,31))));
		var report=reports.save(CompanyReportsEntity.create(company,q,null));
		var version=versions.save(CompanyReportVersionsEntity.create(report,1,LocalDateTime.now(),true,null));
		values.save(CompanyReportMetricValuesEntity.create(version,metrics.findByMetricCode("ROE").orElseThrow(),q,BigDecimal.ONE,type));
		return q;
	}
}
