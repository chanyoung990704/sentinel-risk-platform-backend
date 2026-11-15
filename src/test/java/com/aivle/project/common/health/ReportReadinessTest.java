package com.aivle.project.reporting.internal.health;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.reporting.internal.company.reportjob.config.ReportWorkerProperties;
import com.aivle.project.reporting.internal.company.reportjob.service.ReportJobWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
class ReportReadinessTest {
	@Test void apiReflectsDatabaseAndCapacityIndependentlyOfBroker() {
		// given
		var jdbc=mock(JdbcTemplate.class);var health=new ReportApiReadiness(jdbc,1000);when(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'",Integer.class)).thenReturn(999,1000);
		// when / then
		assertThat(health.health().getStatus()).isEqualTo(Status.UP);assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
	}
	@Test void databaseFailureDoesNotLeakDriverMessage() {
		// given
		var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'",Integer.class)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private JDBC details"));
		// when / then
		var health=new ReportApiReadiness(jdbc,1000).health();assertThat(health.getStatus()).isEqualTo(Status.DOWN);assertThat(health.getDetails().toString()).doesNotContain("private JDBC details");
	}
	@Test void workerRequiresEnabledAcceptingWorkerAndDatabase() {
		// given
		var jdbc=mock(JdbcTemplate.class);var worker=mock(ReportJobWorker.class);var properties=new ReportWorkerProperties();var health=new ReportWorkerReadiness(jdbc,properties,worker);
		// when / then
		assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);properties.setEnabled(true);assertThat(health.health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);when(worker.isAccepting()).thenReturn(true);when(jdbc.queryForObject("SELECT 1",Integer.class)).thenReturn(1);assertThat(health.health().getStatus()).isEqualTo(Status.UP);when(jdbc.queryForObject("SELECT 1",Integer.class)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private"));assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
	}
}
