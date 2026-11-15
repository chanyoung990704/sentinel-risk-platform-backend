package com.aivle.project.news.api;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public interface NewsAnalysisView {
	Long getId();
	BigDecimal getAverageScore();
	LocalDateTime getAnalyzedAt();
}
