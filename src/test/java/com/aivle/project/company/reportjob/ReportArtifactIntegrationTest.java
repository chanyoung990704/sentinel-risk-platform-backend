package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.company.reportjob.service.*;
import com.aivle.project.company.reportjob.adapter.LocalReportArtifactStore;
import com.aivle.project.company.reportjob.model.*;
import com.aivle.project.messaging.outbox.OutboxWriter;
import com.aivle.project.support.ReportFlowIntegrationSupport;
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
class ReportArtifactIntegrationTest extends ReportFlowIntegrationSupport {
	@Autowired ReportResultService results;@Autowired ReportCompletionService completion;@Autowired LocalReportArtifactStore store;@Autowired ReportArtifactCleanupService cleanup;
	@MockitoSpyBean OutboxWriter outbox;
	@Test void fileBeforeDatabaseFailureCanBeRetriedAndCompletionResponseCanBeLost()throws Exception {
		// given
		var id=enqueue();var l=claim();var pdf=generate(l);doThrow(new org.springframework.dao.DataIntegrityViolationException("fixture")).when(outbox).append(any());
		// when
		assertThatThrownBy(()->results.storeAndComplete(l,pdf)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);reset(outbox);
		// then
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isZero();assertThat(jdbc.queryForObject("SELECT admission_slot_held FROM report_requests WHERE id=?",Boolean.class,id.toString())).isTrue();
		assertThat(results.storeAndComplete(l,pdf)).isEqualTo(CompletionDecision.COMPLETED);assertThat(results.storeAndComplete(l,pdf)).isEqualTo(CompletionDecision.DUPLICATE);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isZero();
	}
	@Test void concurrentCompletionCreatesOneArtifact()throws Exception {
		// given
		var id=enqueue();var l=claim();var stored=store.storeAttempt(l,generate(l));var pool=Executors.newFixedThreadPool(2);
		try{
			// when
			var a=pool.submit(()->completion.complete(l,stored));var b=pool.submit(()->completion.complete(l,stored));
			// then
			assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(CompletionDecision.COMPLETED,CompletionDecision.DUPLICATE);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
		}finally{pool.shutdownNow();}
	}
	@Test void expiredWorkerCannotCompleteNewAttemptOrOverwriteItsFile()throws Exception {
		// given
		var id=enqueue();var old=claim();var pdf=generate(old);var stored=store.storeAttempt(old,pdf);claims.recover(clock.now().plusSeconds(31));var next=claims.claimReady(1,"replacement",clock.now().plusSeconds(40)).getFirst();
		// when / then
		assertThat(completion.complete(old,stored)).isEqualTo(CompletionDecision.STALE);var newer=store.storeAttempt(next,pdf);assertThat(newer.key()).isNotEqualTo(stored.key());assertThat(completion.complete(next,newer)).isEqualTo(CompletionDecision.COMPLETED);
	}
	@Test void cleanupPreservesReferenceAndLiveLeaseButDeletesExpiredOrphan()throws Exception {
		// given
		var id=enqueue();var l=claim();var stored=store.storeAttempt(l,generate(l));var path=store.path(stored.key());Files.setLastModifiedTime(path,FileTime.from(Instant.now().minusSeconds(600)));
		// when / then
		assertThat(cleanup.cleanupOrphans(Instant.now())).isZero();assertThat(path).exists();claims.failOrRetry(l,"PDF_INVALID",false,clock.now());assertThat(cleanup.cleanupOrphans(Instant.now())).isEqualTo(1);assertThat(path).doesNotExist();
		var nextId=enqueue();var next=claim();var report=generate(next);results.storeAndComplete(next,report);String key=jdbc.queryForObject("SELECT storage_key FROM report_artifacts WHERE request_id=?",String.class,nextId.toString());Files.setLastModifiedTime(store.path(key),FileTime.from(Instant.now().minusSeconds(600)));assertThat(cleanup.cleanupOrphans(Instant.now())).isZero();assertThat(store.path(key)).exists();
	}
	@Test void traversalAndSymlinkStorageAreRejected()throws Exception {
		// given / when / then
		assertThatThrownBy(()->store.path("../../escape.pdf")).isInstanceOf(RuntimeException.class);var l=claimAfterEnqueue();var key="custom-reports/"+l.requestId()+"/"+l.generation()+"/"+l.attemptId()+".pdf";var path=store.path(key);Files.createDirectories(path.getParent());Path outside=Files.createTempFile("sentinel-owned-symlink-test", ".pdf");
		try{Files.createSymbolicLink(path,outside);assertThatThrownBy(()->store.storeAttempt(l,generate(l))).isInstanceOf(RuntimeException.class);assertThat(Files.size(outside)).isZero();}finally{Files.deleteIfExists(path);Files.deleteIfExists(outside);}
	}
	private JobLease claimAfterEnqueue()throws Exception {enqueue();return claim();}
}
