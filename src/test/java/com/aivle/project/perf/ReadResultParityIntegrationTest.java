package com.aivle.project.perf;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.finance.internal.company.service.CompanyOverviewService;
import com.aivle.project.support.PortfolioIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 실제 선택 기업의 모든 업무 응답과 공개 시험 SQL을 전후 대조한다. 인증 SQL은 수집하지 않는다. */
@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets",
	"PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false"})
@Import(ReadResultParityIntegrationTest.CaptureConfiguration.class)
class ReadResultParityIntegrationTest extends PortfolioIntegrationSupport {
	@Autowired CompanyOverviewService overview;
	@Autowired ObjectMapper mapper;
	@Autowired JdbcTemplate jdbc;
	@Autowired PublicReadSql capture;
	@Test void actualResponsesMatchRecordedBaselineAndQueriesStayReadOnly() throws Exception {
		// given: 실제 활성 snapshot만 선택한다. fixture snapshot은 건드리지 않는다.
		String snapshot=jdbc.queryForObject("SELECT snapshot_id FROM active_data_snapshots WHERE family='FINANCIAL'",String.class);
		assertThat(snapshot).isEqualTo("2ba59132-4743-446b-a307-f81e9dd563f2");
		var ids=jdbc.queryForList("SELECT company_id FROM snapshot_report_versions WHERE snapshot_id=? GROUP BY company_id ORDER BY company_id",Long.class,snapshot);
		assertThat(ids).hasSize(2083);
		Map<String,Object> responses=new TreeMap<>();
		capture.enabled.set(true);
		try {
			// when
			for(int i=0;i<20;i++) {
				long id=ids.get((int)Math.round(i*(ids.size()-1)/19.0));
				var response=overview.getOverview(id,"20253");
				assertThat(response.getForecast().getMetricSeries()).isNotEmpty();
				responses.put(Long.toString(id),mapper.valueToTree(response));
			}
		} finally {capture.enabled.remove();}
		// then: JSON의 업무 필드를 제외하지 않고 전부 비교한다.
		assertThat(capture.sql).isNotEmpty().allMatch(sql->sql.toLowerCase(Locale.ROOT).startsWith("select "));
		String mode=System.getenv().getOrDefault("PORTFOLIO_PARITY_MODE","self");
		Path root=Path.of(System.getenv().getOrDefault("PORTFOLIO_DATA_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio").toString())).resolve("parity");
		if(mode.equals("before")) {
			Files.createDirectories(root);
			Files.writeString(root.resolve("before.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(responses),StandardOpenOption.CREATE_NEW);
			Files.writeString(root.resolve("before-sql.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(capture.sql),StandardOpenOption.CREATE_NEW);
		} else if(mode.equals("after")) {
			assertThat(root.resolve("before.json")).exists();
			// 저장된 JSON과 동일하게 직렬화 후 파싱한다. LongNode/IntNode 구현 차이는 HTTP 응답 차이가 아니다.
			assertThat(wireTree(responses)).isEqualTo(mapper.readTree(root.resolve("before.json").toFile()));
			Files.writeString(root.resolve("after-sql.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(capture.sql),StandardOpenOption.CREATE_NEW);
		} else {
			for(var entry:responses.entrySet())assertThat(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(overview.getOverview(Long.valueOf(entry.getKey()),"20253"))).isEqualTo(entry.getValue());
		}
	}
	@Test void serializedNumericEqualityStillRejectsBusinessChanges() throws Exception {
		// given: HTTP JSON은 같아도 메모리 숫자 노드 구현은 다를 수 있다.
		var memory=mapper.createObjectNode().put("id",1089L).put("amount",1.25);
		var stored=mapper.readTree("{\"id\":1089,\"amount\":1.25}");
		assertThat(memory).isNotEqualTo(stored);
		// when / then
		assertThat(wireTree(memory)).isEqualTo(stored);
		memory.put("amount",1.26);
		assertThat(wireTree(memory)).isNotEqualTo(stored);
	}
	private com.fasterxml.jackson.databind.JsonNode wireTree(Object value) throws Exception {
		return mapper.readTree(mapper.writeValueAsBytes(value));
	}

	@TestConfiguration(proxyBeanMethods=false) static class CaptureConfiguration {
		@Bean PublicReadSql publicReadSql(){return new PublicReadSql();}
	}
	static class PublicReadSql extends JdbcEventListener {
		final ThreadLocal<Boolean> enabled=new ThreadLocal<>();
		final List<String> sql=new ArrayList<>();
		@Override public void onAfterExecuteQuery(PreparedStatementInformation statement,long nanos,SQLException error) {
			if(Boolean.TRUE.equals(enabled.get()) && error==null && sql.size()<1000)sql.add(statement.getSqlWithValues());
		}
	}
}
