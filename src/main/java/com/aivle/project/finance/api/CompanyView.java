package com.aivle.project.finance.api;
public record CompanyView(Long id, String corpName, String corpCode, String stockCode, IndustryView industryCode) implements CompanyIdentity {
	public CompanyView(Long id, String corpName, String corpCode, String stockCode) { this(id,corpName,corpCode,stockCode,null); }
	public static CompanyView copyOf(CompanyIdentity source) {
		return new CompanyView(source.getId(), source.getCorpName(), source.getCorpCode(), source.getStockCode(), IndustryView.copyOf(source.getIndustryCode()));
	}
	public Long getId() { return id; }
	public String getCorpName() { return corpName; }
	public String getCorpCode() { return corpCode; }
	public String getStockCode() { return stockCode; }
	public IndustryIdentity getIndustryCode() { return industryCode; }
}
