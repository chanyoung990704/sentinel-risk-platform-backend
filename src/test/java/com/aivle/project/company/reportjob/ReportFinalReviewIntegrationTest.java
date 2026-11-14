package com.aivle.project.company.reportjob;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.service.*;
import com.aivle.project.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.company.reportjob.model.*;
import com.aivle.project.messaging.outbox.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportFinalReviewIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired ReportCompletionService completion;
	@Autowired LocalReportArtifactStore store;
	@Autowired ReportArtifactCleanupService cleanup;
	@MockitoSpyBean OutboxWriter outbox;
	@MockitoSpyBean ReportClock observedClock;
	CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
	@AfterEach void releaseBarrier(){release.countDown();}
	void blockFinalOutbox() {
		doAnswer(invocation->{
			ReportEvent event=invocation.getArgument(0);invocation.callRealMethod();
			if(event.payload().status().name().equals("SUCCEEDED")){
				entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("완료 barrier 제한 초과");
			}return null;
		}).when(outbox).append(any());
	}
	void awaitDatabaseTime(Instant after)throws Exception {
		long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
		while(clock.now().isBefore(after)&&System.nanoTime()<deadline)Thread.sleep(20);
		assertThat(clock.now()).isAfterOrEqualTo(after);
	}
	@Test void cleanupSerializesWithCompletionAndExpiredCompletionRollsBack()throws Exception {
		// given: 파일 유예는 mtime으로 모델링하고 실제 DB lease는 짧게 만료시킨다.
		var id=enqueue();var l=claim();var stored=store.storeAttempt(l,generate(l));var file=store.path(stored.key());Files.setLastModifiedTime(file,FileTime.from(Instant.now().minusSeconds(600)));
		Instant expiry=clock.now().plusSeconds(1);jdbc.update("UPDATE report_jobs SET lease_until=? WHERE id=?",java.sql.Timestamp.from(expiry),l.jobId().toString());blockFinalOutbox();var pool=Executors.newFixedThreadPool(2);
		Future<CompletionDecision> done=null;Future<Integer> cleaned=null;
		try {
			done=pool.submit(()->completion.complete(l,stored));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();awaitDatabaseTime(expiry.plusMillis(50));
			// when: 미커밋 artifact와 만료 lease를 보는 정리는 완료의 job 잠금 뒤에 서야 한다.
			cleaned=pool.submit(()->cleanup.cleanupOrphans(clock.now()));var pending=cleaned;
			assertThatThrownBy(()->pending.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			release.countDown();
			// then: 만료 완료는 전체 rollback, 그 뒤 고아만 삭제한다.
			assertThat(done.get(5,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.STALE);assertThat(cleaned.get(5,TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isZero();assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString())).isEqualTo("PROCESSING");assertThat(file).doesNotExist();
		}finally{release.countDown();if(done!=null)done.get(5,TimeUnit.SECONDS);if(cleaned!=null)cleaned.get(5,TimeUnit.SECONDS);pool.shutdownNow();}
	}
	@Test void deadlinePassingAfterFinalWriteRollsBackArtifactSlotAndSuccessEvent()throws Exception {
		// given
		var id=enqueue();var l=claim();var stored=store.storeAttempt(l,generate(l));Instant before=clock.now();blockFinalOutbox();var pool=Executors.newSingleThreadExecutor();Future<CompletionDecision> done=null;
		try {
			done=pool.submit(()->completion.complete(l,stored));assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
			// when: lease 30초 안이지만 시도 20초는 지난 마지막 쓰기 시점이다.
			doReturn(before.plusSeconds(21)).when(observedClock).now();release.countDown();
			// then
			assertThat(done.get(5,TimeUnit.SECONDS)).isEqualTo(CompletionDecision.STALE);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isZero();assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isTrue();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=? AND payload LIKE '%SUCCEEDED%'",Integer.class,id.toString())).isZero();
		}finally{release.countDown();if(done!=null)done.get(5,TimeUnit.SECONDS);pool.shutdownNow();}
	}
	@Test void lockWaitCannotReviveExpiredLeaseOrApplyOldFailure()throws Exception {
		// given
		var first=enqueue();var second=enqueue();var leases=claims.claimReady(2,"blocked",clock.now());Instant sample=clock.now(),expiry=sample.plusSeconds(1);for(var l:leases)jdbc.update("UPDATE report_jobs SET lease_until=? WHERE id=?",java.sql.Timestamp.from(expiry),l.jobId().toString());var pool=Executors.newFixedThreadPool(2);var running=new ArrayList<Future<Boolean>>();
		try(var blocker=runtimeDataSource.getConnection()){
			blocker.setAutoCommit(false);for(var l:leases)try(var query=blocker.prepareStatement("SELECT id FROM report_jobs WHERE id=? FOR UPDATE")){query.setString(1,l.jobId().toString());query.executeQuery().close();}
			try {
				// when
				var heartbeat=pool.submit(()->claims.heartbeat(leases.get(0),sample));running.add(heartbeat);var failure=pool.submit(()->claims.failOrRetry(leases.get(1),"OLD_FAILURE",false,sample));running.add(failure);awaitDatabaseTime(expiry.plusMillis(50));assertThat(heartbeat.isDone()).isFalse();assertThat(failure.isDone()).isFalse();blocker.commit();
				// then
				assertThat(heartbeat.get(5,TimeUnit.SECONDS)).isFalse();assertThat(failure.get(5,TimeUnit.SECONDS)).isFalse();assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isEqualTo(2);
			}finally{blocker.rollback();}
		}finally{try{for(var task:running)task.get(5,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
	}
	@Test void staleClaimTimeDoesNotCreateAlreadyExpiredLease()throws Exception {
		// given
		enqueue();Instant before=clock.now();
		// when
		var lease=claims.claimReady(1,"old-sample",before.minusSeconds(3600)).getFirst();
		// then
		assertThat(lease.leaseUntil()).isAfter(before);
	}
}
