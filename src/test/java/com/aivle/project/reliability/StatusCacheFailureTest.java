package com.aivle.project.reliability;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.reporting.internal.company.reportrequest.cache.ReportStatusCache;
import com.aivle.project.reporting.internal.company.reportjob.service.ReportWorkerLifecycle;
import com.aivle.project.reporting.api.CustomReportTopics;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;

@Tag("reliability") @ActiveProfiles("local") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.worker.enabled=true","custom-report.messaging.enabled=true","custom-report.messaging.auto-startup=true","custom-report.status-cache.enabled=true","custom-report.status-cache.auto-startup=true"})
class StatusCacheFailureTest extends ReportFlowIntegrationSupport {
    static final String GROUP="sentinel-status-cache-fault-"+UUID.randomUUID();
    @DynamicPropertySource static void group(DynamicPropertyRegistry properties){properties.add("custom-report.status-cache.group-id",()->GROUP);}
    @LocalServerPort int port; @Autowired PasswordEncoder passwords; @Autowired ReportStatusCache cache;
    @Autowired ReportWorkerLifecycle worker; @Autowired MeterRegistry meters;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    String token;boolean cacheStopped;
    @AfterEach void restoreAndStopOwnedWorker() throws Exception {
        try { cacheControl("up");mock(false); }
        finally { worker.stop();for(long user:users){jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?",user);jdbc.update("DELETE FROM user_roles WHERE user_id=?",user);} }
    }
    @Test @Timeout(600) void cacheOutageKeepsAuthenticatedReadAndWorkerCompletionThenProjectorResumesForThreeSeeds() throws Exception {
        // given: 인증 Redis는 계속 정상이며 cache Redis만 중단한다.
        String password=UUID.randomUUID()+"aA1!", email=jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,users.getFirst());
        jdbc.update("UPDATE users SET password=?,password_changed_at=NOW() WHERE id=?",passwords.encode(password),users.getFirst());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_USER'",users.getFirst());
        var login=send("POST","/api/auth/login",Map.of("email",email,"password",password),false,null);assertThat(login.statusCode()).isEqualTo(200);
        token=mapper.readTree(login.body()).path("data").path("accessToken").asText();assertThat(token).isNotBlank();
        var all=new ArrayList<Map<String,Object>>();
        try(var admin=AdminClient.create(Map.of("bootstrap.servers","127.0.0.1:19092"));var producer=new KafkaProducer<byte[],byte[]>(Map.of("bootstrap.servers","127.0.0.1:19092","key.serializer",ByteArraySerializer.class,"value.serializer",ByteArraySerializer.class))) {
            for(int seed=1;seed<=3;seed++) {
                mock(true);
                var accepted=send("POST","/api/companies/"+company+"/custom-report-requests",Map.of("additionalInstructions","현금흐름 검토"),true,UUID.randomUUID().toString());assertThat(accepted.statusCode()).isEqualTo(202);
                UUID id=UUID.fromString(mapper.readTree(accepted.body()).path("data").path("requestId").asText());
                await(()->inflight(id),90,"실제 모의 HTTP 진입 barrier");
                assertThat(send("GET","/api/report-requests/"+id,null,true,null).statusCode()).isEqualTo(200);
                String older=jdbc.queryForObject("SELECT payload FROM outbox_events WHERE aggregateid=? AND type='STATUS_CHANGED' ORDER BY created_at,id LIMIT 1",String.class,id.toString());
                String event=mapper.readTree(older).path("eventId").asText();
                for(int p=0;p<3;p++) {
                    var anchor=new ProducerRecord<byte[],byte[]>(CustomReportTopics.STATUS,p,id.toString().getBytes(StandardCharsets.UTF_8),older.getBytes(StandardCharsets.UTF_8));anchor.headers().add("id",event.getBytes(StandardCharsets.UTF_8));var boundary=producer.send(anchor).get(10,TimeUnit.SECONDS);
                    await(()->committed(admin,new TopicPartition(boundary.topic(),boundary.partition()))>=boundary.offset()+1,45,"projector 시작 offset barrier");
                }
                double retryBefore=counter("retry");long faultAt=System.currentTimeMillis();
                cacheStopped=true;cacheControl("stop");assertThat(inflight(id)).as("cache 중단 시에도 실제 HTTP 응답 보류").isTrue();
                // when: HTTP 응답을 풀어 DB 결과를 확정한다. 캐시 쓰기는 실패해야 한다.
                mock(false);await(()->"SUCCEEDED".equals(jdbc.queryForObject("SELECT status FROM report_requests WHERE id=?",String.class,id.toString())),90,"cache outage 중 DB 완료");
                long began=System.nanoTime();var fallback=send("GET","/api/report-requests/"+id,null,true,null);long fallbackMs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began);
                assertThat(fallback.statusCode()).isEqualTo(200);assertThat(mapper.readTree(fallback.body()).path("data").path("status").asText()).isEqualTo("SUCCEEDED");assertThat(fallbackMs).isLessThan(5000);
                var wire=new ProducerRecord<byte[],byte[]>(CustomReportTopics.STATUS,id.toString().getBytes(StandardCharsets.UTF_8),older.getBytes(StandardCharsets.UTF_8));wire.headers().add("id",event.getBytes(StandardCharsets.UTF_8));var metadata=producer.send(wire).get(10,TimeUnit.SECONDS);
                var partition=new TopicPartition(metadata.topic(),metadata.partition());
                await(()->counter("retry")>retryBefore,30,"projector cache 실패 관측");assertThat(committed(admin,partition)).isLessThan(metadata.offset()+1);
                // then: 자기 group만 재개하고 오래된 wire 상태 대신 현재 DB를 재구성한다.
                long restarted=System.currentTimeMillis();cacheControl("up");cacheStopped=false;
                var projected=new java.util.concurrent.atomic.AtomicReference<com.aivle.project.reporting.api.ReportRequestView>();
                await(()-> {
                    var value=cache.getOwnedStatus(users.getFirst(),id);
                    if(value.isPresent()&&value.get().status().equals("SUCCEEDED")&&committed(admin,partition)>=metadata.offset()+1){projected.set(value.get());return true;}
                    return false;
                },90,"API miss fill 없이 projector 갱신+ACK 재개");
                var current=projected.get();
                assertThat(current.status()).isEqualTo("SUCCEEDED");assertThat(current.stateVersion()).isEqualTo(jdbc.queryForObject("SELECT state_version FROM report_requests WHERE id=?",Long.class,id.toString()));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_jobs WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
                var row=new LinkedHashMap<String,Object>();row.put("seed",seed);row.put("requestId",id);row.put("faultAtMs",faultAt);row.put("restartAtMs",restarted);row.put("resumedAtMs",System.currentTimeMillis());row.put("fallbackMs",fallbackMs);row.put("oldEventId",event);row.put("partition",metadata.partition());row.put("offset",metadata.offset());row.put("committedOffset",committed(admin,partition));row.put("stateVersion",current.stateVersion());all.add(row);
            }
        }
        Path out=output();Files.writeString(out.resolve(label()+"-status-cache-three-seeds.json"),mapper.writeValueAsString(all),StandardOpenOption.CREATE_NEW);
    }
    double counter(String outcome){var counter=meters.find("report.status.projector").tag("outcome",outcome).counter();return counter==null?0:counter.count();}
    long committed(AdminClient admin,TopicPartition partition){try{var value=admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata().get(3,TimeUnit.SECONDS).get(partition);return value==null?-1:value.offset();}catch(Exception unavailable){return -1;}}
    boolean inflight(UUID id){try{var r=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/inflight/custom-reports")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());for(var row:mapper.readTree(r.body()))if(row.path("requestId").asText().equals(id.toString()))return true;return false;}catch(Exception failure){return false;}}
    void mock(boolean hold)throws Exception {var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("seed","cache-fault","hold_response",hold,"delay_ms",0)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);}
    HttpResponse<String> send(String method,String path,Object body,boolean auth,String key)throws Exception {var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/json");if(auth)builder.header("Authorization","Bearer "+token);if(key!=null)builder.header("Idempotency-Key",key);builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    void cacheControl(String action)throws Exception {Path log=output().resolve(label()+"-cache-"+action+"-"+UUID.randomUUID()+".log");var process=new ProcessBuilder("python3","scripts/reliability/cache_runtime.py",action).redirectErrorStream(true).redirectOutput(log.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("소유 캐시 구성 요소 제어 한도");}assertThat(process.exitValue()).isZero();}
    void await(BooleanSupplier condition,int seconds,String name)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}throw new AssertionError(name+" 관찰 한도 초과");}
    Path output()throws Exception {Path path=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/status-cache"));Files.createDirectories(path);return path;}
    String label(){return System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual");}
}
