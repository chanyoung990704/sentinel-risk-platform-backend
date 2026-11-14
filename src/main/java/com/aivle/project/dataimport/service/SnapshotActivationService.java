package com.aivle.project.dataimport.service;
import com.aivle.project.dataimport.model.CanonicalMetricCell;
import java.util.*;
import com.aivle.project.dataimport.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SnapshotActivationService {
	public static final String FAMILY="FINANCIAL";
	private final ImportJournalRepository imports;private final SnapshotReportRepository reports;private final SnapshotVerificationService verifier;
	public SnapshotActivationService(ImportJournalRepository imports,SnapshotReportRepository reports,SnapshotVerificationService verifier){this.imports=imports;this.reports=reports;this.verifier=verifier;}
	@Transactional(timeout=60)
	public void verify(UUID id) {imports.lock(id);verifier.check(id);reports.verified(id);}
	@Transactional(timeout=60)
	public void activate(UUID id) {
		var snapshot=imports.lock(id);if(!snapshot.state().equals("VERIFIED"))throw new IllegalStateException("VERIFIED만 활성화 가능");
		verifier.check(id);reports.lockPointer(FAMILY);reports.switchPointer(FAMILY,id);
	}
	@Transactional(readOnly=true)
	public Optional<UUID> resolveActiveOrLegacy(String family) {if(!FAMILY.equals(family))throw new IllegalArgumentException("지원하지 않는 snapshot family");return reports.active(family);}
	@Transactional(readOnly=true)
	public List<CanonicalMetricCell> load(UUID id,Long company,int from,int to) {
		if(!reports.state(id).equals("VERIFIED"))throw new IllegalStateException("명시 조회는 VERIFIED만 허용");
		ImportPreflightService.offsetQuarter(from,0);ImportPreflightService.offsetQuarter(to,0);if(from>to)throw new IllegalArgumentException("분기 범위 역전");
		return reports.materializedRange(id,company,from,to);
	}
}
