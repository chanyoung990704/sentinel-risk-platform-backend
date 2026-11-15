package com.aivle.project.news.api;
import java.time.LocalDateTime;
public record NewsArticleSnapshot(Long id,String title,String summary,String sentiment,String link,
	LocalDateTime publishedAt,NewsAnalysisSnapshot newsAnalysis) implements NewsArticleView {
	public Long getId() { return id; }
	public String getTitle() { return title; }
	public String getSummary() { return summary; }
	public String getSentiment() { return sentiment; }
	public String getLink() { return link; }
	public LocalDateTime getPublishedAt() { return publishedAt; }
	public NewsAnalysisView getNewsAnalysis() { return newsAnalysis; }
}
