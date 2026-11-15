package com.aivle.project.finance.api;

/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface PredictionWarmup {
	void ensurePredictionCached(Long companyId, int requestedQuarterKey);
}
