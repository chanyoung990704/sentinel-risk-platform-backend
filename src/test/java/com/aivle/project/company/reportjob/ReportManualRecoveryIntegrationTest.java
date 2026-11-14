package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.service.*;
import com.aivle.project.company.reportjob.model.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false","custom-report.admission.global-limit=5","custom-report.admission.user-limit=3","custom-report.worker.retry-jitter-ms=0"})
class ReportManualRecoveryIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired ReportRecoveryService recovery;@Autowired ReportResultService results;
	@Autowired ReportJobWorker worker;@org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReportClock mutableClock;
	@BeforeEach void makeAdmin(){jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_ADMIN'",users.getLast());}
	@AfterEach void removeAdmin(){for(long user:users)jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);}
	JobLease fail(UUID id){var lease=claim();claims.failOrRetry(lease,"PDF_INVALID",false,clock.now());return lease;}
	@Test void repeatedAndConcurrentCommandCreatesOneNewGenerationAndCompletesRealPdf()throws Exception {
		// given
		var id=enqueue();var old=fail(id);var original=jdbc.queryForMap("SELECT snapshot_id,input_hash,additional_instructions FROM report_requests WHERE id=?",id.toString());var command=UUID.randomUUID();var pool=Executors.newFixedThreadPool(10);
		try {
			// when
			var futures=new ArrayList<Future<ReportRecoveryService.RecoveryResult>>();for(int i=0;i<10;i++)futures.add(pool.submit(()->recovery.recover(users.getLast(),id,command,"모의 서버 오류 원인 제거")));for(var f:futures)assertThat(f.get().generation()).isEqualTo(2);for(int i=0;i<10;i++)assertThat(recovery.recover(users.getLast(),id,command,"모의 서버 오류 원인 제거").generation()).isEqualTo(2);
			// then
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_recovery_commands WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isEqualTo(1);
			var next=claim();assertThat(next.generation()).isEqualTo(2);assertThat(next.attemptNo()).isEqualTo(1);var pdf=generate(next);assertThat(results.storeAndComplete(old,pdf)).isEqualTo(CompletionDecision.STALE);assertThat(results.storeAndComplete(next,pdf)).isEqualTo(CompletionDecision.COMPLETED);
			assertThat(jdbc.queryForMap("SELECT snapshot_id,input_hash,additional_instructions FROM report_requests WHERE id=?",id.toString())).isEqualTo(original);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_attempts WHERE job_id=?",Integer.class,old.jobId().toString())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT state FROM report_attempts WHERE id=?",String.class,old.attemptId().toString())).isEqualTo("FAILED");
			assertThat(recovery.recover(users.getLast(),id,command,"모의 서버 오류 원인 제거").generation()).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isFalse();
		}finally{pool.shutdownNow();}
	}
	@Test void quotaDenialRollsBackGenerationAndAudit()throws Exception {
		// given
		var id=enqueue();fail(id);for(int i=0;i<3;i++)enqueue();
		// when / then
		assertThatThrownBy(()->recovery.recover(users.getLast(),id,UUID.randomUUID(),"재개")).hasMessageContaining("USER_CAPACITY");assertThat(jdbc.queryForObject("SELECT generation FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_recovery_commands WHERE request_id=?",Integer.class,id.toString())).isZero();
	}
	@Test void ordinaryUserCannotRecoverAndCommandCannotChangeRequestOrReason()throws Exception {
		// given
		var id=enqueue();fail(id);var command=UUID.randomUUID();
		// when / then
		assertThatThrownBy(()->recovery.recover(users.getFirst(),id,command,"재개")).hasMessageContaining("FORBIDDEN");recovery.recover(users.getLast(),id,command,"재개");assertThatThrownBy(()->recovery.recover(users.getLast(),id,command,"다른 사유")).hasMessageContaining("CONFLICT");fail(id);var second=enqueue();fail(second);assertThatThrownBy(()->recovery.recover(users.getLast(),second,command,"재개")).hasMessageContaining("CONFLICT");
	}
	@Test void successCannotBeRecoveredAndOldInboxCannotRestartFailedJob()throws Exception {
		// given
		var id=enqueue();var l=fail(id);String initial=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString());
		// when / then
		assertThat(inbox.accept(mapper.readValue(initial,com.aivle.project.messaging.outbox.ReportEvent.class)).name()).isEqualTo("DUPLICATE");assertThat(jdbc.queryForObject("SELECT state FROM report_jobs WHERE request_id=?",String.class,id.toString())).isEqualTo("FAILED");recovery.recover(users.getLast(),id,UUID.randomUUID(),"재개");var next=claim();results.storeAndComplete(next,generate(next));assertThatThrownBy(()->recovery.recover(users.getLast(),id,UUID.randomUUID(),"또 재개")).hasMessageContaining("CONFLICT");
	}
	@Test void exhaustedThreeAttemptsRemainInHistoryAfterRecovery()throws Exception {
		// given
		var id=enqueue();var now=clock.now();for(int i=0;i<3;i++){var l=claims.claimReady(1,"exhaust",now.plusSeconds(i*10)).getFirst();claims.failOrRetry(l,"AI_HTTP_503",true,now.plusSeconds(i*10+1));}
		// when
		recovery.recover(users.getLast(),id,UUID.randomUUID(),"원인 제거");var next=claim();results.storeAndComplete(next,generate(next));
		// then
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_attempts a JOIN report_jobs j ON j.id=a.job_id WHERE j.request_id=? AND a.generation=1",Integer.class,id.toString())).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT state FROM report_jobs WHERE request_id=?",String.class,id.toString())).isEqualTo("SUCCEEDED");
	}
	@Test void actualWorkerRetriesOneHttp503AndCompletesWithoutLosingFixedInput()throws Exception {
		// given
		var id=enqueue();var now=clock.now();org.mockito.Mockito.doReturn(now).when(mutableClock).now();String original=jdbc.queryForObject("SELECT input_hash FROM report_requests WHERE id=?",String.class,id.toString());var http=java.net.http.HttpClient.newHttpClient();var control=java.net.URI.create("http://127.0.0.1:18081/control/custom-reports");
		try {
			http.send(java.net.http.HttpRequest.newBuilder(control).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"fail_first\":1,\"response_mode\":\"valid\"}")).build(),java.net.http.HttpResponse.BodyHandlers.discarding());
			// when / then
			assertThat(worker.runOnce().getFirst().get(15,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.STALE);assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString())).isEqualTo("RETRY_WAIT");
			org.mockito.Mockito.doReturn(now.plusSeconds(3)).when(mutableClock).now();assertThat(worker.runOnce().getFirst().get(15,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.COMPLETED);
			assertThat(jdbc.queryForObject("SELECT attempt_no FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT input_hash FROM report_requests WHERE id=?",String.class,id.toString())).isEqualTo(original);
		}finally{http.send(java.net.http.HttpRequest.newBuilder(control).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"fail_first\":0,\"response_mode\":\"valid\"}")).build(),java.net.http.HttpResponse.BodyHandlers.discarding());}
	}
	@Test void separateLocalCliProcessReplaysSameManagementCommand()throws Exception {
		// given
		var id=enqueue();fail(id);var command=UUID.randomUUID();String classpath=java.nio.file.Files.readString(java.nio.file.Path.of("build/portfolio-report-classpath.txt")).strip();
		for(int i=0;i<2;i++) {
			java.nio.file.Path log=java.nio.file.Files.createTempFile("sentinel-recovery-cli-", ".log");
			try {
				// when
				var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx128m","-cp",classpath,"com.aivle.project.company.reportjob.cli.ReportRecoveryCommand",id.toString(),command.toString(),users.getLast().toString(),"CLI 원인 제거 후 재개");
				var env=builder.environment();env.clear();for(String name:List.of("PATH","HOME","USER","LANG","JAVA_HOME"))if(System.getenv(name)!=null)env.put(name,System.getenv(name));env.put("SPRING_PROFILES_ACTIVE","local");env.put("PORTFOLIO_SECRET_DIR",com.aivle.project.support.PortfolioSecretPaths.directory().toString());var process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
				// then
				assertThat(process.waitFor(20,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).withFailMessage(java.nio.file.Files.readString(log)).isZero();assertThat(java.nio.file.Files.readString(log)).contains("\"generation\":2").doesNotContain("CLI 원인 제거 후 재개");
			}finally{java.nio.file.Files.deleteIfExists(log);}
		}
		assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isEqualTo(1);
	}
}
