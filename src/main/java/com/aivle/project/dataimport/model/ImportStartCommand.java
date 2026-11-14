package com.aivle.project.dataimport.model;

/** 제출 이력과 논리 데이터셋을 구분한다. 기본 정책은 오류 없는 입력만 허용한다. */
public record ImportStartCommand(ImportPreflightResult input, String submittedBy, int chunkSize,
	QuarantinePolicy policy) {
	public enum QuarantinePolicy { STRICT, SENTINEL_ARCHIVE_CODE_GAP_V1 }
}
