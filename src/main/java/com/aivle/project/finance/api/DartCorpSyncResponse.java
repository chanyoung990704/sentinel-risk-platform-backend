package com.aivle.project.finance.api;

/**
 * DART 기업 목록 동기화 실행 응답.
 */
public record DartCorpSyncResponse(
	Long jobExecutionId,
	String status
) {
}
