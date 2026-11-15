package com.aivle.project.insight.internal.company.insight.service;

import com.aivle.project.finance.internal.company.entity.CompaniesEntity;
import com.aivle.project.insight.api.CompanyInsightDto;
import com.aivle.project.insight.api.CompanyInsightType;
import com.aivle.project.news.internal.company.news.entity.NewsAnalysisEntity;
import com.aivle.project.news.internal.company.news.entity.NewsArticleEntity;
import com.aivle.project.news.internal.company.news.repository.NewsAnalysisRepository;
import com.aivle.project.news.internal.company.news.repository.NewsArticleRepository;
import com.aivle.project.news.api.NewsAnalysisResponse;
import com.aivle.project.news.api.NewsRefreshResponse;
import com.aivle.project.news.internal.company.news.service.NewsService;
import com.aivle.project.insight.internal.company.reportanalysis.entity.ReportAnalysisEntity;
import com.aivle.project.insight.internal.company.reportanalysis.entity.ReportContentEntity;
import com.aivle.project.insight.internal.company.reportanalysis.repository.ReportAnalysisRepository;
import com.aivle.project.insight.internal.company.reportanalysis.repository.ReportContentRepository;
import com.aivle.project.insight.internal.company.reportanalysis.service.ReportAnalysisService;
import com.aivle.project.finance.internal.company.repository.CompaniesRepository;
import com.aivle.project.finance.internal.company.service.CompanyReputationScoreService;
import com.aivle.project.aiintegration.api.ExternalAiUnavailableException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyInsightServiceTest {
	@org.mockito.Mock
	private com.aivle.project.news.api.NewsReadModel newsQueries;


	@Mock
	private com.aivle.project.finance.api.CompanyDirectory companiesRepository;
	@Mock
	private NewsAnalysisRepository newsAnalysisRepository;
	@Mock
	private NewsArticleRepository newsArticleRepository;
	@Mock
	private ReportAnalysisRepository reportAnalysisRepository;
	@Mock
	private ReportContentRepository reportContentRepository;
	@Mock
	private com.aivle.project.news.api.NewsOperations newsService;
	@Mock
	private ReportAnalysisService reportAnalysisService;
	@Mock
	private com.aivle.project.finance.api.ReputationOperations companyReputationScoreService;

	@InjectMocks
	private CompanyInsightService companyInsightService;

	@Test
	@DisplayName("DB에 인사이트가 있으면 DB 데이터를 반환한다")
	void getInsights_ReturnsDatabaseItems() {
		// given
		Long companyId = 1L;
		CompaniesEntity company = CompaniesEntity.create("001", "테스트기업", "TEST", "123456", LocalDate.now());
		ReportAnalysisEntity reportAnalysis = ReportAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		NewsAnalysisEntity newsAnalysis = NewsAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());

		ReportContentEntity reportContent = ReportContentEntity.create(
			reportAnalysis, "보고서", "요약", BigDecimal.ONE, LocalDateTime.of(2026, 2, 6, 0, 0), "https://r.example", null
		);
		NewsArticleEntity newsArticle = NewsArticleEntity.create(
			newsAnalysis, "뉴스", "본문", BigDecimal.ONE, LocalDateTime.of(2026, 2, 6, 0, 0), "https://n.example", "NEU"
		);

		when(companiesRepository.findById(companyId)).thenReturn(Optional.of(company));
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(reportAnalysis));
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(newsAnalysis));
		when(reportContentRepository.existsByReportAnalysisId(any())).thenReturn(true);
		when(newsQueries.existsByNewsAnalysisId(any())).thenReturn(true);
		when(reportContentRepository.findByReportAnalysisIdOrderByPublishedAtDesc(eq(reportAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(reportContent)));
		when(newsQueries.findByNewsAnalysisIdOrderByPublishedAtDesc(eq(newsAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(newsArticle)));

		// when
		com.aivle.project.insight.api.CompanyInsightResult result = companyInsightService.getInsights(companyId, 0, 1, 0, 1, false);

		// then
		assertThat(result.processing()).isFalse();
		assertThat(result.items()).hasSize(2);
		assertThat(result.averageScore()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(result.items().get(0).getType()).isEqualTo(CompanyInsightType.REPORT);
		assertThat(result.items().get(1).getType()).isEqualTo(CompanyInsightType.NEWS);
		verify(reportAnalysisService, org.mockito.Mockito.never()).fetchAndStoreReport(any());
		verify(newsService, org.mockito.Mockito.never()).refreshLatestNews(any());
		verify(companyReputationScoreService).syncExternalHealthScoreIfPresent(companyId, "123456");
	}

	@Test
	@DisplayName("DB에 인사이트가 없으면 외부 API 결과를 반환한다")
	void getInsights_ReturnsExternalItems() {
		// given
		Long companyId = 1L;
		CompaniesEntity company = CompaniesEntity.create("001", "테스트기업", "TEST", "123456", LocalDate.now());
		when(companiesRepository.findById(companyId)).thenReturn(Optional.of(company));
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(reportContentRepository.findTopByReportAnalysisCompanyIdOrderByPublishedAtDesc(companyId))
			.thenReturn(Optional.empty());
		when(newsQueries.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId))
			.thenReturn(Optional.empty());
		when(reportAnalysisService.fetchAndStoreReport("123456")).thenReturn(null);
		when(newsService.refreshLatestNews("123456")).thenReturn(new NewsRefreshResponse(
			new NewsAnalysisResponse(
				1L, companyId, "테스트기업", 1, 0.1, java.time.OffsetDateTime.now(), List.of(), java.time.OffsetDateTime.now()
			),
			false
		));

		ReportAnalysisEntity reportAnalysis = ReportAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		NewsAnalysisEntity newsAnalysis = NewsAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		ReportContentEntity reportContent = ReportContentEntity.create(
			reportAnalysis, "보고서", "요약", BigDecimal.ONE, LocalDateTime.of(2026, 2, 6, 0, 0), "https://r.example", null
		);
		NewsArticleEntity newsArticle = NewsArticleEntity.create(
			newsAnalysis, "뉴스", "본문", BigDecimal.ONE, LocalDateTime.of(2026, 2, 6, 0, 0), "https://n.example", "NEU"
		);
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(reportAnalysis));
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(newsAnalysis));
		when(reportContentRepository.findByReportAnalysisIdOrderByPublishedAtDesc(eq(reportAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(reportContent)));
		when(newsQueries.findByNewsAnalysisIdOrderByPublishedAtDesc(eq(newsAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(newsArticle)));

		// when
		com.aivle.project.insight.api.CompanyInsightResult result = companyInsightService.getInsights(companyId, 0, 1, 0, 1, false);

		// then
		assertThat(result.processing()).isFalse();
		assertThat(result.items()).hasSize(2);
		assertThat(result.averageScore()).isEqualByComparingTo(BigDecimal.ONE);
		assertThat(result.items())
			.extracting(CompanyInsightDto::getType)
			.containsExactly(CompanyInsightType.REPORT, CompanyInsightType.NEWS);
		assertThat(result.items().get(0).getPublishedAt()).isNotNull();
		assertThat(result.items().get(1).getPublishedAt()).isNotNull();
		verify(reportAnalysisService).fetchAndStoreReport("123456");
		verify(newsService).refreshLatestNews("123456");
		verify(companyReputationScoreService).syncExternalHealthScoreIfPresent(companyId, "123456");
	}

	@Test
	@DisplayName("refresh=true면 최신 뉴스/보고서를 강제 재수집한다")
	void getInsights_RefreshTrue_ForceRefetch() {
		// given
		Long companyId = 1L;
		CompaniesEntity company = CompaniesEntity.create("001", "테스트기업", "TEST", "123456", LocalDate.now());
		ReportAnalysisEntity reportAnalysis = ReportAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		NewsAnalysisEntity newsAnalysis = NewsAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		ReportContentEntity reportContent = ReportContentEntity.create(
			reportAnalysis, "보고서", "요약", BigDecimal.ONE, LocalDateTime.now(), "https://r.example", null
		);
		NewsArticleEntity newsArticle = NewsArticleEntity.create(
			newsAnalysis, "뉴스", "본문", BigDecimal.ONE, LocalDateTime.now(), "https://n.example", "NEU"
		);

		when(companiesRepository.findById(companyId)).thenReturn(Optional.of(company));
		when(newsService.refreshLatestNews("123456")).thenReturn(new NewsRefreshResponse(
			new NewsAnalysisResponse(
				1L, companyId, "테스트기업", 1, 0.1, java.time.OffsetDateTime.now(), List.of(), java.time.OffsetDateTime.now()
			),
			false
		));
		when(reportAnalysisService.fetchAndStoreReport("123456")).thenReturn(null);
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(reportAnalysis));
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(newsAnalysis));
		when(reportContentRepository.existsByReportAnalysisId(any())).thenReturn(true);
		when(newsQueries.existsByNewsAnalysisId(any())).thenReturn(true);
		when(reportContentRepository.findByReportAnalysisIdOrderByPublishedAtDesc(eq(reportAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(reportContent)));
		when(newsQueries.findByNewsAnalysisIdOrderByPublishedAtDesc(eq(newsAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(newsArticle)));

		// when
		com.aivle.project.insight.api.CompanyInsightResult result = companyInsightService.getInsights(companyId, 0, 1, 0, 1, true);

		// then
		assertThat(result.processing()).isFalse();
		assertThat(result.items()).hasSize(2);
		verify(newsService).refreshLatestNews("123456");
		verify(reportAnalysisService).fetchAndStoreReport("123456");
		verify(companyReputationScoreService).syncExternalHealthScoreIfPresent(companyId, "123456");
	}

	@Test
	@DisplayName("외부 AI 호출이 실패해도 기존 DB 인사이트가 있으면 fallback 응답을 반환한다")
	void getInsights_ReturnsFallbackItemsWhenExternalFails() {
		// given
		Long companyId = 1L;
		CompaniesEntity company = CompaniesEntity.create("001", "테스트기업", "TEST", "123456", LocalDate.now());
		ReportAnalysisEntity reportAnalysis = ReportAnalysisEntity.create(company, "테스트기업", 1, BigDecimal.ONE, LocalDateTime.now());
		ReportContentEntity reportContent = ReportContentEntity.create(
			reportAnalysis, "보고서", "요약", BigDecimal.ONE, LocalDateTime.of(2026, 2, 6, 0, 0), "https://r.example", null
		);

		when(companiesRepository.findById(companyId)).thenReturn(Optional.of(company));
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.of(reportAnalysis));
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(reportContentRepository.existsByReportAnalysisId(any())).thenReturn(true);
		when(newsQueries.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId)).thenReturn(Optional.empty());
		when(reportContentRepository.findByReportAnalysisIdOrderByPublishedAtDesc(eq(reportAnalysis.getId()), any(PageRequest.class)))
			.thenReturn(new PageImpl<>(List.of(reportContent)));
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(newsService.refreshLatestNews("123456"))
			.thenThrow(new ExternalAiUnavailableException("AI Server connection failed", "AI_TIMEOUT", new RuntimeException("timeout")));
		doThrow(new ExternalAiUnavailableException("AI Server connection failed", "AI_UNAVAILABLE", new RuntimeException("unavailable")))
			.when(companyReputationScoreService).syncExternalHealthScoreIfPresent(companyId, "123456");

		// when
		com.aivle.project.insight.api.CompanyInsightResult result = companyInsightService.getInsights(companyId, 0, 1, 0, 1, false);

		// then
		assertThat(result.items()).hasSize(1);
		assertThat(result.items().get(0).getType()).isEqualTo(CompanyInsightType.REPORT);
	}

	@Test
	@DisplayName("외부 AI 호출이 실패하고 기존 데이터도 없으면 503 예외를 던진다")
	void getInsights_ThrowsWhenNoDataAndExternalFails() {
		// given
		Long companyId = 1L;
		CompaniesEntity company = CompaniesEntity.create("001", "테스트기업", "TEST", "123456", LocalDate.now());
		when(companiesRepository.findById(companyId)).thenReturn(Optional.of(company));
		when(reportAnalysisRepository.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(newsQueries.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId)).thenReturn(Optional.empty());
		when(reportContentRepository.findTopByReportAnalysisCompanyIdOrderByPublishedAtDesc(companyId)).thenReturn(Optional.empty());
		when(newsQueries.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId)).thenReturn(Optional.empty());
		when(reportAnalysisService.fetchAndStoreReport("123456"))
			.thenThrow(new ExternalAiUnavailableException("AI Server connection failed", "AI_UNAVAILABLE", new RuntimeException("unavailable")));
		when(newsService.refreshLatestNews("123456"))
			.thenThrow(new ExternalAiUnavailableException("AI Server connection failed", "AI_TIMEOUT", new RuntimeException("timeout")));
		doThrow(new ExternalAiUnavailableException("AI Server connection failed", "AI_TIMEOUT", new RuntimeException("timeout")))
			.when(companyReputationScoreService).syncExternalHealthScoreIfPresent(companyId, "123456");

		// when & then
		assertThatThrownBy(() -> companyInsightService.getInsights(companyId, 0, 1, 0, 1, false))
			.isInstanceOf(ExternalAiUnavailableException.class);
	}
}
