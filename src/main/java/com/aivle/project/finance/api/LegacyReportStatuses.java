package com.aivle.project.finance.api;
import com.aivle.project.finance.api.AiReportStatusResponse;
import java.util.Optional;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface LegacyReportStatuses {
	void createPending(String requestId,String companyCode,Integer year,Integer quarter);
	Optional<AiReportStatusResponse> getStatus(String requestId);
}
