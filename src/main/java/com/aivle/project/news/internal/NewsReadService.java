package com.aivle.project.news.internal;
import com.aivle.project.news.api.*;
import com.aivle.project.news.internal.company.news.entity.*;
import com.aivle.project.news.internal.company.news.repository.*;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class NewsReadService implements NewsReadModel {
	private final NewsAnalysisRepository analyses;
	private final NewsArticleRepository articles;
	private NewsAnalysisSnapshot analysis(NewsAnalysisEntity row) {
		return new NewsAnalysisSnapshot(row.getId(),row.getAverageScore(),row.getAnalyzedAt());
	}
	private NewsArticleView article(NewsArticleEntity row) {
		return new NewsArticleSnapshot(row.getId(),row.getTitle(),row.getSummary(),row.getSentiment(),row.getLink(),row.getPublishedAt(),analysis(row.getNewsAnalysis()));
	}
	public Optional<NewsAnalysisView> findTopByCompanyIdOrderByAnalyzedAtDesc(Long companyId) {
		return analyses.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId).map(this::analysis);
	}
	public Optional<NewsArticleView> findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(Long companyId) {
		return articles.findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(companyId).map(this::article);
	}
	public Page<NewsArticleView> findByNewsAnalysisIdOrderByPublishedAtDesc(Long id,Pageable pageable) {
		return articles.findByNewsAnalysisIdOrderByPublishedAtDesc(id,pageable).map(this::article);
	}
	public boolean existsByNewsAnalysisId(Long id) { return articles.existsByNewsAnalysisId(id); }
}
