package com.aivle.project.company.reportrequest.cache;

import com.aivle.project.company.reportrequest.model.ReportRequestStatus;
import com.aivle.project.company.reportrequest.service.ReportRequestQueryService.Status;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import io.lettuce.core.*;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** 권한 확인 뒤 사용하는 재구성 가능한 읽기 캐시. 인증 Redis 연결과 자원을 공유하지 않는다. */
@Component
public class ReportStatusCache implements DisposableBean {
    public static final Duration TTL=Duration.ofSeconds(2);
    private final boolean enabled;
    private final ObjectMapper mapper;
    private final ObjectReader reader;
    private final MeterRegistry meters;
    private final LettuceConnectionFactory factory;
    private final DefaultClientResources resources;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> update=new DefaultRedisScript<>();
    private record Stored(long owner,Status status) { }

    public ReportStatusCache(ObjectMapper mapper,MeterRegistry meters,
        @Value("${custom-report.status-cache.enabled:false}") boolean enabled,
        @Value("${custom-report.status-cache.redis.host:127.0.0.1}") String host,
        @Value("${custom-report.status-cache.redis.port:16380}") int port,
        @Value("${custom-report.status-cache.redis.password:}") String password) {
        this.enabled=enabled;this.mapper=mapper;this.meters=meters;
        var copy=mapper.copy();copy.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(1024).maxNumberLength(64).build());
        reader=copy.readerFor(Stored.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        update.setLocation(new ClassPathResource("redis/report-status-version.lua"));update.setResultType(Long.class);
        if(!enabled){factory=null;resources=null;redis=null;return;}
        if(host.isBlank()||port<1||port>65535)throw new IllegalArgumentException("상태 캐시 연결 설정 확인 필요");
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        var options=ClientOptions.builder().autoReconnect(true).disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).requestQueueSize(128)
            .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(200)).build()).timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200))).build();
        var client=LettuceClientConfiguration.builder().clientResources(resources).clientOptions(options).commandTimeout(Duration.ofMillis(200)).shutdownTimeout(Duration.ZERO).build();
        var server=new RedisStandaloneConfiguration(host,port);if(!password.isEmpty())server.setPassword(password);
        factory=new LettuceConnectionFactory(server,client);factory.afterPropertiesSet();factory.start();
        redis=new StringRedisTemplate(factory);redis.afterPropertiesSet();
    }
    public boolean isEnabled(){return enabled;}
    private String key(long owner,UUID id){if(owner<=0||id==null)throw new IllegalArgumentException("상태 캐시 키 확인 필요");return "custom-report:status:v1:"+owner+":"+id;}
    private void count(String outcome){meters.counter("report.status.cache","outcome",outcome).increment();}
    public Optional<Status> getOwnedStatus(long owner,UUID id) {
        return read(owner,id,-1);
    }
    public Optional<Status> getOwnedStatus(long owner,UUID id,long expectedVersion) {
        return read(owner,id,expectedVersion);
    }
    private Optional<Status> read(long owner,UUID id,long expectedVersion) {
        if(!enabled)return Optional.empty();
        try {
            Object value=redis.opsForHash().get(key(owner,id),"body");
            if(value==null){count("miss");return Optional.empty();}
            String body=value.toString();if(body.length()>8192)throw new IllegalArgumentException("CACHE_VALUE_SIZE");
            Stored stored=reader.readValue(body);Status status=stored.status();
            if(stored.owner()!=owner||status==null||!id.equals(status.requestId())||status.stateVersion()<1||status.generation()<1||status.attemptNo()<0)throw new IllegalArgumentException("CACHE_VALUE_INVALID");
            ReportRequestStatus.valueOf(status.status());
            if(expectedVersion>=0&&status.stateVersion()!=expectedVersion){count("version_miss");count("miss");return Optional.empty();}
            count("hit");
            return Optional.of(new Status(id,status.status(),status.stateVersion(),status.generation(),status.attemptNo(),status.errorCode(),status.retryAt(),"SUCCEEDED".equals(status.status())?"/api/report-requests/"+id+"/file":null));
        } catch(com.fasterxml.jackson.core.JsonProcessingException|IllegalArgumentException invalid){count("invalid");return Optional.empty();}
        catch(RuntimeException unavailable){count("read_error");return Optional.empty();}
    }
    /** projector는 실패를 전파해 ACK를 보류한다. API 조회만 putBestEffort를 쓴다. */
    public boolean putIfNewer(long owner,UUID id,Status status,Duration ttl) {
        if(!enabled)return false;
        if(status==null||!id.equals(status.requestId())||status.stateVersion()<1||ttl.isNegative()||ttl.isZero()||ttl.compareTo(TTL)>0)throw new IllegalArgumentException("상태 캐시 값/TTL 확인 필요");
        try {
            Long applied=redis.execute(update,List.of(key(owner,id)),Long.toString(status.stateVersion()),mapper.writeValueAsString(new Stored(owner,status)),Long.toString(ttl.toMillis()));
            boolean changed=Long.valueOf(1).equals(applied);count(changed?"put":"stale");return changed;
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalStateException("상태 캐시 직렬화 실패",invalid);}
    }
    public void putBestEffort(long owner,UUID id,Status status) {
        try {putIfNewer(owner,id,status,TTL);}catch(RuntimeException unavailable){count("write_error");}
    }
    public void evict(long owner,UUID id){if(enabled)redis.delete(key(owner,id));}
    @Override public void destroy(){if(factory!=null)factory.destroy();if(resources!=null)resources.shutdown();}
}
