package com.aivle.project.finance.internal.dataimport.model;

import java.util.List;
import java.util.UUID;

/** 원본/불변 입력과 staging·업무 보고서의 전체 대조 결과. */
public record ImportVerificationResult(UUID snapshotId, String basis, String state, long expectedKeys,
	long expectedNulls, String expectedChecksum, Layer stage, Layer report,
	long quarantineErrors, long warnings, List<String> contractIssues) {
	public long unexpectedDifferenceCount() {
		return contractIssues.size() + stage.differences() + report.differences();
	}
	public boolean valid() { return unexpectedDifferenceCount() == 0; }
	public record Layer(long actualRows, long actualKeys, long nulls, long zeros, long missing,
		long unexpected, long different, long duplicateKeys, long invalidQuarters, String checksum) {
		public long differences() { return missing + unexpected + different + duplicateKeys + invalidQuarters; }
	}
}
