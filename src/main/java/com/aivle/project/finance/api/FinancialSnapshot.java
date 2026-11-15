package com.aivle.project.finance.api;
import java.util.List;
public record FinancialSnapshot(int schemaVersion, SourceRef source, List<CanonicalMetricCell> cells, String checksum) {
	public FinancialSnapshot { cells = List.copyOf(cells); }
}
