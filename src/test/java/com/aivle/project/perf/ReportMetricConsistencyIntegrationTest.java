package com.aivle.project.perf;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.CustomReportIntegrationSupport;
import com.aivle.project.company.reportrequest.model.CustomReportCommand;
import com.aivle.project.company.reportrequest.service.ReportRequestService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(properties={"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets","PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false","custom-report.admission.user-limit=200"})
class ReportMetricConsistencyIntegrationTest extends CustomReportIntegrationSupport {
    @Autowired ReportRequestService requests; @Autowired QueryCountProbe probe;
    @Test void acceptedHundredIncludesFailuresAndWaitingRatherThanHundredCompleted() {
        // given
        for(int i=0;i<100;i++) requests.accept(users.getFirst(),company,"metric-"+java.util.UUID.randomUUID(),new CustomReportCommand(null,"현금흐름 검토"));
        jdbc.update("UPDATE report_requests SET status='SUCCEEDED' WHERE user_id=? ORDER BY id LIMIT 60",users.getFirst());
        jdbc.update("UPDATE report_requests SET status='FAILED' WHERE user_id=? AND status='ACCEPTED' ORDER BY id LIMIT 20",users.getFirst());
        // when
        var counts=jdbc.queryForMap("SELECT COUNT(*) accepted,SUM(status='SUCCEEDED') succeeded,SUM(status='FAILED') failed,SUM(status NOT IN ('SUCCEEDED','FAILED')) remaining FROM report_requests WHERE user_id=?",users.getFirst());
        // then: 이 계산 fixture는 완료 파일의 정합성을 주장하지 않는다.
        assertThat(((Number)counts.get("accepted")).intValue()).isEqualTo(100);
        assertThat(((Number)counts.get("succeeded")).intValue()).isEqualTo(60);
        assertThat(((Number)counts.get("failed")).intValue()).isEqualTo(20);
        assertThat(((Number)counts.get("remaining")).intValue()).isEqualTo(20);
    }
    @Test void actualRequestThreadQueryIsCountedUnderBoundedReportLabels() throws Exception {
        // given
        var filter=new PortfolioMetrics(probe,new SimpleMeterRegistry());
        for(var target:java.util.List.of(new String[]{"POST","/api/companies/313/custom-report-requests"},new String[]{"GET","/api/report-requests/00000000-0000-4000-8000-000000000001"})) {
            var response=new MockHttpServletResponse();
            // when
            filter.doFilter(new MockHttpServletRequest(target[0],target[1]),response,(req,res)->jdbc.queryForObject("SELECT 1",Integer.class));
            // then
            assertThat(response.getHeader("X-Portfolio-Sql-Count")).isEqualTo("1");
            assertThat(response.getHeader("X-Portfolio-Sql-Errors")).isEqualTo("0");
        }
    }
}
