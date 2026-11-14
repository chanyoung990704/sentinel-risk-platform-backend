package com.aivle.project.dataimport.repository;
import com.aivle.project.dataimport.model.*;
import com.aivle.project.dataimport.model.CanonicalMetricCell.*;
import com.aivle.project.dataimport.model.ImportPreflightResult.Issue;
import com.aivle.project.quarter.support.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
/** snapshot 보고서의 SQL 접근을 담당한다. */
@Repository
public class SnapshotReportRepository {
	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	public SnapshotReportRepository(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}
	public String stock(Long company) {return jdbc.queryForObject("SELECT stock_code FROM companies WHERE id=?",String.class,company);}
	public record Target(Long companyId,int quarterKey) { }
	public List<Target> targets(UUID id) {return jdbc.query("SELECT c.id,s.quarter_key FROM import_stage_values s LEFT JOIN companies c ON c.stock_code=s.stock_code WHERE s.snapshot_id=? GROUP BY c.id,s.stock_code,s.quarter_key ORDER BY s.stock_code,s.quarter_key",(r,n)->new Target(r.getObject(1,Long.class),r.getInt(2)),id.toString());}
	public long quarter(int key) {
		var q=QuarterCalculator.parseQuarterKey(key);
		jdbc.update("INSERT INTO quarters(year,quarter,quarter_key,start_date,end_date) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE quarter_key=VALUES(quarter_key)",q.year(),q.quarter(),key,QuarterCalculator.startDate(q),QuarterCalculator.endDate(q));
		return jdbc.queryForObject("SELECT id FROM quarters WHERE quarter_key=?",Long.class,key);
	}
	public long report(Long company,long quarter) {
		jdbc.update("INSERT INTO company_reports(company_id,quarter_id) VALUES(?,?) ON DUPLICATE KEY UPDATE company_id=VALUES(company_id)",company,quarter);
		return jdbc.queryForObject("SELECT id FROM company_reports WHERE company_id=? AND quarter_id=? FOR UPDATE",Long.class,company,quarter);
	}
	public Optional<Long> version(UUID id,Long company,int quarter) {
		return jdbc.query("SELECT b.report_version_id FROM snapshot_report_versions b JOIN quarters q ON q.id=b.quarter_id WHERE b.snapshot_id=? AND b.company_id=? AND q.quarter_key=?",(r,n)->r.getLong(1),id.toString(),company,quarter).stream().findFirst();
	}
	public long createVersion(UUID id,long report) {
		int next=jdbc.query("SELECT version_no FROM company_report_versions WHERE company_report_id=? ORDER BY version_no DESC LIMIT 1 FOR UPDATE",(r,n)->r.getInt(1),report).stream().findFirst().orElse(0)+1;
		GeneratedKeyHolder key=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO company_report_versions(company_report_id,version_no,generated_at,is_published,data_snapshot_id) VALUES(?,?,CURRENT_TIMESTAMP(6),0,?)",Statement.RETURN_GENERATED_KEYS);p.setLong(1,report);p.setInt(2,next);p.setString(3,id.toString());return p;},key);return Objects.requireNonNull(key.getKey()).longValue();
	}
	public void bridge(UUID id,Long company,long quarter,long version) {jdbc.update("INSERT INTO snapshot_report_versions(snapshot_id,company_id,quarter_id,report_version_id) VALUES(?,?,?,?)",id.toString(),company,quarter,version);}
	public void store(long version,long quarter,List<CanonicalMetricCell> cells) {
		List<Object[]> args=new ArrayList<>();
		for(var c:cells) {Long metric=jdbc.queryForObject("SELECT id FROM metrics WHERE metric_code=?",Long.class,c.key().metricCode());args.add(new Object[]{version,metric,quarter,c.value(),c.key().valueType()});}
		jdbc.batchUpdate("INSERT INTO company_report_metric_values(report_version_id,metric_id,quarter_id,metric_value,value_type) VALUES(?,?,?,?,?)",args);
	}
	public List<CanonicalMetricCell> staged(UUID id) {
		return jdbc.query("SELECT stock_code,quarter_key,metric_code,value_type,metric_value,provenance_json FROM import_stage_values WHERE snapshot_id=? ORDER BY stock_code,quarter_key,metric_code,value_type",(r,n)->new CanonicalMetricCell(new Key(r.getString(1),r.getInt(2),r.getString(3),r.getString(4)),r.getBigDecimal(5),decode(r.getString(6),new TypeReference<List<Provenance>>(){})),id.toString());
	}
	public List<CanonicalMetricCell> stagedGroup(UUID id,String stock,int quarter) {
		return jdbc.query("SELECT stock_code,quarter_key,metric_code,value_type,metric_value,provenance_json FROM import_stage_values WHERE snapshot_id=? AND stock_code=? AND quarter_key=? ORDER BY metric_code",(r,n)->new CanonicalMetricCell(new Key(r.getString(1),r.getInt(2),r.getString(3),r.getString(4)),r.getBigDecimal(5),decode(r.getString(6),new TypeReference<List<Provenance>>(){})),id.toString(),stock,quarter);
	}
	public List<CanonicalMetricCell> materialized(UUID id) {
		return readMaterialized("",id.toString());
	}
	public List<CanonicalMetricCell> materializedRange(UUID id,Long company,int from,int to) {
		return readMaterialized(" AND cr.company_id=? AND q.quarter_key BETWEEN ? AND ?",id.toString(),company,from,to);
	}
	private List<CanonicalMetricCell> readMaterialized(String restriction,Object... args) {
		// ASCII 키 쪽을 UTF-8로 바꾸면 복합 PK의 나머지 열을 쓰지 못하므로 비교값을 맞춘다.
		String sql="SELECT c.stock_code,q.quarter_key,m.metric_code,v.value_type,v.metric_value,s.provenance_json"
			+" FROM company_report_metric_values v JOIN company_report_versions rv ON rv.id=v.report_version_id"
			+" JOIN company_reports cr ON cr.id=rv.company_report_id JOIN companies c ON c.id=cr.company_id"
			+" JOIN quarters q ON q.id=v.quarter_id JOIN metrics m ON m.id=v.metric_id"
			+" LEFT JOIN import_stage_values s ON s.snapshot_id=rv.data_snapshot_id"
			+" AND s.stock_code=CONVERT(c.stock_code USING ascii) COLLATE ascii_bin AND s.quarter_key=q.quarter_key"
			+" AND s.metric_code=CONVERT(m.metric_code USING ascii) COLLATE ascii_bin"
			+" AND s.value_type=CONVERT(v.value_type USING ascii) COLLATE ascii_bin"
			+" WHERE rv.data_snapshot_id=?"+restriction+" ORDER BY c.stock_code,q.quarter_key,m.metric_code,v.value_type";
		return jdbc.query(sql,(r,n)->new CanonicalMetricCell(new Key(r.getString(1),r.getInt(2),r.getString(3),r.getString(4)),r.getBigDecimal(5),r.getString(6)==null?List.of():decode(r.getString(6),new TypeReference<List<Provenance>>(){})),args).stream().sorted(Comparator.comparing(CanonicalMetricCell::key)).toList();
	}
	public Map<String,Object> metadata(UUID id) {return jdbc.queryForMap("SELECT * FROM data_snapshots WHERE id=?",id.toString());}
	public String state(UUID id) {return jdbc.queryForObject("SELECT state FROM data_snapshots WHERE id=?",String.class,id.toString());}
	public List<Issue> issues(UUID id) {
		return jdbc.query("SELECT issue_json FROM import_errors WHERE run_id=(SELECT id FROM import_runs WHERE snapshot_id=? ORDER BY created_at,id LIMIT 1) AND disposition IN ('EXCLUDED','WARNING') ORDER BY id",(r,n)->decode(r.getString(1),new TypeReference<Issue>(){}),id.toString());
	}
	public List<CanonicalMetricCell> planned(UUID id) {
		List<CanonicalMetricCell> result=new ArrayList<>();
		jdbc.query("SELECT payload_json,payload_sha256,state,cell_count FROM import_chunks WHERE snapshot_id=? ORDER BY chunk_number",r->{String payload=r.getString(1);if(!com.aivle.project.dataimport.service.ImportPreflightService.sha256(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(r.getString(2)) || !r.getString(3).equals("COMMITTED"))throw new IllegalStateException("입력 계획/checkpoint 손상");List<CanonicalMetricCell> cells=decode(payload,new TypeReference<List<CanonicalMetricCell>>(){});if(cells.size()!=r.getInt(4))throw new IllegalStateException("입력 계획 행수 불일치");result.addAll(cells);},id.toString());
		return result.stream().sorted(Comparator.comparing(CanonicalMetricCell::key)).toList();
	}
	public boolean bridgesMatch(UUID id,int groups) {
		long bridges=jdbc.queryForObject("SELECT COUNT(*) FROM snapshot_report_versions WHERE snapshot_id=?",Long.class,id.toString());
		long versions=jdbc.queryForObject("SELECT COUNT(*) FROM company_report_versions WHERE data_snapshot_id=?",Long.class,id.toString());
		long invalid=jdbc.queryForObject("SELECT COUNT(*) FROM snapshot_report_versions b JOIN company_report_versions rv ON rv.id=b.report_version_id JOIN company_reports cr ON cr.id=rv.company_report_id WHERE b.snapshot_id=? AND (cr.company_id<>b.company_id OR cr.quarter_id<>b.quarter_id OR rv.data_snapshot_id IS NULL OR rv.data_snapshot_id<>b.snapshot_id)",Long.class,id.toString());
		return bridges==groups && versions==groups && invalid==0;
	}
	public void verified(UUID id) {if(jdbc.update("UPDATE data_snapshots SET state='VERIFIED' WHERE id=? AND state IN ('STAGED','VERIFIED')",id.toString())!=1)throw new IllegalStateException("검증 상태 전환 거부");}
	public Optional<UUID> active(String family) {return jdbc.query("SELECT snapshot_id FROM active_data_snapshots WHERE family=? AND snapshot_id IS NOT NULL",(r,n)->UUID.fromString(r.getString(1)),family).stream().findFirst();}
	public void lockPointer(String family) {jdbc.queryForObject("SELECT family FROM active_data_snapshots WHERE family=? FOR UPDATE",String.class,family);}
	public void switchPointer(String family,UUID id) {if(jdbc.update("UPDATE active_data_snapshots SET snapshot_id=?,generation=generation+1,updated_at=CURRENT_TIMESTAMP(6) WHERE family=?",id.toString(),family)!=1)throw new IllegalStateException("활성 포인터 없음");}
	public <T> T decode(String value,TypeReference<T> type) {try{return mapper.readValue(value,type);}catch(Exception error){throw new IllegalStateException("snapshot 원본 기록 손상",error);}}
}
