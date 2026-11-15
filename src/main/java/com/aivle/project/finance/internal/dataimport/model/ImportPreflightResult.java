package com.aivle.project.finance.internal.dataimport.model;
import com.aivle.project.finance.api.CanonicalMetricCell;

import java.util.List;

public record ImportPreflightResult(ImportManifest manifest, List<CanonicalMetricCell> cells,
	List<Company> companies, List<Industry> industries, List<Issue> issues, List<Comparison> comparisons) {
	public ImportPreflightResult {
		cells = List.copyOf(cells);
		companies = List.copyOf(companies);
		industries = List.copyOf(industries);
		issues = List.copyOf(issues);
		comparisons = List.copyOf(comparisons);
	}
	public boolean ready() { return issues.stream().noneMatch(i -> i.severity().equals("ERROR")); }
	public record Company(String stockCode, String corpCode, String name,
		CanonicalMetricCell.Provenance provenance) { }
	public record Industry(String stockCode, String code, String name,
		CanonicalMetricCell.Provenance provenance) { }
	public record Issue(String severity, String code, CanonicalMetricCell.Provenance provenance,
		String key, String raw, String detail) { }
	public record Comparison(String role, long primaryKeys, long referenceKeys, long missing,
		long unexpected, long different, String primaryChecksum, String referenceChecksum) { }
}
