package com.aivle.project.aiintegration.api;

/** 기존 AI 제공자 프로토콜. 호출자는 HTTP 클라이언트 구현을 알 필요가 없다. */
public interface AiGateway {
	AiAnalysisResponse getPrediction(String companyCode);
	byte[] getAnalysisReportPdf(String companyCode);
	AiHealthScoreResponse getHealthScore(String companyCode);
	AiSignalResponse getSignals(String companyCode, String period);
	AiCommentResponse getAiComment(String companyCode, String period);
}
