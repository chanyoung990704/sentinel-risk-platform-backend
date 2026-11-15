package com.aivle.project.news.internal;
import com.aivle.project.finance.api.ExternalReputationScores;
import com.aivle.project.news.internal.company.news.repository.NewsAnalysisRepository;
import com.aivle.project.news.internal.company.news.service.NewsService;
import java.util.Optional;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
class NewsReputationProvider implements ExternalReputationScores {
	private final NewsAnalysisRepository analyses;
	private final NewsService news;
	private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
	public Optional<BigDecimal> latest(Long companyId,String stockCode,boolean fetchIfMissing) {
		var latest=analyses.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);
		if(latest.isEmpty()&&fetchIfMissing){news.fetchAndStoreNews(stockCode);latest=analyses.findTopByCompanyIdOrderByAnalyzedAtDesc(companyId);}
		return latest.map(analysis -> analysis.getAverageScore());
	}
	@Override public java.util.Map<Long,BigDecimal> latestScores(java.util.Collection<Long> ids) {
		if (ids.isEmpty()) return java.util.Map.of();
		var scores = new java.util.HashMap<Long,BigDecimal>();
		jdbc.query("""
			SELECT n.company_id,n.average_score FROM news_analyses n WHERE n.company_id IN (:ids)
			AND NOT EXISTS (SELECT 1 FROM news_analyses newer WHERE newer.company_id=n.company_id
			  AND (newer.analyzed_at>n.analyzed_at OR (newer.analyzed_at=n.analyzed_at AND newer.id>n.id)))
			""", java.util.Map.of("ids",ids), (org.springframework.jdbc.core.RowCallbackHandler) row -> {
				var score=row.getBigDecimal("average_score");
				if(score!=null)scores.put(row.getLong("company_id"),score);
			});
		return java.util.Map.copyOf(scores);
	}
}
