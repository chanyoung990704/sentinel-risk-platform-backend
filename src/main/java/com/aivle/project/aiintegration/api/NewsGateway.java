package com.aivle.project.aiintegration.api;
import com.aivle.project.aiintegration.api.NewsApiResponse;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface NewsGateway {
	NewsApiResponse fetchNews(String companyCode, String companyName);
	com.aivle.project.aiintegration.api.ReportApiResponse fetchReport(String companyCode);
}
