package com.aivle.project.reporting.internal.health;
import org.springframework.boot.actuate.health.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
/** DB와 접수 수용량만으로 판단하는 API 준비 상태. */
@Component("reportApi") @Profile("local")
public class ReportApiReadiness implements HealthIndicator {
	private final JdbcTemplate jdbc;private final int limit;
	public ReportApiReadiness(JdbcTemplate jdbc,@Value("${custom-report.admission.global-limit:1000}") int limit){this.jdbc=jdbc;this.limit=limit;}
	public Health health(){
		try{Integer used=jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'",Integer.class);if(used==null||used<0)return Health.down().withDetail("reason","ADMISSION_UNAVAILABLE").build();return (used<limit?Health.up():Health.status(Status.OUT_OF_SERVICE)).withDetail("used",used).withDetail("limit",limit).build();}
		catch(DataAccessException unavailable){return Health.down().withDetail("reason","DATABASE_UNAVAILABLE").build();}
	}
}
