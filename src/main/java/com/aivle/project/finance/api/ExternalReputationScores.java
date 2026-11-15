package com.aivle.project.finance.api;
import java.util.Optional;
import java.math.BigDecimal;
/** 외부 해석 점수 조회 SPI. 재무 모듈이 뉴스 저장소에 접근하지 않게 한다. */
public interface ExternalReputationScores {
	Optional<BigDecimal> latest(Long companyId,String stockCode,boolean fetchIfMissing);
	java.util.Map<Long,BigDecimal> latestScores(java.util.Collection<Long> companyIds);
}
