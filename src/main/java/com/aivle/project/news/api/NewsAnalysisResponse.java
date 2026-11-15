package com.aivle.project.news.api;
import com.aivle.project.news.api.NewsArticleDto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 뉴스 분석 조회 응답 DTO.
 */
@Schema(description = "뉴스 분석 조회 응답")
public record NewsAnalysisResponse(
    @Schema(description = "분석 ID", example = "1")
    Long id,

    @Schema(description = "기업 ID", example = "100")
    Long companyId,

    @Schema(description = "기업명", example = "동화약품")
    String companyName,

    @Schema(description = "분석된 뉴스 총 개수", example = "3")
    Integer totalCount,

    @Schema(description = "평균 감성 점수", example = "0.0041")
    Double averageScore,

    @Schema(description = "AI 분석 시각 (UTC)", example = "2026-02-05T12:35:41.369155Z")
    OffsetDateTime analyzedAt,

    @Schema(description = "뉴스 기사 목록")
    List<NewsArticleDto> news,

    @Schema(description = "생성 시각 (UTC)", example = "2026-02-05T12:35:41.369155Z")
    OffsetDateTime createdAt
) {
}
