package com.aivle.project.community.api;


/**
 * 카테고리 요약 응답 DTO.
 */
public record CategorySummaryResponse(
	Long id,
	String name,
	String description,
	int sortOrder,
	boolean active
) {
}
