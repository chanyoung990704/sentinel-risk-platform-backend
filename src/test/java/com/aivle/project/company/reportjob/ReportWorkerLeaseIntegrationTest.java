package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.reporting.internal.company.reportjob.model.*;
import com.aivle.project.reporting.api.CompletionDecision;
import com.aivle.project.reporting.internal.company.reportjob.service.*;
import com.aivle.project.reporting.internal.company.reportjob.port.*;
import com.aivle.project.reporting.internal.company.reportjob.repository.ReportJobRepository;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestService;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.messaging.inbox.ReportJobInbox;
import com.aivle.project.reporting.internal.messaging.outbox.ReportEvent;
import com.aivle.project.support.CustomReportIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("realinfra") @ActiveProfiles("local")
@Import(ReportWorkerLeaseIntegrationTest.FakeConfiguration.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio",
	"spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false","custom-report.worker.retry-jitter-ms=0"})
class ReportWorkerLeaseIntegrationTest extends CustomReportIntegrationSupport {
	@Autowired ReportRequestService requests;@Autowired ReportJobInbox inbox;@Autowired ReportJobRepository jobs;@Autowired ObjectMapper mapper;
	@Autowired ReportJobClaimService claims;@Autowired ReportJobRecovery recovery;@Autowired ReportJobWorker worker;@Autowired Recorder recorder;
	@MockitoSpyBean ReportClock clock;@MockitoSpyBean ReportGenerationPort generator;
	Instant now;
	@BeforeEach void configureWorker(){now=Instant.now();doReturn(now).when(clock).now();doReturn(new GeneratedReport(new byte[]{1,2},"fixture")).when(generator).generate(any(),any(),any());recorder.reset();worker.startAccepting();}
	@AfterEach void resetWorker(){recorder.release.countDown();worker.startAccepting();}
	UUID enqueue()throws Exception {var id=requests.accept(users.getFirst(),company,"lease-"+UUID.randomUUID(),new CustomReportCommand(null,"lease 시험")).requestId();inbox.accept(mapper.readValue(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString()),ReportEvent.class));return id;}
	JobLease claim() {return claims.claimReady(1,"fixture-worker",now).getFirst();}
	@Test void twoClaimersExecuteOneAttemptAndHeartbeatKeepsFenceVersion()throws Exception {
		// given
		var id=enqueue();var pool=Executors.newFixedThreadPool(2);
		try {
			// when
			var a=pool.submit(()->claims.claimReady(1,"worker-a",now));var b=pool.submit(()->claims.claimReady(1,"worker-b",now));var leases=new ArrayList<JobLease>();leases.addAll(a.get());leases.addAll(b.get());
			// then
			assertThat(leases).hasSize(1);var lease=leases.getFirst();assertThat(claims.heartbeat(lease,now.plusSeconds(5))).isTrue();assertThat(jobs.findByRequest(id).orElseThrow().version()).isEqualTo(lease.version());
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_attempts WHERE job_id=?",Integer.class,lease.jobId().toString())).isEqualTo(1);
		} finally {pool.shutdownNow();}
	}
	@Test void expiredLeaseIsRecoveredAndOldTokenCannotHeartbeatOrFailNewAttempt()throws Exception {
		// given
		var id=enqueue();var old=claim();
		// when
		assertThat(recovery.recover(now.plusSeconds(31))).isEqualTo(1);var next=claims.claimReady(1,"replacement",now.plusSeconds(40)).getFirst();
		// then
		assertThat(next.attemptNo()).isEqualTo(2);assertThat(next.version()).isGreaterThan(old.version());assertThat(next.leaseToken()).isNotEqualTo(old.leaseToken());
		assertThat(claims.heartbeat(old,now.plusSeconds(41))).isFalse();assertThat(claims.failOrRetry(old,"OLD",false,now.plusSeconds(41))).isFalse();
		assertThat(jdbc.queryForObject("SELECT state FROM report_attempts WHERE id=?",String.class,old.attemptId().toString())).isEqualTo("LEASE_EXPIRED");
	}
	@Test void transientFailuresStopAfterThreeAttemptsAndReturnSlotOnce()throws Exception {
		// given
		var id=enqueue();Instant time=now;
		for(int i=1;i<=3;i++) {
			// when / then
			var lease=claims.claimReady(1,"retry-worker",time).getFirst();assertThat(lease.attemptNo()).isEqualTo(i);assertThat(claims.failOrRetry(lease,"AI_HTTP_503",true,time.plusSeconds(1))).isTrue();time=time.plusSeconds(10);
		}
		assertThat(jobs.findByRequest(id).orElseThrow().state().name()).isEqualTo("FAILED");assertThat(claims.claimReady(1,"extra",time.plusSeconds(100))).isEmpty();
		assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isZero();
	}
	@Test void permanentFailureDoesNotRetryOrReleaseSlotTwice()throws Exception {
		// given
		var id=enqueue();var lease=claim();
		// when / then
		assertThat(claims.failOrRetry(lease,"PDF_INVALID",false,now.plusSeconds(1))).isTrue();assertThat(claims.failOrRetry(lease,"PDF_INVALID",false,now.plusSeconds(2))).isFalse();
		assertThat(jobs.findByRequest(id).orElseThrow().attemptNo()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isFalse();
	}
	@Test void durableReadyRunsWithoutWakeupAndExternalPortHasNoDatabaseTransaction()throws Exception {
		// given
		var id=enqueue();
		// when
		var runs=worker.runOnce();for(var f:runs)assertThat(f.get(15,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.COMPLETED);
		// then: R04에서는 파일/최종 커밋 대신 검증 fake port를 사용한다.
		assertThat(runs).hasSize(1);assertThat(recorder.calls.get()).isEqualTo(1);assertThat(recorder.inTransaction.get()).isFalse();
	}
	@Test void fourWorkersAreBoundedAndExtraJobsStayReady()throws Exception {
		// given
		for(int i=0;i<6;i++)enqueue();recorder.entered=new CountDownLatch(4);recorder.release=new CountDownLatch(1);
		try {
			// when
			var runs=worker.runOnce();assertThat(recorder.entered.await(10,TimeUnit.SECONDS)).isTrue();
			// then
			assertThat(runs).hasSize(4);assertThat(worker.runOnce()).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE state='READY'",Integer.class)).isEqualTo(2);
			recorder.release.countDown();for(var f:runs)f.get(15,TimeUnit.SECONDS);
		} finally {recorder.release.countDown();}
	}
	@Test void gracefulStopBlocksNewClaimsAndPreservesReadyJob()throws Exception {
		// given
		var id=enqueue();worker.stopClaiming();
		// when / then
		assertThat(worker.runOnce()).isEmpty();assertThat(jobs.findByRequest(id).orElseThrow().state().name()).isEqualTo("READY");
	}
	@Test void attemptDeadlineStillAppliesWhileResultPortIsBlocked()throws Exception {
		// given
		var id=enqueue();recorder.entered=new CountDownLatch(1);recorder.release=new CountDownLatch(1);recorder.waitSeconds=30;var runs=worker.runOnce();
		try {
			assertThat(recorder.entered.await(10,TimeUnit.SECONDS)).isTrue();
			// when / then
			assertThat(runs.getFirst().get(22,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.STALE);assertThat(jobs.findByRequest(id).orElseThrow().lastErrorCode()).isEqualTo("ATTEMPT_TIMEOUT");
		}finally{recorder.release.countDown();runs.getFirst().get(5,TimeUnit.SECONDS);}
	}

	@TestConfiguration(proxyBeanMethods=false) static class FakeConfiguration {@Bean @Primary Recorder recorder(){return new Recorder();}}
	static class Recorder implements ReportResultPort {
		final AtomicInteger calls=new AtomicInteger();final AtomicBoolean inTransaction=new AtomicBoolean();volatile CountDownLatch entered=new CountDownLatch(0),release=new CountDownLatch(0);volatile int waitSeconds=12;
		void reset(){calls.set(0);inTransaction.set(false);entered=new CountDownLatch(0);release=new CountDownLatch(0);waitSeconds=12;}
		public CompletionDecision storeAndComplete(JobLease lease,GeneratedReport report){inTransaction.compareAndSet(false,TransactionSynchronizationManager.isActualTransactionActive());calls.incrementAndGet();entered.countDown();try{if(!release.await(waitSeconds,TimeUnit.SECONDS))throw new IllegalStateException("fixture barrier timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return CompletionDecision.COMPLETED;}
	}
}
