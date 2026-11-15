package com.aivle.project.reporting.internal.company.reportrequest.cache;
import com.aivle.project.reporting.api.CustomReportCommand;

import static org.assertj.core.api.Assertions.*;

import com.aivle.project.reporting.api.ReportRequestException;
import com.aivle.project.reporting.internal.company.reportrequest.service.*;
import com.aivle.project.app.internal.perf.QueryCountProbe;
import com.aivle.project.support.CustomReportIntegrationSupport;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.status-cache.enabled=true","custom-report.status-cache.auto-startup=false"})
class ReportStatusCacheIntegrationTest extends CustomReportIntegrationSupport {
    @Autowired ReportStatusCache cache;
    @Autowired ReportRequestQueryService queries;
    @Autowired ReportRequestService requests;
    @Autowired QueryCountProbe probe;
    @Autowired com.aivle.project.reporting.internal.messaging.inbox.ReportJobInbox inbox;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    UUID request() { return requests.accept(users.getFirst(),company,"cache-"+UUID.randomUUID(),new CustomReportCommand(null,"현금흐름 검토")).requestId(); }
    com.aivle.project.reporting.api.ReportRequestView status(UUID id,long version) { return new com.aivle.project.reporting.api.ReportRequestView(id,"PROCESSING",version,1,1,null,null,null); }
    @Test void missLoadsOwnedDatabaseAndTwoSecondTtlExpires() throws Exception {
        // given
        var id=request();long owner=users.getFirst();assertThat(cache.getOwnedStatus(owner,id)).isEmpty();
        // when
        var fresh=queries.getOwned(owner,id);
        // then
        assertThat(cache.getOwnedStatus(owner,id)).contains(fresh);Thread.sleep(2100);
        assertThat(cache.getOwnedStatus(owner,id)).isEmpty();assertThat(queries.getOwned(owner,id)).isEqualTo(fresh);
    }
    @Test void olderVersionCannotOverwriteFiveOrExtendItsTtl() throws Exception {
        // given
        var id=request();long owner=users.getFirst();
        assertThat(cache.putIfNewer(owner,id,status(id,5),Duration.ofSeconds(2))).isTrue();
        Thread.sleep(1100);
        // when
        assertThat(cache.putIfNewer(owner,id,status(id,4),Duration.ofSeconds(2))).isFalse();
        // then
        assertThat(cache.getOwnedStatus(owner,id).orElseThrow().stateVersion()).isEqualTo(5);
        Thread.sleep(1000);assertThat(cache.getOwnedStatus(owner,id)).isEmpty();
    }
    @Test void versionComparisonPreservesLongPrecision() {
        // given
        var id=request();long owner=users.getFirst();long high=9007199254740993L;
        cache.putIfNewer(owner,id,status(id,high),Duration.ofSeconds(2));
        // when / then
        assertThat(cache.putIfNewer(owner,id,status(id,high-1),Duration.ofSeconds(2))).isFalse();
        assertThat(cache.getOwnedStatus(owner,id).orElseThrow().stateVersion()).isEqualTo(high);
    }
    @Test void delayedProjectorCannotExposeAnOlderDatabaseVersion() throws Exception {
        // given
        var id=request();var first=queries.getOwned(users.getFirst(),id);assertThat(first.status()).isEqualTo("ACCEPTED");
        var body=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",String.class,id.toString());
        inbox.accept(mapper.readValue(body,com.aivle.project.reporting.internal.messaging.outbox.ReportEvent.class));
        assertThat(cache.getOwnedStatus(users.getFirst(),id).orElseThrow().stateVersion()).isEqualTo(first.stateVersion());
        // when / then: 권한 확인 SQL에 현재 버전도 포함할 수 있어 이벤트 지연을 감춘다.
        var current=queries.getOwned(users.getFirst(),id);
        assertThat(current.status()).isEqualTo("ENQUEUED");assertThat(current.stateVersion()).isGreaterThan(first.stateVersion());
    }
    @Test void anotherOwnerAndDeletedUserCannotReadACachedOwnedResult() {
        // given
        var id=request();long owner=users.getFirst();queries.getOwned(owner,id);
        // when / then
        assertThat(cache.getOwnedStatus(users.getLast(),id)).isEmpty();
        assertThatThrownBy(()->queries.getOwned(users.getLast(),id)).isInstanceOf(ReportRequestException.class).hasMessage("CUSTOM_REPORT_NOT_FOUND");
        jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP(6) WHERE id=?",owner);
        try { assertThatThrownBy(()->queries.getOwned(owner,id)).isInstanceOf(ReportRequestException.class).hasMessage("CUSTOM_REPORT_NOT_FOUND"); }
        finally { jdbc.update("UPDATE users SET deleted_at=NULL WHERE id=?",owner); }
        assertThatThrownBy(()->queries.getOwned(null,id)).isInstanceOf(ReportRequestException.class).hasMessage("CUSTOM_REPORT_UNAUTHORIZED");
    }
    @Test void hundredConcurrentColdReadsRemainEqualAndExposeDatabaseAmplification() throws Exception {
        // given
        var id=request();long owner=users.getFirst();var expected=queries.getOwnedFromDatabase(owner,id);cache.evict(owner,id);
        var start=new CountDownLatch(1);var ready=new CountDownLatch(100);var latencies=new ConcurrentLinkedQueue<Double>();var counts=new ConcurrentLinkedQueue<Long>();
        // when
        try(var pool=Executors.newFixedThreadPool(100)) {
            var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<100;i++) tasks.add(pool.submit(()-> {
                try { ready.countDown();start.await();probe.begin("report_status");long began=System.nanoTime();
                    try { assertThat(queries.getOwned(owner,id)).isEqualTo(expected); }
                    finally { counts.add(probe.end().statements());latencies.add((System.nanoTime()-began)/1_000_000.0); }
                } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new IllegalStateException(interrupted); }
            }));
            boolean allReady=ready.await(10,TimeUnit.SECONDS);start.countDown();assertThat(allReady).isTrue();for(var task:tasks)task.get(20,TimeUnit.SECONDS);
        }
        // then
        assertThat(counts).hasSize(100);assertThat(counts).allMatch(n->n==2||n==4);
        var sorted=latencies.stream().sorted().toList();var evidence=new LinkedHashMap<String,Object>();
        evidence.put("requests",100);evidence.put("concurrency",100);evidence.put("sqlStatements",counts.stream().mapToLong(Long::longValue).sum());
        evidence.put("coldMissLoads",counts.stream().filter(n->n==4).count());evidence.put("p95Millis",sorted.get(94));evidence.put("cacheTtlSeconds",2);
        evidence.put("sqlSamples",new ArrayList<>(counts));evidence.put("latencyMillis",new ArrayList<>(latencies));
        Path out=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/status-cache"));Files.createDirectories(out);
        Files.writeString(out.resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-cold-miss-100-"+UUID.randomUUID()+".json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(evidence),StandardOpenOption.CREATE_NEW);
    }
}
