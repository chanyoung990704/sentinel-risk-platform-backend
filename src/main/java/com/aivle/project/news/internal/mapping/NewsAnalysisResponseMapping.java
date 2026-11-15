package com.aivle.project.news.internal.mapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import com.aivle.project.news.api.NewsAnalysisResponse;
public final class NewsAnalysisResponseMapping {
 private NewsAnalysisResponseMapping() { }


    /**
     * 뉴스 분석 엔티티와 뉴스 기사 목록から DTO로 변환.
     */
    public static NewsAnalysisResponse from(
        Long companyId,
        com.aivle.project.news.internal.company.news.entity.NewsAnalysisEntity entity,
        List<com.aivle.project.news.internal.company.news.entity.NewsArticleEntity> articles
    ) {
        return new NewsAnalysisResponse(
            entity.getId(),
            companyId,
            entity.getCompanyName(),
            entity.getTotalCount(),
            entity.getAverageScore() != null ? entity.getAverageScore().doubleValue() : null,
            entity.getAnalyzedAt().atZone(ZoneOffset.UTC).toOffsetDateTime(),
            articles.stream()
                .map(com.aivle.project.news.internal.mapping.NewsArticleDtoMapping::from)
                .collect(Collectors.toList()),
            entity.getCreatedAt().atZone(ZoneOffset.UTC).toOffsetDateTime()
        );
    }

}
