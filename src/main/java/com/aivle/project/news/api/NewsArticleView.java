package com.aivle.project.news.api;
import java.time.LocalDateTime;
public interface NewsArticleView {
	Long getId();
	String getTitle();
	String getSummary();
	String getSentiment();
	String getLink();
	LocalDateTime getPublishedAt();
	NewsAnalysisView getNewsAnalysis();
}
