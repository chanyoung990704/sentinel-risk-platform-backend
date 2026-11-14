package com.aivle.project.company.reportjob.model;
/** 외부 오류 본문/사용자 원문 대신 제한된 코드와 재시도 분류만 보존한다. */
public class ReportGenerationFailure extends RuntimeException {
	private final String code;private final boolean retryable;
	public ReportGenerationFailure(String code,boolean retryable){super(code);this.code=code;this.retryable=retryable;}
	public String code(){return code;}public boolean retryable(){return retryable;}
}
