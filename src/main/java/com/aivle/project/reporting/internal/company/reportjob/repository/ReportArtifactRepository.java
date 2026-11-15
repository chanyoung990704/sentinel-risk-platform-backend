package com.aivle.project.reporting.internal.company.reportjob.repository;
import com.aivle.project.reporting.internal.company.reportjob.model.StoredReport;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aivle.project.storage.api.FileCatalog;
import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.storage.api.FileUsageType;
import org.springframework.stereotype.Repository;
@Repository public class ReportArtifactRepository {
	private final JdbcTemplate jdbc;
	private final FileCatalog files;
	public ReportArtifactRepository(JdbcTemplate jdbc,FileCatalog files){this.jdbc=jdbc;this.files=files;}
	public Optional<StoredReport> find(UUID request){return jdbc.query("SELECT * FROM report_artifacts WHERE request_id=?",(r,n)->new StoredReport(UUID.fromString(r.getString("request_id")),r.getInt("generation"),UUID.fromString(r.getString("attempt_id")),r.getString("storage_key"),r.getLong("file_size"),r.getString("sha256")),request.toString()).stream().findFirst();}
	public void insert(StoredReport s,String localPath){
		// 기존 완료 트랜잭션에 참여해 파일 메타·artifact·슬롯 해제를 함께 확정한다.
		long file=files.save(FileMetadata.create(FileUsageType.CUSTOM_REPORT_PDF,localPath,s.key(),"custom-report.pdf",s.size(),"application/pdf")).getId();
		jdbc.update("INSERT INTO report_artifacts(id,request_id,attempt_id,generation,file_id,storage_key,file_size,sha256,content_type) VALUES(?,?,?,?,?,?,?,?,'application/pdf')",UUID.randomUUID().toString(),s.requestId().toString(),s.attemptId().toString(),s.generation(),file,s.key(),s.size(),s.sha256());
	}
	/** 호출자가 job 잠금을 보유한 상태에서 최종 참조 또는 유효 lease를 확인한다. */
	public boolean protectedFromCleanup(String key,UUID request,UUID attempt){
		// RR의 과거 read view를 쓰지 않도록 두 판단 모두 current locking read로 수행한다.
		if(!jdbc.queryForList("SELECT id FROM report_artifacts WHERE storage_key=? FOR UPDATE",String.class,key).isEmpty())return true;
		return !jdbc.queryForList("SELECT id FROM report_jobs WHERE request_id=? AND attempt_id=? AND state='RUNNING' AND lease_until>CURRENT_TIMESTAMP(6) FOR UPDATE",String.class,request.toString(),attempt.toString()).isEmpty();
	}
}
