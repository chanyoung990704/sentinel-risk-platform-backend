package com.aivle.project.finance.api;
public record IndustryView(String industryCode, String industryName) implements IndustryIdentity {
	public String getIndustryCode() { return industryCode; }
	public String getIndustryName() { return industryName; }
	public static IndustryView copyOf(IndustryIdentity source) {
		return source == null ? null : new IndustryView(source.getIndustryCode(),source.getIndustryName());
	}
}
