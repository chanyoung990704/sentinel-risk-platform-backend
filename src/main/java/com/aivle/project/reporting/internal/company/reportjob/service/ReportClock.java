package com.aivle.project.reporting.internal.company.reportjob.service;
import java.time.Instant;
import org.springframework.stereotype.Component;
@Component public class ReportClock {
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;
	public ReportClock(org.springframework.jdbc.core.JdbcTemplate jdbc){this.jdbc=jdbc;}
	public Instant now(){return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",java.sql.Timestamp.class).toInstant();}
}
