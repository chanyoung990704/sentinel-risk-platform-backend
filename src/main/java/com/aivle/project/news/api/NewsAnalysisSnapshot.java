package com.aivle.project.news.api;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record NewsAnalysisSnapshot(Long id,BigDecimal averageScore,LocalDateTime analyzedAt) implements NewsAnalysisView {
	public Long getId() { return id; }
	public BigDecimal getAverageScore() { return averageScore; }
	public LocalDateTime getAnalyzedAt() { return analyzedAt; }
}
