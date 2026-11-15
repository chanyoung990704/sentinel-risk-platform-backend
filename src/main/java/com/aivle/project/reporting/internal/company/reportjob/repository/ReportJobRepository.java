package com.aivle.project.reporting.internal.company.reportjob.repository;
import java.util.UUID;
import java.util.*;
import java.sql.*;
import com.aivle.project.reporting.internal.company.reportjob.entity.ReportJobEntity;
import com.aivle.project.reporting.api.ReportJobStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository public class ReportJobRepository {
	private final JdbcTemplate jdbc;
	public ReportJobRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public boolean createIfAbsent(UUID requestId){return jdbc.update("INSERT IGNORE INTO report_jobs(id,request_id,state) VALUES(?,?,'READY')",UUID.randomUUID().toString(),requestId.toString())==1;}
	public Optional<ReportJobEntity> findByRequest(UUID request){return jdbc.query("SELECT * FROM report_jobs WHERE request_id=?",this::map,request.toString()).stream().findFirst();}
	public ReportJobEntity lock(UUID id){return jdbc.queryForObject("SELECT * FROM report_jobs WHERE id=? FOR UPDATE",this::map,id.toString());}
	public Optional<ReportJobEntity> lockByRequest(UUID request){return jdbc.query("SELECT * FROM report_jobs WHERE request_id=? FOR UPDATE",this::map,request.toString()).stream().findFirst();}
	public java.time.Instant attemptStartedAt(UUID attempt){return jdbc.queryForObject("SELECT started_at FROM report_attempts WHERE id=?",Timestamp.class,attempt.toString()).toInstant();}
	public void reopen(UUID id){jdbc.update("UPDATE report_jobs SET state='READY',generation=generation+1,version=version+1,attempt_no=0,attempt_id=NULL,lease_token=NULL,owner=NULL,lease_until=NULL,retry_at=NULL,last_error_code=NULL,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",id.toString());}
	public java.util.List<com.aivle.project.reporting.internal.company.reportjob.entity.ReportAttemptEntity> history(UUID request){return jdbc.query("SELECT a.* FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id=? ORDER BY a.generation,a.attempt_no",(r,n)->new com.aivle.project.reporting.internal.company.reportjob.entity.ReportAttemptEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("job_id")),r.getInt("generation"),r.getInt("attempt_no"),r.getString("state"),r.getString("error_code")),request.toString());}
	public List<ReportJobEntity> ready(int limit,java.time.Instant now){return jdbc.query("SELECT * FROM report_jobs WHERE state='READY' OR (state='RETRY_WAIT' AND retry_at<=?) ORDER BY created_at,id LIMIT ? FOR UPDATE SKIP LOCKED",this::map,Timestamp.from(now),limit);}
	public List<ReportJobEntity> expired(java.time.Instant now){return jdbc.query("SELECT * FROM report_jobs WHERE state='RUNNING' AND lease_until<=? ORDER BY lease_until LIMIT 100 FOR UPDATE SKIP LOCKED",this::map,Timestamp.from(now));}
	public void claim(com.aivle.project.reporting.internal.company.reportjob.model.JobLease l,String owner,java.time.Instant now){
		jdbc.update("UPDATE report_jobs SET state='RUNNING',version=?,attempt_no=?,attempt_id=?,lease_token=?,owner=?,lease_until=?,retry_at=NULL,last_error_code=NULL,updated_at=? WHERE id=?",l.version(),l.attemptNo(),l.attemptId().toString(),l.leaseToken().toString(),owner,Timestamp.from(l.leaseUntil()),Timestamp.from(now),l.jobId().toString());
		jdbc.update("INSERT INTO report_attempts(id,job_id,generation,attempt_no,claim_version,lease_token,state,started_at) VALUES(?,?,?,?,?,?,'RUNNING',?)",l.attemptId().toString(),l.jobId().toString(),l.generation(),l.attemptNo(),l.version(),l.leaseToken().toString(),Timestamp.from(now));
	}
	public void heartbeat(UUID id,java.time.Instant until){jdbc.update("UPDATE report_jobs SET lease_until=? WHERE id=?",Timestamp.from(until),id.toString());}
	public void finishAttempt(UUID attempt,String state,String error,java.time.Instant now){jdbc.update("UPDATE report_attempts SET state=?,error_code=?,completed_at=? WHERE id=?",state,error,Timestamp.from(now),attempt.toString());}
	public void finish(UUID id,String state,String error,java.time.Instant retry,java.time.Instant now){jdbc.update("UPDATE report_jobs SET state=?,last_error_code=?,retry_at=?,lease_until=NULL,updated_at=? WHERE id=?",state,error,retry==null?null:Timestamp.from(retry),Timestamp.from(now),id.toString());}
	private ReportJobEntity map(ResultSet r,int n)throws SQLException{return new ReportJobEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("request_id")),ReportJobStatus.valueOf(r.getString("state")),r.getLong("version"),r.getInt("generation"),r.getInt("attempt_no"),uuid(r.getString("attempt_id")),uuid(r.getString("lease_token")),instant(r.getTimestamp("lease_until")),instant(r.getTimestamp("retry_at")),r.getString("last_error_code"),r.getString("owner"));}
	private UUID uuid(String v){return v==null?null:UUID.fromString(v);}private java.time.Instant instant(Timestamp v){return v==null?null:v.toInstant();}
}
