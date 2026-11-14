package com.aivle.project.reliability;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.company.reportjob.model.*;
import com.aivle.project.company.reportjob.service.ReportResultService;
import com.aivle.project.company.reportrequest.cache.ReportStatusCache;
import com.aivle.project.company.reportrequest.model.CustomReportCommand;
import com.aivle.project.company.reportrequest.service.ReportRequestQueryService;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("reliability") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(properties={"spring.kafka.listener.auto-startup=false","custom-report.messaging.enabled=false","custom-report.worker.enabled=false","custom-report.status-cache.enabled=true","custom-report.status-cache.auto-startup=false"})
class PortfolioFaultMatrixTest extends ReportFlowIntegrationSupport {
    @Autowired ReportStatusCache cache;@Autowired ReportRequestQueryService queries;@Autowired ReportResultService results;
    final HttpClient http=HttpClient.newHttpClient();
    @AfterEach void resetMock()throws Exception {control(0);}
    void control(int failures)throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control/custom-reports")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("fail_first",failures)))).build(),HttpResponse.BodyHandlers.discarding());assertThat(response.statusCode()).isEqualTo(200);
    }
    @Test void retryKeepsAcceptedSnapshotAndActualChecksumAfterNewPointerActivation()throws Exception {
        // given: 실제 reader/source checksum과 첫 외부 오류를 고정한다.
        UUID id=enqueue();var row=jdbc.queryForMap("SELECT snapshot_id,company_id,target_quarter_key,input_hash,additional_instructions FROM report_requests WHERE id=?",id.toString());
        UUID original=UUID.fromString(row.get("snapshot_id").toString());int quarter=((Number)row.get("target_quarter_key")).intValue();
        var source=sources.load(original,company,quarter);var input=new CustomReportInput(row.get("additional_instructions").toString(),row.get("input_hash").toString());
        String active=jdbc.queryForObject("SELECT snapshot_id FROM active_data_snapshots WHERE family='FINANCIAL'",String.class);
        String replacement=jdbc.queryForObject("SELECT s.id FROM data_snapshots s WHERE s.state='VERIFIED' AND s.id<>? AND EXISTS(SELECT 1 FROM snapshot_report_versions b WHERE b.snapshot_id=s.id) ORDER BY s.id LIMIT 1",String.class,active);
        control(1);var first=claim();assertThatThrownBy(()->generator.generate(source,input,id)).isInstanceOf(ReportGenerationFailure.class);assertThat(claims.failOrRetry(first,"HTTP_503",true,clock.now())).isTrue();
        try {
            // when: 검증된 B가 현재 pointer가 되어도 저장된 요청의 A로 재시도한다.
            jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL'",replacement);Thread.sleep(1400);
            var second=claim();var repeated=sources.load(original,company,quarter);assertThat(repeated.checksum()).isEqualTo(source.checksum());
            assertThat(results.storeAndComplete(second,generator.generate(repeated,input,id))).isEqualTo(CompletionDecision.COMPLETED);
            // then: mock의 독립 checksum 검증을 통과한 두 호출은 모두 A다.
            var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/journal")).GET().build(),HttpResponse.BodyHandlers.ofString());
            var calls=new ArrayList<Map<String,Object>>();for(var entry:mapper.readTree(response.body()))if(entry.path("requestId").asText().equals(id.toString())){
                assertThat(entry.path("sourceSnapshot").asText()).isEqualTo(original.toString());assertThat(entry.path("sourceChecksum").asText()).isEqualTo(source.checksum());calls.add(mapper.convertValue(entry,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
            }
            assertThat(calls).hasSize(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_artifacts WHERE request_id=?",Integer.class,id.toString())).isEqualTo(1);
            evidence("fixed-source-retry",Map.of("requestId",id,"acceptedSnapshot",original,"newActiveSnapshot",replacement,"checksum",source.checksum(),"calls",calls,"attempts",2,"artifacts",1));
        }finally{jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL'",active);}
    }
    @Test void ownedCacheNamespaceLossRebuildsAllStatusesFromDatabase()throws Exception {
        // given: 시험 소유 사용자 키 열 개만 대상으로 한다.
        var ids=new ArrayList<UUID>();long owner=users.getFirst();var before=new HashMap<UUID,ReportRequestQueryService.Status>();
        for(int n=0;n<10;n++){var id=requests.accept(owner,company,"namespace-"+UUID.randomUUID(),new CustomReportCommand(null,"캐시 소실 검증")).requestId();ids.add(id);before.put(id,queries.getOwned(owner,id));assertThat(cache.getOwnedStatus(owner,id)).isPresent();}
        // when: 다른 사용자/인증 Redis 키를 지우지 않고 이 시험의 전체 키를 유실시킨다.
        for(UUID id:ids)cache.evict(owner,id);
        // then
        for(UUID id:ids){assertThat(cache.getOwnedStatus(owner,id)).isEmpty();assertThat(queries.getOwned(owner,id)).isEqualTo(before.get(id));assertThat(cache.getOwnedStatus(owner,id)).contains(before.get(id));}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_requests WHERE user_id=?",Integer.class,owner)).isEqualTo(10);
        evidence("cache-owned-namespace-loss",Map.of("requestIds",ids,"keysLost",10,"dbRowsRetained",10,"reconstructed",10));
    }
    void evidence(String name,Object value)throws Exception {
        Path out=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/delivery"));Files.createDirectories(out);
        Files.writeString(out.resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-"+name+".json"),mapper.writeValueAsString(value),StandardOpenOption.CREATE_NEW);
    }
}
