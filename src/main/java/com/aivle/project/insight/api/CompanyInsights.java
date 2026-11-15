package com.aivle.project.insight.api;
import com.aivle.project.insight.api.CompanyInsightResult;
import com.aivle.project.aiintegration.api.ExternalAiUnavailableException;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface CompanyInsights {
	CompanyInsightResult getInsights(Long companyId, int newsPage, int newsSize, int reportPage, int reportSize, boolean refresh);
	ExternalAiUnavailableException ensureInsightData(Long companyId, boolean refresh);
	CompanyInsightResult loadInsights(Long companyId, int newsPage, int newsSize, int reportPage, int reportSize, ExternalAiUnavailableException externalFailure);
}
