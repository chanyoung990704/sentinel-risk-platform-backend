package com.aivle.project.finance.api;
import com.aivle.project.aiintegration.api.AiAnalysisResponse;
import com.aivle.project.storage.api.FileMetadata;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface LegacyFinancialAi {
	AiAnalysisResponse getCompanyAnalysis(Long companyId, Integer year, Integer quarter);
	FileMetadata generateAndSaveReport(Long companyId, Integer year, Integer quarter);
	FileMetadata getReportFile(String companyCode, Integer year, Integer quarter);
	FileMetadata getReportFileById(Long companyId, Integer year, Integer quarter);
	void generateReportAsync(String requestId, Long companyId, Integer year, Integer quarter);
	void processReportGeneration(String requestId, Long companyId, Integer year, Integer quarter);
	FileMetadata storeAndLinkReport(Long companyId, int targetYear, int targetQuarter, byte[] pdfContent);
}
