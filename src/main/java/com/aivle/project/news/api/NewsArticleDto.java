package com.aivle.project.news.api;
import com.aivle.project.aiintegration.api.NewsItemResponse;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * 뉴스 기사 DTO.
 */
@Schema(description = "뉴스 기사")
public record NewsArticleDto(
    @Schema(description = "뉴스 기사 ID", example = "1")
    Long id,

    @Schema(description = "뉴스 제목")
    String title,

    @Schema(description = "뉴스 요약")
    String summary,

    @Schema(description = "감성 점수", example = "0.0007")
    Double score,

    @Schema(description = "뉴스 발행 시각 (UTC)", example = "2026-01-30T09:34:00+09:00")
    OffsetDateTime publishedAt,

    @Schema(description = "뉴스 URL")
    String link,

    @Schema(description = "감성 분류 (POS/NEU/NEG)", example = "NEU")
    String sentiment,

    @Schema(description = "생성 시각 (UTC)", example = "2026-02-05T12:35:41.369155Z")
    OffsetDateTime createdAt
) {
}
