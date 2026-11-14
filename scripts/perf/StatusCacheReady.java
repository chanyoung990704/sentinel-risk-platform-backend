import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/** 기존 상태 이벤트의 재전달 barrier로 과거 backlog가 측정에 섞이지 않게 한다. */
public final class StatusCacheReady {
    public static void main(String[] args) {
        try {run();}
        catch(Exception failure){System.err.println("STATUS_CACHE_READY_FAILED: "+failure.getClass().getSimpleName());System.exit(1);}
    }
    static void run() throws Exception {
        String topic="sentinel.custom-report.status.v1",group="sentinel-custom-report-status-cache-v1";
        byte[] body=System.in.readNBytes(65537);if(body.length==0||body.length>65536)throw new IllegalArgumentException();
        var mapper=new ObjectMapper();var node=mapper.readTree(body);
        String request=UUID.fromString(node.path("requestId").asText()).toString(),event=UUID.fromString(node.path("eventId").asText()).toString();
        if(!node.path("eventType").asText().equals("STATUS_CHANGED")||node.path("schemaVersion").asInt()!=1)throw new IllegalArgumentException();
        var config=new HashMap<String,Object>();config.put("bootstrap.servers","127.0.0.1:19092");config.put("key.serializer",ByteArraySerializer.class);config.put("value.serializer",ByteArraySerializer.class);
        var barriers=new HashMap<TopicPartition,Long>();
        try(var producer=new KafkaProducer<byte[],byte[]>(config);var admin=AdminClient.create(Map.of("bootstrap.servers","127.0.0.1:19092"))) {
            for(int p=0;p<3;p++) {
                var record=new ProducerRecord<byte[],byte[]>(topic,p,request.getBytes(StandardCharsets.UTF_8),body);record.headers().add("id",event.getBytes(StandardCharsets.UTF_8));
                var metadata=producer.send(record).get(15,TimeUnit.SECONDS);barriers.put(new TopicPartition(topic,p),metadata.offset()+1);
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(180);
            while(System.nanoTime()<deadline) {
                var committed=admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(5,TimeUnit.SECONDS);
                if(barriers.entrySet().stream().allMatch(entry->committed.containsKey(entry.getKey())&&committed.get(entry.getKey()).offset()>=entry.getValue())) {
                    System.out.println("STATUS_CACHE_READY "+mapper.writeValueAsString(Map.of("ready",true,"group",group,"requestId",request,"eventId",event,"at",Instant.now().toString(),"barriers",barriers.entrySet().stream().map(entry->Map.of("partition",entry.getKey().partition(),"committedAtLeast",entry.getValue())).toList())));return;
                }
                Thread.sleep(500);
            }
            throw new IllegalStateException("CATCH_UP_TIMEOUT");
        }
    }
}
