package com.aivle.project.company.reportrequest.cache;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.company.reportrequest.model.*;
import com.aivle.project.company.reportrequest.service.*;
import com.aivle.project.messaging.CustomReportTopics;
import com.aivle.project.messaging.quarantine.QuarantineReplayService;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.status-cache.enabled=true","custom-report.status-cache.auto-startup=false"})
class ReportStatusCacheProjectorIntegrationTest extends ReportFlowIntegrationSupport {
    @Autowired ReportStatusCache cache; @Autowired ReportStatusCacheProjector projector;
    @Autowired ReportRequestQueryService queries; @Autowired QuarantineReplayService replays;
    final List<Long> offsets=new ArrayList<>();
    ConsumerRecord<byte[],byte[]> record(UUID id,String body) throws Exception {
        long offset=System.currentTimeMillis()*1000+new java.security.SecureRandom().nextInt(1000);offsets.add(offset);
        var record=new ConsumerRecord<byte[],byte[]>(CustomReportTopics.STATUS,0,offset,id.toString().getBytes(StandardCharsets.UTF_8),body.getBytes(StandardCharsets.UTF_8));
        String event=body.startsWith("{\"eventId\"")?mapper.readTree(body).path("eventId").asText():UUID.randomUUID().toString();
        record.headers().add("id",event.getBytes(StandardCharsets.UTF_8));return record;
    }
    @AfterEach void cleanOwnedQuarantine() {
        for(long offset:offsets) for(String id:jdbc.queryForList("SELECT id FROM quarantined_messages WHERE consumer_name='custom-report-status-cache-v1' AND source_topic=? AND source_partition=0 AND source_offset=?",String.class,CustomReportTopics.STATUS,offset)) {
            jdbc.update("DELETE FROM quarantine_replay_commands WHERE quarantine_id=?",id);jdbc.update("DELETE FROM outbox_events WHERE aggregateid=?",id);jdbc.update("DELETE FROM quarantined_messages WHERE id=?",id);
        }
        for(long user:users)jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);
    }
    @Test void olderHintRebuildsCurrentDatabaseRatherThanItsWireState() throws Exception {
        // given
        UUID id=enqueue();String body=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='STATUS_CHANGED' ORDER BY created_at,id LIMIT 1",String.class,id.toString());
        claim();var expected=queries.getOwnedFromDatabase(users.getFirst(),id);assertThat(expected.status()).isEqualTo("PROCESSING");
        var count=new AtomicInteger();
        // when
        projector.onStatus(record(id,body),count::incrementAndGet);
        // then
        assertThat(count.get()).isEqualTo(1);assertThat(cache.getOwnedStatus(users.getFirst(),id)).contains(expected);
        assertThat(cache.getOwnedStatus(users.getLast(),id)).isEmpty();
    }
    @Test void malformedStatusIsDurablyQuarantinedOnceBeforeAck() throws Exception {
        // given
        UUID id=enqueue();var message=record(id,"{broken");var count=new AtomicInteger();
        // when
        projector.onStatus(message,count::incrementAndGet);projector.onStatus(message,count::incrementAndGet);
        // then
        assertThat(count.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quarantined_messages WHERE consumer_name='custom-report-status-cache-v1' AND source_topic=? AND source_offset=?",Integer.class,CustomReportTopics.STATUS,message.offset())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events o JOIN quarantined_messages q ON q.dlt_event_id=o.id WHERE q.consumer_name='custom-report-status-cache-v1' AND q.source_topic=? AND q.source_offset=?",Integer.class,CustomReportTopics.STATUS,message.offset())).isEqualTo(1);
    }
    @Test void statusQuarantineCannotBeReplayedAsANewReportRequest() throws Exception {
        // given
        var id=requests.accept(users.getFirst(),company,"cache-replay-"+UUID.randomUUID(),new CustomReportCommand(null,"현금흐름 검토")).requestId();
        var message=record(id,"{broken");projector.onStatus(message,()->{});
        var quarantine=UUID.fromString(jdbc.queryForObject("SELECT id FROM quarantined_messages WHERE consumer_name='custom-report-status-cache-v1' AND source_topic=? AND source_offset=?",String.class,CustomReportTopics.STATUS,message.offset()));
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_ADMIN'",users.getLast());
        // when / then
        assertThatThrownBy(()->replays.repairAndReplay(users.getLast(),quarantine,UUID.randomUUID(),id,"상태 힌트 격리 시험")).isInstanceOf(ReportRequestException.class).hasMessage("CUSTOM_REPORT_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",Integer.class,id.toString())).isEqualTo(1);
    }
}
