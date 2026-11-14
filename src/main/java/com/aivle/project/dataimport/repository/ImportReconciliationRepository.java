package com.aivle.project.dataimport.repository;

import com.aivle.project.dataimport.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 대조용 원본 메타데이터와 마스터 연결을 읽는다. */
@Repository
public class ImportReconciliationRepository {
	private final JdbcTemplate jdbc;
	private final SnapshotReportRepository snapshots;
	public ImportReconciliationRepository(JdbcTemplate jdbc,SnapshotReportRepository snapshots) { this.jdbc=jdbc;this.snapshots=snapshots; }
	public ImportPreflightResult metadata(UUID id) {
		var row=snapshots.metadata(id);
		String manifest=(String)row.get("manifest_json"),companies=(String)row.get("companies_json"),industries=(String)row.get("industries_json");
		return new ImportPreflightResult(snapshots.decode(manifest,new TypeReference<ImportManifest>(){}),List.of(),snapshots.decode(companies,new TypeReference<List<ImportPreflightResult.Company>>(){}),snapshots.decode(industries,new TypeReference<List<ImportPreflightResult.Industry>>(){}),snapshots.issues(id),List.of());
	}
	public boolean metadataIntact(UUID id) {
		var row=snapshots.metadata(id);String data=row.get("manifest_json")+"\n"+row.get("companies_json")+"\n"+row.get("industries_json");
		return com.aivle.project.dataimport.service.ImportPreflightService.sha256(data.getBytes(StandardCharsets.UTF_8)).equals(row.get("metadata_checksum"));
	}
	public long masterDifferences(ImportPreflightResult input) {
		Map<String,Map<String,Object>> stored=new HashMap<>();
		jdbc.queryForList("SELECT c.stock_code,c.corp_code,c.corp_name,c.deleted_at,i.industry_code,i.industry_name,i.deleted_at AS industry_deleted_at FROM companies c LEFT JOIN industry_codes i ON i.id=c.industry_code_id").forEach(r->stored.put((String)r.get("stock_code"),r));
		long differences=0;
		for(var c:input.companies()) {var row=stored.get(c.stockCode());if(row==null || row.get("deleted_at")!=null || !c.corpCode().equals(row.get("corp_code")) || !c.name().equals(row.get("corp_name")))differences++;}
		for(var i:input.industries()) {var row=stored.get(i.stockCode());if(row==null || row.get("industry_deleted_at")!=null || !i.code().equals(row.get("industry_code")) || (i.name()!=null && !i.name().equals(row.get("industry_name"))))differences++;}
		return differences;
	}
	public boolean sourcesMatch(UUID id,ImportManifest manifest) {
		var actual=jdbc.query("SELECT source_json FROM data_snapshot_sources WHERE snapshot_id=? ORDER BY source_role",(r,n)->snapshots.decode(r.getString(1),new TypeReference<ImportManifest.SourceManifest>(){}),id.toString());
		return actual.equals(manifest.sources().stream().sorted(Comparator.comparing(ImportManifest.SourceManifest::role)).toList());
	}
	public long wrongReportQuarters(UUID id) {return jdbc.queryForObject("SELECT COUNT(*) FROM company_report_metric_values v JOIN company_report_versions rv ON rv.id=v.report_version_id JOIN company_reports cr ON cr.id=rv.company_report_id WHERE rv.data_snapshot_id=? AND v.quarter_id<>cr.quarter_id",Long.class,id.toString());}
}
