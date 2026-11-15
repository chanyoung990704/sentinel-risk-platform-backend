package com.aivle.project.aiintegration.api;

/** PDF 전송만 수행하며 업무 재시도나 보고서 상태 저장을 수행하지 않는다. */
public interface AiReportTransport {
	int MAX_BYTES = 10 * 1024 * 1024;
	byte[] generatePdf(byte[] json);
}
