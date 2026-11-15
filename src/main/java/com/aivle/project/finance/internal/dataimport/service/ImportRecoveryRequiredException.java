package com.aivle.project.finance.internal.dataimport.service;

/** DB 감사도 기록하지 못했을 때 호출자에게 복구 판단을 요구한다. */
public class ImportRecoveryRequiredException extends IllegalStateException {
	public ImportRecoveryRequiredException(Throwable cause, Throwable auditFailure) {
		super("적재 오류와 감사 저장 실패: DB 복구 후 실행 상태 확인 필요",cause);
		addSuppressed(auditFailure);
	}
}
