package com.aivle.project.finance.internal;

import com.aivle.project.finance.api.*;
import com.aivle.project.finance.internal.dataimport.repository.SnapshotReportRepository;
import com.aivle.project.finance.internal.dataimport.service.ImportPreflightService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialSourceService implements FinancialSources {
	private final JdbcTemplate jdbc;
	private final SnapshotReportRepository reports;
	@Override public Optional<ActiveSource> active() {
		return jdbc.query("SELECT s.id,s.base_quarter_key FROM active_data_snapshots a JOIN data_snapshots s ON s.id=a.snapshot_id WHERE a.family='FINANCIAL' AND s.state='VERIFIED'",
			(row, n) -> new ActiveSource(UUID.fromString(row.getString(1)), row.getInt(2))).stream().findFirst();
	}
	@Override public boolean companyExists(long id) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM companies WHERE id=? AND deleted_at IS NULL", Integer.class, id) == 1;
	}
	@Override public boolean hasValues(UUID snapshot, long company, int quarter) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM company_report_metric_values v JOIN company_report_versions rv ON rv.id=v.report_version_id JOIN company_reports cr ON cr.id=rv.company_report_id JOIN quarters q ON q.id=v.quarter_id WHERE rv.data_snapshot_id=? AND cr.company_id=? AND q.quarter_key=? AND v.value_type='ACTUAL' AND v.metric_value IS NOT NULL",
			Integer.class, snapshot.toString(), company, quarter) > 0;
	}
	@Override public FinancialSnapshot read(SourceRef reference) {
		if (reference.snapshotId() == null || reference.companyId() == null || !reports.state(reference.snapshotId()).equals("VERIFIED"))
			throw new FinancialSourceException("SOURCE_INVALID");
		int from = QuarterCalculator.offset(QuarterCalculator.parseQuarterKey(reference.targetQuarterKey()), -3).toQuarterKey();
		// 모델 입력에는 원본의 로컬 경로·시트 출처를 포함하지 않는다.
		var cells = reports.materializedRange(reference.snapshotId(), reference.companyId(), from, reference.targetQuarterKey()).stream()
			.filter(cell -> cell.key().valueType().equals("ACTUAL"))
			.map(cell -> new CanonicalMetricCell(cell.key(), cell.value(), List.of())).toList();
		if (cells.stream().noneMatch(cell -> cell.key().quarterKey() == reference.targetQuarterKey() && cell.value() != null))
			throw new FinancialSourceException("SOURCE_EMPTY");
		return new FinancialSnapshot(1, reference, cells, ImportPreflightService.checksum(cells));
	}
}
