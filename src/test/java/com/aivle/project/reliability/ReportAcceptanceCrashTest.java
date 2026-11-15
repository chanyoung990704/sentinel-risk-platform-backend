package com.aivle.project.reliability;
import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.ReportFlowIntegrationSupport;
import com.aivle.project.reporting.api.CustomReportCommand;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("reliability") @ActiveProfiles("local")
@SpringBootTest(properties={"spring.kafka.listener.auto-startup=false","custom-report.worker.enabled=false","custom-report.messaging.enabled=false","decorator.datasource.p6spy.enable-logging=false"})
class ReportAcceptanceCrashTest extends ReportFlowIntegrationSupport {
    @Test void actualProcessHaltBeforeAndAfterAcceptCommitPreservesAtomicityAndReplay()throws Exception {
        Path out=Path.of(System.getenv().getOrDefault("PORTFOLIO_K_RUN_DIR","build/reports/delivery"));Files.createDirectories(out);
        var evidence=new ArrayList<Map<String,Object>>();
        for(String point:List.of("before","after")) {
            // given: 원문/비밀을 출력하지 않는 실제 business service 자식 JVM.
            String key="crash-"+UUID.randomUUID();long user=users.getFirst();
            long outboxBefore=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class);
            String cp=Files.readString(Path.of("build/portfolio-report-classpath.txt")).strip();
            var child=new ProcessBuilder("java","-Xmx192m","--class-path",cp,"src/test/java/com/aivle/project/reliability/ReportAcceptanceCrashProcess.java",point,String.valueOf(user),String.valueOf(company),key)
                .redirectErrorStream(true).redirectOutput(out.resolve("accept-"+point+"-"+UUID.randomUUID()+".log").toFile()).start();
            // when
            try{assertThat(child.waitFor(30,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isEqualTo(point.equals("before")?93:94);}
            finally{if(child.isAlive()){child.destroyForcibly();assertThat(child.waitFor(10,TimeUnit.SECONDS)).isTrue();}}
            var saved=jdbc.queryForList("SELECT id FROM report_requests WHERE user_id=?",String.class,user);
            // then: commit 전 둘 다 없음, commit 후 응답 유실 재시도는 같은 요청.
            long outboxDelta=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class)-outboxBefore;
            if(point.equals("before")){assertThat(saved).isEmpty();assertThat(outboxDelta).isZero();assertThat(jdbc.queryForObject("SELECT used_count FROM report_admission WHERE scope_key='GLOBAL'",Integer.class)).isZero();}
            else {
                assertThat(saved).hasSize(1);assertThat(outboxDelta).isEqualTo(1);var replay=requests.accept(user,company,key,new CustomReportCommand(null,"commit 경계 시험"));
                assertThat(replay.requestId().toString()).isEqualTo(saved.getFirst());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregateid=? AND type='REQUEST_ACCEPTED'",Integer.class,saved.getFirst())).isEqualTo(1);
            }
            evidence.add(Map.of("boundary",point,"exitCode",child.exitValue(),"committedRequests",saved.size(),"requestIds",saved,"outboxDelta",outboxDelta));
        }
        Files.writeString(out.resolve(System.getenv().getOrDefault("PORTFOLIO_K_RUN_LABEL","manual")+"-accept-crash.json"),mapper.writeValueAsString(evidence),StandardOpenOption.CREATE_NEW);
    }
}
