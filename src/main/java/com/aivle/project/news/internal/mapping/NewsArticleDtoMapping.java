package com.aivle.project.news.internal.mapping;
import com.aivle.project.aiintegration.api.NewsItemResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import com.aivle.project.news.api.NewsArticleDto;
public final class NewsArticleDtoMapping {
 private NewsArticleDtoMapping() { }


    /**
     * 뉴스 기사 엔티티에서 DTO로 변환.
     */
    public static NewsArticleDto from(com.aivle.project.news.internal.company.news.entity.NewsArticleEntity entity) {
        return new NewsArticleDto(
            entity.getId(),
            entity.getTitle(),
            entity.getSummary(),
            entity.getScore() != null ? entity.getScore().doubleValue() : null,
            entity.getPublishedAt() != null ? entity.getPublishedAt().atZone(ZoneOffset.UTC).toOffsetDateTime() : null,
            entity.getLink(),
            entity.getSentiment(),
            entity.getCreatedAt().atZone(ZoneOffset.UTC).toOffsetDateTime()
        );
    }

    /**
     * AI 서버 응답의 뉴스 아이템에서 DTO로 변환.
     */
    public static NewsArticleDto from(NewsItemResponse item) {
        return new NewsArticleDto(
            null, // ID는 DB 저장 시 생성됨
            item.title(),
            item.summary(),
            item.score() != null ? item.score() : null,
            parseDate(item.date()),
            item.link(),
            item.sentiment(),
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private static OffsetDateTime parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawDate);
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(rawDate).atOffset(ZoneOffset.UTC);
        }
    }

}
