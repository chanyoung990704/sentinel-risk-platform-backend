package com.aivle.project.insight.api;
import java.util.List;
import java.math.BigDecimal;
@io.swagger.v3.oas.annotations.media.Schema(name="InsightResult")
public record CompanyInsightResult(List<CompanyInsightDto> items, BigDecimal averageScore, boolean processing) {
	public CompanyInsightResult { items = List.copyOf(items); }
}
