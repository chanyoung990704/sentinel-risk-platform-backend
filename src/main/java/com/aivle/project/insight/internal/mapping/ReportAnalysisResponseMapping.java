package com.aivle.project.insight.internal.mapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import com.aivle.project.insight.api.ReportAnalysisResponse;
public final class ReportAnalysisResponseMapping {
 private ReportAnalysisResponseMapping() { }


	public static ReportAnalysisResponse from(
		Long companyId,
		com.aivle.project.insight.internal.company.reportanalysis.entity.ReportAnalysisEntity entity,
		List<com.aivle.project.insight.internal.company.reportanalysis.entity.ReportContentEntity> contents
	) {
		return new ReportAnalysisResponse(
			entity.getId(),
			companyId,
			entity.getCompanyName(),
			entity.getTotalCount(),
			entity.getAverageScore() != null ? entity.getAverageScore().doubleValue() : null,
			entity.getAnalyzedAt().atZone(ZoneOffset.UTC).toOffsetDateTime(),
			contents.stream()
				.map(com.aivle.project.insight.internal.mapping.ReportContentDtoMapping::from)
				.collect(Collectors.toList()),
			entity.getCreatedAt().atZone(ZoneOffset.UTC).toOffsetDateTime()
		);
	}

}
