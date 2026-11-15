package com.aivle.project.finance.api;
import com.aivle.project.finance.api.CompanySearchResponse;
import java.util.List;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface CompanySearchQueries {
	List<CompanySearchResponse> search(String keyword);
}
