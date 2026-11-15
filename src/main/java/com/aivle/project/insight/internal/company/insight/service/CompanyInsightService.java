package com.aivle.project.insight.internal.company.insight.service;
import com.aivle.project.insight.api.CompanyInsightResult;

import com.aivle.project.finance.api.CompanyIdentity;
import com.aivle.project.insight.api.CompanyInsightDto;
import com.aivle.project.insight.api.CompanyInsightType;
import com.aivle.project.aiintegration.api.ExternalAiUnavailableException;
import com.aivle.project.news.api.NewsAnalysisView;
import com.aivle.project.news.api.NewsArticleView;
import com.aivle.project.news.api.NewsReadModel;

import com.aivle.project.news.api.NewsOperations;
import com.aivle.project.insight.internal.company.reportanalysis.entity.ReportAnalysisEntity;
import com.aivle.project.insight.internal.company.reportanalysis.entity.ReportContentEntity;
import com.aivle.project.insight.internal.company.reportanalysis.repository.ReportAnalysisRepository;
import com.aivle.project.insight.internal.company.reportanalysis.repository.ReportContentRepository;
import com.aivle.project.insight.internal.company.reportanalysis.service.ReportAnalysisService;
import com.aivle.project.finance.api.CompanyDirectory;
import com.aivle.project.finance.api.ReputationOperations;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기업 인사이트 조회 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyInsightService implements com.aivle.project.insight.api.CompanyInsights {

	private final CompanyDirectory companiesRepository;
	private final NewsReadModel newsQueries;
	
	private final ReportAnalysisRepository reportAnalysisRepository;
	private final ReportContentRepository reportContentRepository;
	private final NewsOperations newsService;
	private final ReportAnalysisService reportAnalysisService;
	private final ReputationOperations companyReputationScoreService;

	/**
	 * 쓰기/조회 통합 경로 (하위 호환용).
	 */
	@Transactional
	public CompanyInsightResult getInsights(
		Long companyId,
		int newsPage,
		int newsSize,
		int reportPage,
		int reportSize,
		boolean refresh
	) {
		InsightCompanyRef companyIdentity = resolveInsightCompanyRef(companyId);
		ExternalAiUnavailableException externalFailure = ensureInsightData(companyIdentity, refresh);
		return loadInsights(companyId, newsPage, newsSize, reportPage, reportSize, externalFailure);
	}

	/**
	 * 인사이트 생성/갱신 단계 (쓰기 트랜잭션).
	 */
	@Transactional
	public ExternalAiUnavailableException ensureInsightData(Long companyId, boolean refresh) {
		InsightCompanyRef companyIdentity = resolveInsightCompanyRef(companyId);
		return ensureInsightData(companyIdentity, refresh);
	}

	/**
	 * 인사이트 조회/조립 단계 (읽기 전용 트랜잭션).
	 */
	@Transactional(readOnly = true)
	public CompanyInsightResult loadInsights(
		Long companyId,
		int newsPage,
		int newsSize,
		int reportPage,
		int reportSize,
		ExternalAiUnavailableException externalFailure
	) {
		Optional<ReportAnalysisEntity> latestReportOpt = reportAnalysisRepository
			.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);
		Optional<NewsAnalysisView> latestNewsOpt = newsQueries
			.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);
		ReportAnalysisEntity latestReport = latestReportOpt.orElse(null);
		NewsAnalysisView latestNews = latestNewsOpt.orElse(null);

		if (latestReport == null) {
			latestReport = reportContentRepository
				.findTopByReportAnalysisCompanyIdOrderByPublishedAtDesc(companyId)
				.map(ReportContentEntity::getReportAnalysis)
				.orElse(null);
		}
		if (latestNews == null) {
			latestNews = newsQueries
				.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId)
				.map(NewsArticleView::getNewsAnalysis)
				.orElse(null);
		}

		List<CompanyInsightDto> items = new java.util.ArrayList<>();

		if (latestReport != null) {
			Page<ReportContentEntity> reportContents = reportContentRepository
				.findByReportAnalysisIdOrderByPublishedAtDesc(latestReport.getId(), PageRequest.of(reportPage, reportSize));
			for (ReportContentEntity content : reportContents.getContent()) {
				items.add(CompanyInsightDto.builder()
					.id(content.getId())
					.type(CompanyInsightType.REPORT)
					.title(content.getTitle())
					.body(content.getSummary())
					.content(null)
					.source(null)
					.publishedAt(resolvePublishedAt(content.getPublishedAt(), latestReport.getAnalyzedAt()))
					.url(content.getLink())
					.build());
			}
		}

		if (latestNews != null) {
			Page<NewsArticleView> newsArticles = newsQueries
				.findByNewsAnalysisIdOrderByPublishedAtDesc(latestNews.getId(), PageRequest.of(newsPage, newsSize));
			for (NewsArticleView article : newsArticles.getContent()) {
				items.add(CompanyInsightDto.builder()
					.id(article.getId())
					.type(CompanyInsightType.NEWS)
					.title(article.getTitle())
					.body(null)
					.content(article.getSummary())
					.source(article.getSentiment())
					.publishedAt(resolvePublishedAt(article.getPublishedAt(), latestNews.getAnalyzedAt()))
					.url(article.getLink())
					.build());
			}
		}

		if (items.isEmpty() && externalFailure != null) {
			throw externalFailure;
		}
		return new CompanyInsightResult(items, latestNews != null ? latestNews.getAverageScore() : null, false);
	}

	private InsightCompanyRef resolveInsightCompanyRef(Long companyId) {
		CompanyIdentity company = companiesRepository.findById(companyId)
			.orElseThrow(() -> new IllegalArgumentException("Company not found for id: " + companyId));
		String stockCode = company.getStockCode();
		if (stockCode == null || stockCode.isBlank()) {
			throw new IllegalArgumentException("Company has no stockCode: " + companyId);
		}
		return new InsightCompanyRef(companyId, stockCode);
	}

	private ExternalAiUnavailableException ensureInsightData(InsightCompanyRef companyIdentity, boolean refresh) {
		Long companyId = companyIdentity.companyId();
		String stockCode = companyIdentity.stockCode();
		ExternalAiUnavailableException externalFailure = null;

		if (refresh) {
			try {
				newsService.refreshLatestNews(stockCode);
			} catch (ExternalAiUnavailableException ex) {
				externalFailure = ex;
				log.warn("인사이트 강제 갱신 중 뉴스 수집 실패: companyId={}, stockCode={}, reasonCode={}",
					companyId, stockCode, ex.getReasonCode());
			}
			try {
				reportAnalysisService.fetchAndStoreReport(stockCode);
			} catch (ExternalAiUnavailableException ex) {
				if (externalFailure == null) {
					externalFailure = ex;
				}
				log.warn("인사이트 강제 갱신 중 보고서 수집 실패: companyId={}, stockCode={}, reasonCode={}",
					companyId, stockCode, ex.getReasonCode());
			}
		}

		Optional<ReportAnalysisEntity> latestReportOpt = reportAnalysisRepository
			.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);
		Optional<NewsAnalysisView> latestNewsOpt = newsQueries
			.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);

		boolean hasReport = latestReportOpt.isPresent()
			&& reportContentRepository.existsByReportAnalysisId(latestReportOpt.get().getId());
		boolean hasNews = latestNewsOpt.isPresent()
			&& newsQueries.existsByNewsAnalysisId(latestNewsOpt.get().getId());

		if (!hasReport) {
			hasReport = reportContentRepository.findTopByReportAnalysisCompanyIdOrderByPublishedAtDesc(companyId).isPresent();
		}
		if (!hasNews) {
			hasNews = newsQueries.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId).isPresent();
		}

		if (!hasReport) {
			try {
				reportAnalysisService.fetchAndStoreReport(stockCode);
			} catch (ExternalAiUnavailableException ex) {
				if (externalFailure == null) {
					externalFailure = ex;
				}
				log.warn("인사이트 조회 중 보고서 수집 실패: companyId={}, stockCode={}, reasonCode={}",
					companyId, stockCode, ex.getReasonCode());
			}
		}
		if (!hasNews) {
			try {
				newsService.refreshLatestNews(stockCode);
			} catch (ExternalAiUnavailableException ex) {
				if (externalFailure == null) {
					externalFailure = ex;
				}
				log.warn("인사이트 조회 중 뉴스 수집 실패: companyId={}, stockCode={}, reasonCode={}",
					companyId, stockCode, ex.getReasonCode());
			}
		}
		try {
			companyReputationScoreService.syncExternalHealthScoreIfPresent(companyId, stockCode);
		} catch (ExternalAiUnavailableException ex) {
			if (externalFailure == null) {
				externalFailure = ex;
			}
			log.warn("인사이트 조회 중 평판 점수 동기화 실패: companyId={}, stockCode={}, reasonCode={}",
				companyId, stockCode, ex.getReasonCode());
		}
		return externalFailure;
	}

	private LocalDateTime resolvePublishedAt(LocalDateTime publishedAt, LocalDateTime fallback) {
		return publishedAt != null ? publishedAt : fallback;
	}

	private record InsightCompanyRef(Long companyId, String stockCode) {
	}
}
