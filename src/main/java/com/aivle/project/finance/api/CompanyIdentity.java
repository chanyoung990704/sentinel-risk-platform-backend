package com.aivle.project.finance.api;
/** 기업을 참조할 때 필요한 최소 정보. JPA 연관 대신 ID/불변 조회값으로 사용한다. */
public interface CompanyIdentity {
	Long getId();
	String getCorpName();
	String getCorpCode();
	String getStockCode();
	IndustryIdentity getIndustryCode();
}
