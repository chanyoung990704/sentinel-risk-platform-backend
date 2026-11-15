package com.aivle.project.reporting.internal.company.reportjob.repository;

import com.aivle.project.reporting.internal.company.reportjob.entity.ReportRecoveryCommandEntity;
import com.aivle.project.reporting.api.ReportRequestException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRecoveryRepository {
	private final JdbcTemplate jdbc;
	private final com.aivle.project.identity.api.IdentityQueries identities;
	public ReportRecoveryRepository(JdbcTemplate jdbc,com.aivle.project.identity.api.IdentityQueries identities){this.jdbc=jdbc;this.identities=identities;}
	public void requireAdmin(Long actor){
		if(actor==null||!identities.isActiveAdministrator(actor))
			throw new ReportRequestException(ReportRequestException.Code.FORBIDDEN);
	}
	public Optional<ReportRecoveryCommandEntity> find(UUID command){
		return jdbc.query("SELECT * FROM report_recovery_commands WHERE id=?",(r,n)->new ReportRecoveryCommandEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("request_id")),r.getLong("actor_user_id"),r.getString("reason"),r.getInt("target_generation")),command.toString()).stream().findFirst();
	}
	public void insert(ReportRecoveryCommandEntity command){
		jdbc.update("INSERT INTO report_recovery_commands(id,request_id,actor_user_id,reason,target_generation) VALUES(?,?,?,?,?)",command.id().toString(),command.requestId().toString(),command.actorUserId(),command.reason(),command.targetGeneration());
	}
}
