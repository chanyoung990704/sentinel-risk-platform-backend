package com.aivle.project.finance.api;
import java.util.Optional;
import java.util.UUID;
public interface FinancialSources {
	record ActiveSource(UUID id, int baseQuarter) { }
	Optional<ActiveSource> active();
	boolean companyExists(long companyId);
	boolean hasValues(UUID snapshotId, long companyId, int quarter);
	FinancialSnapshot read(SourceRef reference);
}
