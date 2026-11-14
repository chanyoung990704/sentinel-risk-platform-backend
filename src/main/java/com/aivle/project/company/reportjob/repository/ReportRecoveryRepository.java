package com.aivle.project.company.reportjob.repository;

import com.aivle.project.company.reportjob.entity.ReportRecoveryCommandEntity;
import com.aivle.project.company.reportrequest.model.ReportRequestException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRecoveryRepository {
	private final JdbcTemplate jdbc;
	public ReportRecoveryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
	public void requireAdmin(Long actor){
		if(actor==null||jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL AND r.deleted_at IS NULL AND r.name='ROLE_ADMIN'",Integer.class,actor)==0)
			throw new ReportRequestException(ReportRequestException.Code.FORBIDDEN);
	}
	public Optional<ReportRecoveryCommandEntity> find(UUID command){
		return jdbc.query("SELECT * FROM report_recovery_commands WHERE id=?",(r,n)->new ReportRecoveryCommandEntity(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("request_id")),r.getLong("actor_user_id"),r.getString("reason"),r.getInt("target_generation")),command.toString()).stream().findFirst();
	}
	public void insert(ReportRecoveryCommandEntity command){
		jdbc.update("INSERT INTO report_recovery_commands(id,request_id,actor_user_id,reason,target_generation) VALUES(?,?,?,?,?)",command.id().toString(),command.requestId().toString(),command.actorUserId(),command.reason(),command.targetGeneration());
	}
}
