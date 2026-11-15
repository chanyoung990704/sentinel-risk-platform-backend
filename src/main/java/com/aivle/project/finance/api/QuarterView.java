package com.aivle.project.finance.api;
public record QuarterView(Long id, short year, byte quarter, int quarterKey) implements QuarterIdentity {
	public Long getId() { return id; }
	public short getYear() { return year; }
	public byte getQuarter() { return quarter; }
	public int getQuarterKey() { return quarterKey; }
}
