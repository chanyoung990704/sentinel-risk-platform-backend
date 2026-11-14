package com.aivle.project.dataimport.repository;

import com.aivle.project.dataimport.entity.*;
import com.aivle.project.dataimport.model.*;
import com.aivle.project.dataimport.model.CanonicalMetricCell.Key;
import com.aivle.project.dataimport.service.ImportPreflightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL만 수행한다. 잠금과 transaction 경계는 service가 결정한다. */
@Repository
public class ImportJournalRepository {
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	public ImportJournalRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true); }
	public DataSnapshotEntity find(String fingerprint) {
		return jdbc.query("SELECT * FROM data_snapshots WHERE fingerprint=?",this::snapshot,fingerprint).stream().findFirst().orElse(null);
	}
	public DataSnapshotEntity lock(UUID id) {
		return jdbc.queryForObject("SELECT * FROM data_snapshots WHERE id=? FOR UPDATE",this::snapshot,id.toString());
	}
	public void create(DataSnapshotEntity s, ImportStartCommand c, String policy, String manifest, String companies, String industries) {
		jdbc.update("INSERT INTO data_snapshots(id,fingerprint,source_fingerprint,canonical_checksum,metadata_checksum,policy_version,base_quarter_key,chunk_size,expected_cells,total_chunks,state,manifest_json,companies_json,industries_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
			s.id().toString(),s.fingerprint(),s.sourceFingerprint(),s.checksum(),s.metadataChecksum(),policy,c.input().manifest().baseQuarterKey(),s.chunkSize(),s.expectedCells(),s.totalChunks(),"PREPARING",manifest,companies,industries);
		for(var source:c.input().manifest().sources()) jdbc.update("INSERT INTO data_snapshot_sources(snapshot_id,source_role,source_json) VALUES(?,?,?)",s.id().toString(),source.role(),json(source));
	}
	public void createRun(UUID id,UUID snapshot,String actor) { jdbc.update("INSERT INTO import_runs(id,snapshot_id,submitted_by,state) VALUES(?,?,?,?)",id.toString(),snapshot.toString(),actor,"PREPARING"); }
	public ImportRunEntity run(UUID id) { return jdbc.queryForObject("SELECT * FROM import_runs WHERE id=?",(r,n)->new ImportRunEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("snapshot_id")),r.getString("state"),r.getInt("retry_count")),id.toString()); }
	public ImportRunEntity lockRun(UUID id) { return jdbc.queryForObject("SELECT * FROM import_runs WHERE id=? FOR UPDATE",(r,n)->new ImportRunEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("snapshot_id")),r.getString("state"),r.getInt("retry_count")),id.toString()); }
	public ImportChunkEntity chunk(UUID snapshot,int number) { return jdbc.query("SELECT * FROM import_chunks WHERE snapshot_id=? AND chunk_number=?",(r,n)->new ImportChunkEntity(r.getInt("chunk_number"),r.getInt("cell_count"),r.getString("payload_sha256"),r.getString("payload_json"),r.getString("state")),snapshot.toString(),number).stream().findFirst().orElse(null); }
	public void plan(UUID snapshot,UUID run,ImportChunkEntity c) {
		jdbc.update("INSERT INTO import_chunks(snapshot_id,chunk_number,planned_run_id,cell_count,payload_sha256,payload_json,state) VALUES(?,?,?,?,?,?,?)",snapshot.toString(),c.number(),run.toString(),c.cellCount(),c.payloadHash(),c.payload(),"PENDING");
	}
	public int planCount(UUID snapshot) { return jdbc.queryForObject("SELECT COUNT(*) FROM import_chunks WHERE snapshot_id=?",Integer.class,snapshot.toString()); }
	public void ready(UUID snapshot,UUID run) {
		jdbc.update("UPDATE data_snapshots SET state='STAGING' WHERE id=? AND state='PREPARING'",snapshot.toString());
		jdbc.update("UPDATE import_runs SET state=CASE WHEN (SELECT state FROM data_snapshots WHERE id=?) IN ('STAGED','VERIFIED') THEN 'COMPLETED' ELSE 'READY' END WHERE id=?",snapshot.toString(),run.toString());
	}
	public int next(UUID snapshot) { return jdbc.query("SELECT chunk_number FROM import_chunks WHERE snapshot_id=? AND state='PENDING' ORDER BY chunk_number LIMIT 1",(r,n)->r.getInt(1),snapshot.toString()).stream().findFirst().orElse(-1); }
	public void insertStage(List<ImportStageValueEntity> values) {
		List<Object[]> args=new ArrayList<>();
		for(var value:values){var c=value.cell();var k=c.key();args.add(new Object[]{value.snapshotId().toString(),k.stockCode(),k.quarterKey(),k.metricCode(),k.valueType(),c.value(),json(c.provenance())});}
		jdbc.batchUpdate("INSERT INTO import_stage_values(snapshot_id,stock_code,quarter_key,metric_code,value_type,metric_value,provenance_json) VALUES(?,?,?,?,?,?,?)",args);
	}
	public void commitChunk(UUID snapshot,int number,UUID run,int count) {
		int updated=jdbc.update("UPDATE import_chunks SET state='COMMITTED',committed_run_id=?,committed_at=CURRENT_TIMESTAMP(6) WHERE snapshot_id=? AND chunk_number=? AND state='PENDING'",run.toString(),snapshot.toString(),number);
		if(updated!=1)throw new IllegalStateException("checkpoint 갱신 수 불일치");
		jdbc.update("UPDATE data_snapshots SET committed_cells=committed_cells+?,committed_chunks=committed_chunks+1 WHERE id=?",count,snapshot.toString());
		if(jdbc.update("UPDATE import_runs SET state='RUNNING' WHERE id=? AND state IN ('READY','RUNNING')",run.toString())!=1)throw new IllegalStateException("실행 상태 전환 거부");
	}
	public void staged(UUID snapshot) {
		jdbc.update("UPDATE data_snapshots SET state='STAGED' WHERE id=?",snapshot.toString());
		jdbc.update("UPDATE import_runs SET state='COMPLETED' WHERE snapshot_id=? AND state<>'FAILED'",snapshot.toString());
	}
	public ImportRunStatus status(UUID run) {
		return jdbc.queryForObject("SELECT r.id,r.snapshot_id,r.state AS run_state,r.retry_count,s.state,s.expected_cells,s.committed_cells,s.total_chunks,s.committed_chunks FROM import_runs r JOIN data_snapshots s ON s.id=r.snapshot_id WHERE r.id=?",(r,n)->new ImportRunStatus(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("snapshot_id")),r.getString("run_state"),r.getString("state"),r.getLong("expected_cells"),r.getLong("committed_cells"),r.getInt("total_chunks"),r.getInt("committed_chunks"),r.getInt("retry_count")),run.toString());
	}
	public String checksum(UUID snapshot) {
		List<CanonicalMetricCell> cells=jdbc.query("SELECT stock_code,quarter_key,metric_code,value_type,metric_value FROM import_stage_values WHERE snapshot_id=? ORDER BY stock_code,quarter_key,metric_code,value_type",(r,n)->new CanonicalMetricCell(new Key(r.getString(1),r.getInt(2),r.getString(3),r.getString(4)),r.getBigDecimal(5),List.of()),snapshot.toString());
		return ImportPreflightService.checksum(cells);
	}
	public void error(ImportErrorEntity e) { jdbc.update("INSERT INTO import_errors(run_id,error_code,disposition,issue_json) VALUES(?,?,?,?)",e.runId().toString(),e.code(),e.disposition(),e.issueJson()); }
	public void retry(UUID run,String reason) { jdbc.update("UPDATE import_runs SET retry_count=retry_count+1,last_error_code=? WHERE id=?",reason,run.toString()); }
	public void failed(UUID run,String reason) { jdbc.update("UPDATE import_runs SET state='FAILED',last_error_code=? WHERE id=? AND state<>'COMPLETED'",reason,run.toString()); }
	public void resume(UUID run,boolean complete) { jdbc.update("UPDATE import_runs SET state=?,last_error_code=NULL WHERE id=?",complete?"COMPLETED":"READY",run.toString()); }
	public String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("적재 입력 직렬화 실패",e);}}
	private DataSnapshotEntity snapshot(ResultSet r,int n)throws SQLException {
		return new DataSnapshotEntity(UUID.fromString(r.getString("id")),r.getString("fingerprint"),r.getString("source_fingerprint"),r.getString("canonical_checksum"),r.getString("metadata_checksum"),r.getInt("chunk_size"),r.getLong("expected_cells"),r.getInt("total_chunks"),r.getString("state"));
	}
}
