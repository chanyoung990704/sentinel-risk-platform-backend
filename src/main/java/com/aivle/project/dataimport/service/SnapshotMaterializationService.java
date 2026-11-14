package com.aivle.project.dataimport.service;
import java.util.UUID;
import com.aivle.project.dataimport.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SnapshotMaterializationService {
	private final ImportJournalRepository imports;private final SnapshotReportRepository reports;
	public SnapshotMaterializationService(ImportJournalRepository imports,SnapshotReportRepository reports){this.imports=imports;this.reports=reports;}
	@Transactional(timeout=30)
	public long materialize(UUID snapshotId,Long companyId,int quarterKey) {
		var snapshot=imports.lock(snapshotId);
		if(!snapshot.state().equals("STAGED") && !snapshot.state().equals("VERIFIED"))throw new IllegalStateException("staging 미완료");
		var existing=reports.version(snapshotId,companyId,quarterKey);if(existing.isPresent())return existing.get();
		if(snapshot.state().equals("VERIFIED"))throw new IllegalStateException("검증 완료 snapshot 변경 거부");
		String stock=reports.stock(companyId);var cells=reports.stagedGroup(snapshotId,stock,quarterKey);
		if(cells.isEmpty())throw new IllegalArgumentException("기업/분기 입력 없음");
		long quarter=reports.quarter(quarterKey),report=reports.report(companyId,quarter),version=reports.createVersion(snapshotId,report);
		reports.store(version,quarter,cells);reports.bridge(snapshotId,companyId,quarter,version);return version;
	}
}
