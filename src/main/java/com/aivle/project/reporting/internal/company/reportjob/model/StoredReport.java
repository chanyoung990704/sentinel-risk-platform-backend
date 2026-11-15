package com.aivle.project.reporting.internal.company.reportjob.model;
import java.util.UUID;
public record StoredReport(UUID requestId,int generation,UUID attemptId,String key,long size,String sha256) {
	/** 이 port는 PDF만 저장하므로 MIME은 변경 가능한 입력으로 받지 않는다. */
	public String contentType(){return "application/pdf";}
}
