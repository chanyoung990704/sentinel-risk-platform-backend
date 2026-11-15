package com.aivle.project.reporting.api;
import java.util.UUID;
import java.time.Instant;
/** 요청 소유자 검증 후 노출되는 상태의 값 사본. */
@io.swagger.v3.oas.annotations.media.Schema(name="Status")
public record ReportRequestView(UUID requestId,String status,long stateVersion,int generation,int attemptNo,
	String errorCode,Instant retryAt,String resultUrl) { }
