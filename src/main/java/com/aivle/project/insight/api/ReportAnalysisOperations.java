package com.aivle.project.insight.api;
import com.aivle.project.insight.api.ReportAnalysisResponse;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface ReportAnalysisOperations {
	ReportAnalysisResponse fetchAndStoreReport(String stockCode);
	ReportAnalysisResponse getLatestReport(String stockCode);
}
