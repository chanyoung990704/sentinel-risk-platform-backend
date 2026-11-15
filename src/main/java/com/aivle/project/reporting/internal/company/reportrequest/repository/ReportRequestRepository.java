package com.aivle.project.reporting.internal.company.reportrequest.repository;
import com.aivle.project.reporting.internal.company.reportrequest.entity.ReportRequestEntity;

import com.aivle.project.reporting.api.ReportRequestStatus;
import com.aivle.project.reporting.api.ReportRequestException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository public class ReportRequestRepository {
	private final JdbcTemplate jdbc;
	private final com.aivle.project.identity.api.IdentityQueries identities;
	private final com.aivle.project.finance.api.FinancialSources sources;
	public ReportRequestRepository(JdbcTemplate jdbc,com.aivle.project.identity.api.IdentityQueries identities,com.aivle.project.finance.api.FinancialSources sources){this.jdbc=jdbc;this.identities=identities;this.sources=sources;}
	public Optional<ReportRequestEntity> findByKey(long user,String hash){return jdbc.query("SELECT * FROM report_requests WHERE user_id=? AND idempotency_key_hash=?",this::map,user,hash).stream().findFirst();}
	public Optional<ReportRequestEntity> find(UUID id){return jdbc.query("SELECT * FROM report_requests WHERE id=?",this::map,id.toString()).stream().findFirst();}
	public ReportRequestEntity lock(UUID id){return jdbc.query("SELECT * FROM report_requests WHERE id=? FOR UPDATE",this::map,id.toString()).stream().findFirst().orElseThrow(()->new ReportRequestException(ReportRequestException.Code.NOT_FOUND));}
	private ReportRequestEntity map(ResultSet r,int n)throws SQLException{return new ReportRequestEntity(UUID.fromString(r.getString("id")),r.getLong("user_id"),r.getLong("company_id"),r.getInt("target_quarter_key"),UUID.fromString(r.getString("snapshot_id")),r.getString("idempotency_key_hash"),r.getString("input_hash"),r.getString("additional_instructions"),ReportRequestStatus.valueOf(r.getString("status")),r.getLong("state_version"),r.getBoolean("admission_slot_held"));}
	public void insert(ReportRequestEntity r){jdbc.update("INSERT INTO report_requests(id,user_id,company_id,target_quarter_key,snapshot_id,idempotency_key_hash,input_hash,additional_instructions,status,state_version,admission_slot_held) VALUES(?,?,?,?,?,?,?,?,?,?,?)",r.id().toString(),r.userId(),r.companyId(),r.targetQuarterKey(),r.snapshotId().toString(),r.keyHash(),r.inputHash(),r.instructions(),r.status().name(),r.stateVersion(),r.admissionSlotHeld());}
	public void requireUser(long user){if(!identities.isActive(user))throw new ReportRequestException(ReportRequestException.Code.UNAUTHORIZED);}
	public void requireCompany(long company){if(!sources.companyExists(company))throw new ReportRequestException(ReportRequestException.Code.INVALID);}
	public record Source(UUID id,int baseQuarter) { }
	public Source active(){var source=sources.active().orElseThrow(()->new ReportRequestException(ReportRequestException.Code.INVALID));return new Source(source.id(),source.baseQuarter());}
	public void requireValues(UUID snapshot,long company,int quarter){if(!sources.hasValues(snapshot,company,quarter))throw new ReportRequestException(ReportRequestException.Code.INVALID);}
	public int lockCounter(String scope){return jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=? FOR UPDATE",Integer.class,scope);}
	public void ensureUserCounter(long user){jdbc.update("INSERT INTO report_admission(scope_key,used_count) VALUES(?,0) ON DUPLICATE KEY UPDATE scope_key=VALUES(scope_key)","USER:"+user);}
	public void changeCounter(String scope,int delta){if(jdbc.update("UPDATE report_admission SET used_count=used_count+? WHERE scope_key=? AND used_count+?>=0",delta,scope,delta)!=1)throw new IllegalStateException("Admission invariant");}
	public boolean releaseSlot(UUID id){return jdbc.update("UPDATE report_requests SET admission_slot_held=0 WHERE id=? AND admission_slot_held=1",id.toString())==1;}
	public void holdSlot(UUID id){if(jdbc.update("UPDATE report_requests SET admission_slot_held=1 WHERE id=? AND admission_slot_held=0",id.toString())!=1)throw new IllegalStateException("Admission slot invariant");}
	public ReportRequestEntity transition(UUID id,ReportRequestStatus status,String error,Instant retryAt){jdbc.update("UPDATE report_requests SET status=?,state_version=state_version+1,last_error_code=?,retry_at=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",status.name(),error,retryAt==null?null:java.sql.Timestamp.from(retryAt),id.toString());return find(id).orElseThrow();}
}
