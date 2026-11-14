package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.company.reportjob.config.ReportWorkerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
class ReportWorkerPropertiesTest {
	@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(ReportWorkerProperties.class) static class Binding { }
	private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Binding.class);
	@Test void contractDefaultsAndExplicitBoundedOverridesBind(){
		// given / when / then
		runner.run(c->{assertThat(c).hasNotFailed();var p=c.getBean(ReportWorkerProperties.class);assertThat(p.getWorkers()).isEqualTo(4);assertThat(p.getLeaseSeconds()).isEqualTo(30);assertThat(p.getHeartbeatSeconds()).isEqualTo(5);assertThat(p.getAttemptTimeoutMillis()).isEqualTo(20000);assertThat(p.getMaxAttempts()).isEqualTo(3);});
		runner.withPropertyValues("custom-report.worker.workers=2","custom-report.worker.max-attempts=2").run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(ReportWorkerProperties.class).getWorkers()).isEqualTo(2);});
	}
	@Test void zeroWorkersIsRejected(){
		// given / when / then
		runner.withPropertyValues("custom-report.worker.workers=0").run(c->assertThat(c).hasFailed());
	}
	@Test void unsafeLeaseVersusDeadlineIsRejected(){
		// given / when / then
		runner.withPropertyValues("custom-report.worker.lease-seconds=20").run(c->assertThat(c).hasFailed());
	}
	@Test void zeroRetryBudgetIsRejected(){
		// given / when / then
		runner.withPropertyValues("custom-report.worker.max-attempts=0").run(c->assertThat(c).hasFailed());
	}
	@Test void excessiveHeartbeatIntervalIsRejected(){
		// given / when / then
		runner.withPropertyValues("custom-report.worker.heartbeat-seconds=30").run(c->assertThat(c).hasFailed());
	}
}
