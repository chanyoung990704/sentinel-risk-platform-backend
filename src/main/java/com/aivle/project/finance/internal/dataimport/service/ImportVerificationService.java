package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.api.CanonicalMetricCell.Key;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.finance.internal.dataimport.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 동일한 읽기 transaction에서 전체 키·값·NULL·출처와 마스터를 대조한다. */
@Service
public class ImportVerificationService {
	private final SnapshotReportRepository snapshots;
	private final ImportReconciliationRepository reconciliation;
	private final ImportInputValidator validator;
	public ImportVerificationService(SnapshotReportRepository snapshots,ImportReconciliationRepository reconciliation,ImportInputValidator validator) {this.snapshots=snapshots;this.reconciliation=reconciliation;this.validator=validator;}
	@Transactional(readOnly=true,timeout=180)
	public ImportVerificationResult verify(UUID id) {return compare(id,null);}
	@Transactional(readOnly=true,timeout=180)
	public ImportVerificationResult verify(UUID id,ImportPreflightResult source) {return compare(id,Objects.requireNonNull(source));}
	private ImportVerificationResult compare(UUID id,ImportPreflightResult source) {
		if(!reconciliation.metadataIntact(id))throw new IllegalStateException("snapshot 메타데이터 손상");
		var row=snapshots.metadata(id);var metadata=reconciliation.metadata(id);var planned=snapshots.planned(id);
		var durable=new ImportPreflightResult(metadata.manifest(),planned,metadata.companies(),metadata.industries(),metadata.issues(),List.of());
		var expected=source==null?durable:source;List<String> issues=new ArrayList<>();
		try {validator.validate(new ImportStartCommand(durable,"verification",((Number)row.get("chunk_size")).intValue(),QuarantinePolicy.valueOf((String)row.get("policy_version"))));}
		catch(IllegalArgumentException invalid){issues.add("DURABLE_INPUT_POLICY: "+invalid.getMessage());}
		if(source!=null) {
			try {validator.validate(new ImportStartCommand(source,"raw-verification",((Number)row.get("chunk_size")).intValue(),QuarantinePolicy.valueOf((String)row.get("policy_version"))));}
			catch(IllegalArgumentException invalid){issues.add("SOURCE_INPUT_POLICY: "+invalid.getMessage());}
			if(!source.manifest().equals(metadata.manifest()) || !source.companies().equals(metadata.companies()) || !source.industries().equals(metadata.industries()))issues.add("SOURCE_IDENTITY_MISMATCH");
		}
		if(!Set.of("STAGED","VERIFIED").contains(row.get("state")))issues.add("INCOMPLETE_SNAPSHOT");
		if(!reconciliation.sourcesMatch(id,metadata.manifest()))issues.add("SOURCE_AUDIT_MISMATCH");
		long masterDiff=reconciliation.masterDifferences(expected);if(masterDiff>0)issues.add("MASTER_DIFFERENCES: "+masterDiff);
		int groups=(int)expected.cells().stream().map(c->c.key().stockCode()+":"+c.key().quarterKey()).distinct().count();
		if(!snapshots.bridgesMatch(id,groups))issues.add("VERSION_BRIDGE_MISMATCH");
		if(reconciliation.wrongReportQuarters(id)>0)issues.add("REPORT_QUARTER_LINK_MISMATCH");
		Set<Integer> quarters=new HashSet<>();for(int n=0;n<4;n++)quarters.add(ImportPreflightService.offsetQuarter(expected.manifest().baseQuarterKey(),-n));
		var stage=layer(expected.cells(),snapshots.staged(id),quarters);
		var report=layer(expected.cells(),snapshots.materialized(id),quarters);
		return new ImportVerificationResult(id,source==null?"DB_PAYLOAD":"RAW_FILES",(String)row.get("state"),expected.cells().size(),expected.cells().stream().filter(c->c.value()==null).count(),ImportPreflightService.checksum(expected.cells()),stage,report,metadata.issues().stream().filter(i->i.severity().equals("ERROR")).count(),metadata.issues().stream().filter(i->i.severity().equals("WARNING")).count(),List.copyOf(issues));
	}
	private ImportVerificationResult.Layer layer(List<CanonicalMetricCell> expected,List<CanonicalMetricCell> actual,Set<Integer> quarters) {
		Map<Key,CanonicalMetricCell> baseline=new TreeMap<>(),stored=new TreeMap<>();expected.forEach(c->baseline.put(c.key(),c));
		long duplicates=0;for(var cell:actual)if(stored.putIfAbsent(cell.key(),cell)!=null)duplicates++;
		long missing=baseline.keySet().stream().filter(k->!stored.containsKey(k)).count(),extra=stored.keySet().stream().filter(k->!baseline.containsKey(k)).count();
		long different=baseline.entrySet().stream().filter(e->{var value=stored.get(e.getKey());return value!=null && (!Objects.equals(e.getValue().value(),value.value()) || !e.getValue().provenance().equals(value.provenance()));}).count();
		return new ImportVerificationResult.Layer(actual.size(),stored.size(),actual.stream().filter(c->c.value()==null).count(),actual.stream().filter(c->c.value()!=null && c.value().signum()==0).count(),missing,extra,different,duplicates,actual.stream().filter(c->!quarters.contains(c.key().quarterKey())).count(),ImportPreflightService.checksum(actual));
	}
}
