package com.aivle.project.news.api;
import com.aivle.project.news.api.NewsAnalysisResponse;
import com.aivle.project.news.api.NewsRefreshResponse;
import java.time.LocalDateTime;
import java.util.List;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface NewsOperations {
	NewsAnalysisResponse fetchAndStoreNews(String stockCode);
	NewsRefreshResponse refreshLatestNews(String stockCode);
	NewsAnalysisResponse getLatestNews(String stockCode);
	List<NewsAnalysisResponse> getNewsHistory(String stockCode, LocalDateTime start, LocalDateTime end);
}
