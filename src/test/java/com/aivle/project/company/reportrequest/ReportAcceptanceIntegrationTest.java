package com.aivle.project.company.reportrequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.aivle.project.company.reportrequest.model.*;
import com.aivle.project.company.reportrequest.service.ReportRequestService;
import com.aivle.project.messaging.outbox.OutboxWriter;
import com.aivle.project.support.CustomReportIntegrationSupport;
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
	"spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false","custom-report.admission.global-limit=5","custom-report.admission.user-limit=3"})
class ReportAcceptanceIntegrationTest extends CustomReportIntegrationSupport {
	@Autowired ReportRequestService requests;
	@MockitoSpyBean OutboxWriter outbox;
	CustomReportCommand input(String text){return new CustomReportCommand(null,text);}
	AcceptedReportRequest accept(long user,String key,String text){return requests.accept(user,company,key,input(text));}
	@Test void oneHundredConcurrentSameKeysConvergeToOneRequestEventAndSlot() throws Exception {
		// given
		var pool=Executors.newFixedThreadPool(100);var start=new CountDownLatch(1);var futures=new ArrayList<Future<AcceptedReportRequest>>();
		try {
			for(int i=0;i<100;i++)futures.add(pool.submit(()->{start.await();return accept(users.getFirst(),"same-key-100","동시 요청");}));
			// when
			start.countDown();var ids=new HashSet<UUID>();for(var f:futures)ids.add(f.get(30,TimeUnit.SECONDS).requestId());
			// then
			assertThat(ids).hasSize(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=?",Integer.class,users.getFirst())).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",Integer.class,ids.iterator().next().toString())).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isEqualTo(1);
		} finally {pool.shutdownNow();}
	}
	@Test void identicalRetryNormalizesCrLfAndUserKeysAreIndependent() {
		// given / when
		var first=accept(users.getFirst(),"body-key-01","  내용\r\n둘째 줄  ");var replay=accept(users.getFirst(),"body-key-01","내용\n둘째 줄");
		var other=accept(users.getLast(),"body-key-01","내용\n둘째 줄");
		// then
		assertThat(replay.requestId()).isEqualTo(first.requestId());assertThat(other.requestId()).isNotEqualTo(first.requestId());
		assertThatThrownBy(()->accept(users.getFirst(),"body-key-01","수정 내용")).hasMessageContaining("CONFLICT");
	}
	@Test void outboxFailureRollsBackRequestAndCounters() {
		// given
		doThrow(new DataIntegrityViolationException("fixture outbox insert failure")).when(outbox).append(any());
		// when / then
		assertThatThrownBy(()->accept(users.getFirst(),"rollback-key","원자 저장")).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=?",Integer.class,users.getFirst())).isZero();
		assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(used_count),0) FROM report_admission WHERE scope_key=?",Integer.class,"USER:"+users.getFirst())).isZero();
	}
	@Test void capacityRaceNeverExceedsGlobalFiveOrUserThree() throws Exception {
		// given
		var pool=Executors.newFixedThreadPool(20);var futures=new ArrayList<Future<Boolean>>();
		try {
			// when
			for(int i=0;i<20;i++){final int n=i;futures.add(pool.submit(()->{try{accept(users.get(n%2),"quota-key-"+n,"제한 시험");return true;}catch(com.aivle.project.common.error.CommonException full){return false;}}));}
			int accepted=0;for(var f:futures)if(f.get(30,TimeUnit.SECONDS))accepted++;
			// then
			assertThat(accepted).isEqualTo(5);for(long user:users)assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=?",Integer.class,user)).isLessThanOrEqualTo(3);
			assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'",Integer.class)).isEqualTo(5);
		} finally {pool.shutdownNow();}
	}
	@Test void unicodeCodePointAndKeyBoundariesAreValidated() {
		// given / when / then
		assertThat(accept(users.getFirst(),"unicode-key","😀".repeat(2000)).requestId()).isNotNull();
		for(String value:List.of("😀".repeat(2001),"   ","\uD800"))assertThatThrownBy(()->accept(users.getFirst(),"invalid-key",value)).hasMessageContaining("INVALID");
		assertThatThrownBy(()->accept(users.getFirst(),"bad key!","내용")).hasMessageContaining("INVALID");
	}
	@Test void committedAcceptanceSurvivesLostResponseAndActivePointerChange() {
		// given: 응답을 버린 첫 호출 뒤 active pointer가 없어져도 재해석하지 않는다.
		var first=accept(users.getFirst(),"lost-response","고정 입력");String original=jdbc.queryForObject("SELECT snapshot_id FROM active_data_snapshots WHERE family='FINANCIAL'",String.class);
		try {
			jdbc.update("UPDATE active_data_snapshots SET snapshot_id=NULL WHERE family='FINANCIAL'");
			// when
			var replay=accept(users.getFirst(),"lost-response","고정 입력");
			// then
			assertThat(replay.requestId()).isEqualTo(first.requestId());assertThat(jdbc.queryForObject("SELECT snapshot_id FROM report_requests WHERE id=?",String.class,first.requestId().toString())).isEqualTo(original);
		} finally {jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL'",original);}
	}
}
