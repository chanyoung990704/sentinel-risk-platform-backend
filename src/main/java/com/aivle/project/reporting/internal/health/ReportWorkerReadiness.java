package com.aivle.project.reporting.internal.health;
import org.springframework.boot.actuate.health.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties;
import com.aivle.project.reporting.internal.company.reportjob.service.ReportJobWorker;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
/** 기존 DB 작업을 실행할 수 있는지 판단하는 작업자 준비 상태. */
@Component("reportWorker") @Profile("local")
public class ReportWorkerReadiness implements HealthIndicator {
	private final JdbcTemplate jdbc;private final ReportWorkerProperties properties;private final ReportJobWorker worker;
	public ReportWorkerReadiness(JdbcTemplate jdbc,ReportWorkerProperties properties,ReportJobWorker worker){this.jdbc=jdbc;this.properties=properties;this.worker=worker;}
	public Health health(){
		if(!properties.isEnabled()||!worker.isAccepting())return Health.status(Status.OUT_OF_SERVICE).withDetail("reason","WORKER_DISABLED_OR_STOPPING").build();
		try{return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1",Integer.class))?Health.up().build():Health.down().build();}
		catch(DataAccessException unavailable){return Health.down().withDetail("reason","DATABASE_UNAVAILABLE").build();}
	}
}
