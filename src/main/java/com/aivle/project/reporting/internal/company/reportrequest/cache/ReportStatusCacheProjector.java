package com.aivle.project.reporting.internal.company.reportrequest.cache;

import com.aivle.project.reporting.internal.company.reportrequest.service.ReportRequestQueryService;
import com.aivle.project.reporting.api.CustomReportTopics;
import com.aivle.project.reporting.internal.messaging.consumer.*;
import com.aivle.project.reporting.internal.messaging.quarantine.QuarantineService;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.*;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** 상태 이벤트는 갱신 힌트이며 응답 데이터와 소유자는 항상 현재 DB에서 재구성한다. */
@Component @ConditionalOnProperty(name="custom-report.status-cache.enabled",havingValue="true")
public class ReportStatusCacheProjector {
    private final ReportStatusCache cache;private final ReportRequestQueryService queries;
    private final QuarantineService quarantine;private final MeterRegistry meters;private final ObjectReader reader;
    public ReportStatusCacheProjector(ReportStatusCache cache,ReportRequestQueryService queries,QuarantineService quarantine,MeterRegistry meters,ObjectMapper mapper){
        this.cache=cache;this.queries=queries;this.quarantine=quarantine;this.meters=meters;
        var copy=mapper.copy();copy.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(65536).maxNumberLength(64).build());copy.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        reader=copy.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @KafkaListener(id="custom-report-status-cache",topics=CustomReportTopics.STATUS,groupId="${custom-report.status-cache.group-id:sentinel-custom-report-status-cache-v1}",containerFactory="reportStatusKafkaListenerContainerFactory",autoStartup="${custom-report.status-cache.auto-startup:${custom-report.messaging.auto-startup:false}}")
    public void onStatus(ConsumerRecord<byte[],byte[]> record,Acknowledgment acknowledgment){
        try {
            UUID id;
            try{id=decode(record);}
            catch(PoisonReportMessage invalid){quarantine.quarantine(record,invalid.getMessage());count("quarantined");acknowledgment.acknowledge();return;}
            var current=queries.latestForProjection(id);
            if(current.isPresent()){var value=current.get();boolean applied=cache.putIfNewer(value.owner(),id,value.status(),ReportStatusCache.TTL);count(applied?"applied":"stale");}
            else count("missing");
            acknowledgment.acknowledge();
        } catch(RuntimeException unavailable){count("retry");throw new IllegalStateException("STATUS_CACHE_RETRY");}
    }
    private void count(String outcome){meters.counter("report.status.projector","outcome",outcome).increment();}
    private UUID decode(ConsumerRecord<byte[],byte[]> record){
        if(!CustomReportTopics.STATUS.equals(record.topic())||record.partition()<0||record.offset()<0)throw new PoisonReportMessage("STATUS_SOURCE_INVALID");
        if(record.value()==null||record.value().length==0||record.value().length>ReportWireProtocol.MAX_BODY||record.key()==null||record.key().length>1024)throw new PoisonReportMessage("STATUS_MESSAGE_SIZE");
        try {
            JsonNode node=reader.readTree(record.value());
            if(node==null||!node.isObject()||!node.path("schemaVersion").isIntegralNumber()||node.path("schemaVersion").intValue()!=1||!node.path("eventType").asText().equals("STATUS_CHANGED")||!node.path("payload").isObject())throw new IllegalArgumentException();
            UUID id=canonical(node.path("requestId").asText()),event=canonical(node.path("eventId").asText());Instant.parse(node.path("occurredAt").asText());
            if(!strict(record.key()).equals(id.toString()))throw new IllegalArgumentException();
            int count=0;byte[] header=null;for(var entry:record.headers().headers("id")){count++;header=entry.value();}
            if(count!=1||header==null||header.length>128||!strict(header).equals(event.toString()))throw new IllegalArgumentException();
            return id;
        } catch(Exception invalid){throw new PoisonReportMessage("STATUS_MESSAGE_INVALID");}
    }
    private UUID canonical(String text){UUID id=UUID.fromString(text);if(!id.toString().equals(text))throw new IllegalArgumentException();return id;}
    private String strict(byte[] value)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(value)).toString();}
}
