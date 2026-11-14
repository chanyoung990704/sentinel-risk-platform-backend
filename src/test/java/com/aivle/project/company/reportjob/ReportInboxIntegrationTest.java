package com.aivle.project.company.reportjob;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.company.reportrequest.model.*;
import com.aivle.project.company.reportrequest.service.ReportRequestService;
import com.aivle.project.company.reportjob.repository.ReportJobRepository;
import com.aivle.project.messaging.inbox.*;
import com.aivle.project.messaging.outbox.*;
import com.aivle.project.support.CustomReportIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio",
	"spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportInboxIntegrationTest extends CustomReportIntegrationSupport {
	@Autowired ReportRequestService requests;@Autowired ReportJobInbox inbox;@Autowired ObjectMapper mapper;
	@MockitoSpyBean ReportJobRepository jobs;
	ReportEvent event() throws Exception {
		var accepted=requests.accept(users.getFirst(),company,"inbox-key-01",new CustomReportCommand(null,"Inbox 시험"));
		return mapper.readValue(jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,accepted.requestId().toString()),ReportEvent.class);
	}
	@Test void duplicateHundredTimesAndTwentyConcurrentCallsProduceOneDurableJob() throws Exception {
		// given
		var event=event();var pool=Executors.newFixedThreadPool(20);var results=new ArrayList<Future<InboxAcceptance>>();
		try {
			// when
			for(int i=0;i<20;i++)results.add(pool.submit(()->inbox.accept(event)));
			int fresh=0;for(var f:results)if(f.get(30,TimeUnit.SECONDS)==InboxAcceptance.ACCEPTED)fresh++;
			for(int i=0;i<100;i++)assertThat(inbox.accept(event)).isEqualTo(InboxAcceptance.DUPLICATE);
			// then
			assertThat(fresh).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,event.requestId().toString())).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_events WHERE request_id=?",Integer.class,event.requestId().toString())).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT state FROM report_jobs WHERE request_id=?",String.class,event.requestId().toString())).isEqualTo("READY");
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=? AND type='STATUS_CHANGED'",Integer.class,event.requestId().toString())).isEqualTo(1);
		} finally {pool.shutdownNow();}
	}
	@Test void inboxInsertAndJobFailureRollbackTogetherThenRetrySucceeds() throws Exception {
		// given
		var event=event();doThrow(new DataIntegrityViolationException("fixture job insert")).when(jobs).createIfAbsent(event.requestId());
		// when / then
		assertThatThrownBy(()->inbox.accept(event)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_events WHERE request_id=?",Integer.class,event.requestId().toString())).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,event.requestId().toString())).isEqualTo("ACCEPTED");
		doCallRealMethod().when(jobs).createIfAbsent(event.requestId());assertThat(inbox.accept(event)).isEqualTo(InboxAcceptance.ACCEPTED);
	}
	@Test void newEventIdForSameRequestDoesNotRestartFailedJob() throws Exception {
		// given
		var event=event();inbox.accept(event);jdbc.update("UPDATE report_jobs SET state='FAILED' WHERE request_id=?",event.requestId().toString());
		jdbc.update("UPDATE report_requests SET status='FAILED' WHERE id=?",event.requestId().toString());
		var second=new ReportEvent(UUID.randomUUID(),event.eventType(),1,event.requestId(),event.occurredAt(),event.payload());
		// when / then
		assertThat(inbox.accept(second)).isEqualTo(InboxAcceptance.DUPLICATE);
		assertThat(jdbc.queryForObject("SELECT state FROM report_jobs WHERE request_id=?",String.class,event.requestId().toString())).isEqualTo("FAILED");
	}
	@Test void forgedPayloadIsRejectedBeforeInboxCommit() throws Exception {
		// given
		var event=event();var p=event.payload();var forged=new ReportEvent(event.eventId(),event.eventType(),1,event.requestId(),event.occurredAt(),new ReportEvent.Payload(p.companyId(),p.targetQuarterKey(),p.snapshotId(),p.inputHash(),"변조 원문",p.stateVersion(),p.status()));
		// when / then
		assertThatThrownBy(()->inbox.accept(forged)).hasMessageContaining("INVALID");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbox_events WHERE request_id=?",Integer.class,event.requestId().toString())).isZero();
	}
}
