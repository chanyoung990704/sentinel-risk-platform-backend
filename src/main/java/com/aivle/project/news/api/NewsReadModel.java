package com.aivle.project.news.api;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface NewsReadModel {
	Optional<NewsAnalysisView> findTopByCompanyIdOrderByAnalyzedAtDesc(Long companyId);
	Optional<NewsArticleView> findTopByNewsAnalysisCompanyIdOrderByPublishedAtDesc(Long companyId);
	Page<NewsArticleView> findByNewsAnalysisIdOrderByPublishedAtDesc(Long analysisId,Pageable pageable);
	boolean existsByNewsAnalysisId(Long analysisId);
}
