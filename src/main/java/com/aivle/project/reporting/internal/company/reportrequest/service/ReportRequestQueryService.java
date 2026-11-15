package com.aivle.project.reporting.internal.company.reportrequest.service;
import com.aivle.project.reporting.api.ReportRequestView;

import com.aivle.project.reporting.api.ReportRequestException;
import com.aivle.project.reporting.internal.company.reportjob.repository.ReportArtifactRepository;
import com.aivle.project.reporting.internal.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.reporting.internal.company.reportjob.model.ReportGenerationFailure;
import com.aivle.project.foundation.api.Hashing;
import com.aivle.project.reporting.internal.company.reportrequest.cache.ReportStatusCache;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service public class ReportRequestQueryService implements com.aivle.project.reporting.api.ReportingQueries {
	private final JdbcTemplate jdbc;private final ReportArtifactRepository artifacts;private final LocalReportArtifactStore store;private final ReportStatusCache cache;
	private final com.aivle.project.identity.api.IdentityQueries identities;
	private final org.springframework.transaction.support.TransactionTemplate readTransaction;
	public ReportRequestQueryService(JdbcTemplate jdbc,ReportArtifactRepository artifacts,LocalReportArtifactStore store,ReportStatusCache cache,com.aivle.project.identity.api.IdentityQueries identities,org.springframework.transaction.PlatformTransactionManager manager){
		this.jdbc=jdbc;this.artifacts=artifacts;this.store=store;this.cache=cache;this.identities=identities;
		readTransaction=new org.springframework.transaction.support.TransactionTemplate(manager);readTransaction.setReadOnly(true);
	}
	
	private static final String SELECT="SELECT r.user_id,r.status,r.state_version,r.last_error_code,r.retry_at,COALESCE(j.generation,1) generation,COALESCE(j.attempt_no,0) attempt_no FROM report_requests r LEFT JOIN report_jobs j ON j.request_id=r.id WHERE r.id=?";
	public ReportRequestView getOwned(Long user,UUID id){
		if(user==null)throw new ReportRequestException(ReportRequestException.Code.UNAUTHORIZED);
		if(cache.isEnabled()){
			// 캐시가 있어도 삭제/비활성/소유권 변경은 DB 기준으로 즉시 거부한다.
			var versions=ownedRead(user,()->jdbc.queryForList("SELECT r.state_version FROM report_requests r WHERE r.id=? AND r.user_id=?",Long.class,id.toString(),user));
			if(versions.isEmpty())throw new ReportRequestException(ReportRequestException.Code.NOT_FOUND);
			var hit=cache.getOwnedStatus(user,id,versions.getFirst());if(hit.isPresent())return hit.get();
		}
		var fresh=getOwnedFromDatabase(user,id);cache.putBestEffort(user,id,fresh);return fresh;
	}
	public ReportRequestView getOwnedFromDatabase(Long user,UUID id){return ownedRead(user,()->jdbc.query(SELECT+" AND r.user_id=?",(r,n)->status(r,id),id.toString(),user).stream().findFirst().orElseThrow(()->new ReportRequestException(ReportRequestException.Code.NOT_FOUND)));}
	public record OwnedStatus(long owner,ReportRequestView status) { }
	public java.util.Optional<OwnedStatus> latestForProjection(UUID id){return readTransaction.execute(tx->jdbc.query(SELECT,(r,n)->new OwnedStatus(r.getLong("user_id"),status(r,id)),id.toString()).stream().findFirst().filter(row->identities.isActive(row.owner())));}
	private <T> T ownedRead(Long user,java.util.function.Supplier<T> query){
		if(user==null)throw new ReportRequestException(ReportRequestException.Code.UNAUTHORIZED);
		// 계정 정책과 요청 소유권은 같은 로컬 DB snapshot에서 검사한다. Redis/파일 I/O는 이 트랜잭션 밖이다.
		return readTransaction.execute(tx->{if(!identities.isActive(user))throw new ReportRequestException(ReportRequestException.Code.NOT_FOUND);return query.get();});
	}
	private ReportRequestView status(java.sql.ResultSet r,UUID id)throws java.sql.SQLException{return new ReportRequestView(id,r.getString("status"),r.getLong("state_version"),r.getInt("generation"),r.getInt("attempt_no"),r.getString("last_error_code"),r.getTimestamp("retry_at")==null?null:r.getTimestamp("retry_at").toInstant(),"SUCCEEDED".equals(r.getString("status"))?"/api/report-requests/"+id+"/file":null);}
	public byte[] openOwnedFile(Long user,UUID id){if(!getOwnedFromDatabase(user,id).status().equals("SUCCEEDED"))throw new ReportRequestException(ReportRequestException.Code.CONFLICT);var artifact=artifacts.find(id).orElseThrow(()->new ReportRequestException(ReportRequestException.Code.NOT_FOUND));try{byte[] bytes=store.read(artifact.key());if(bytes.length!=artifact.size()||!Hashing.sha256(bytes).equals(artifact.sha256()))throw new ReportGenerationFailure("FILE_CHECKSUM",false);return bytes;}catch(ReportGenerationFailure missing){throw new ReportRequestException(ReportRequestException.Code.NOT_FOUND);}}
}
