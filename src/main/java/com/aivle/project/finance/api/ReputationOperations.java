package com.aivle.project.finance.api;
import java.math.BigDecimal;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface ReputationOperations {
	void syncExternalHealthScoreIfPresent(Long companyId, String stockCode);
	BigDecimal resolveLatestAverageScore(Long companyId, String stockCode);
}
