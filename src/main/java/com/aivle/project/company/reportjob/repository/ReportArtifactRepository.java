package com.aivle.project.company.reportjob.repository;
import com.aivle.project.company.reportjob.model.StoredReport;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
@Repository public class ReportArtifactRepository {
	private final JdbcTemplate jdbc;public ReportArtifactRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public Optional<StoredReport> find(UUID request){return jdbc.query("SELECT * FROM report_artifacts WHERE request_id=?",(r,n)->new StoredReport(UUID.fromString(r.getString("request_id")),r.getInt("generation"),UUID.fromString(r.getString("attempt_id")),r.getString("storage_key"),r.getLong("file_size"),r.getString("sha256")),request.toString()).stream().findFirst();}
	public void insert(StoredReport s,String localPath){var keys=new GeneratedKeyHolder();jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO files(usage_type,storage_url,storage_key,original_filename,file_size,content_type) VALUES('CUSTOM_REPORT_PDF',?,?,'custom-report.pdf',?,'application/pdf')",java.sql.Statement.RETURN_GENERATED_KEYS);statement.setString(1,localPath);statement.setString(2,s.key());statement.setLong(3,s.size());return statement;},keys);long file=Objects.requireNonNull(keys.getKey()).longValue();jdbc.update("INSERT INTO report_artifacts(id,request_id,attempt_id,generation,file_id,storage_key,file_size,sha256,content_type) VALUES(?,?,?,?,?,?,?,?,'application/pdf')",UUID.randomUUID().toString(),s.requestId().toString(),s.attemptId().toString(),s.generation(),file,s.key(),s.size(),s.sha256());}
	/** 호출자가 job 잠금을 보유한 상태에서 최종 참조 또는 유효 lease를 확인한다. */
	public boolean protectedFromCleanup(String key,UUID request,UUID attempt){
		// RR의 과거 read view를 쓰지 않도록 두 판단 모두 current locking read로 수행한다.
		if(!jdbc.queryForList("SELECT id FROM report_artifacts WHERE storage_key=? FOR UPDATE",String.class,key).isEmpty())return true;
		return !jdbc.queryForList("SELECT id FROM report_jobs WHERE request_id=? AND attempt_id=? AND state='RUNNING' AND lease_until>CURRENT_TIMESTAMP(6) FOR UPDATE",String.class,request.toString(),attempt.toString()).isEmpty();
	}
}
