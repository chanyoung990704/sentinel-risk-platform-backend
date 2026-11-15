package com.aivle.project.insight.internal.mapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.aivle.project.insight.api.ReportContentDto;
public final class ReportContentDtoMapping {
 private ReportContentDtoMapping() { }


	public static ReportContentDto from(
		com.aivle.project.insight.internal.company.reportanalysis.entity.ReportContentEntity entity
	) {
		return new ReportContentDto(
			entity.getId(),
			entity.getTitle(),
			entity.getSummary(),
			entity.getScore() != null ? entity.getScore().doubleValue() : null,
			entity.getPublishedAt() != null
				? entity.getPublishedAt().atZone(ZoneOffset.UTC).toOffsetDateTime()
				: null,
			entity.getLink(),
			entity.getSentiment()
		);
	}

}
