package com.aivle.project.aiintegration.api;

/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface AiJobs {
	boolean dispatchReport(String requestId, Long companyId, Integer year, Integer quarter);
	boolean dispatchCommentWarmup(String requestId, Long companyId, String period);
}
