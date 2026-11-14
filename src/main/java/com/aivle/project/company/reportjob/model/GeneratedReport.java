package com.aivle.project.company.reportjob.model;
public record GeneratedReport(byte[] bytes,String sha256) {
	public GeneratedReport {bytes=bytes.clone();}
	@Override public byte[] bytes(){return bytes.clone();}
}
