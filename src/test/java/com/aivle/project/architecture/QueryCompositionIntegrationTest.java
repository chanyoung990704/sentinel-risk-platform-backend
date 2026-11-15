package com.aivle.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import com.aivle.project.app.internal.perf.QueryCountProbe;
import com.aivle.project.finance.api.*;
import com.aivle.project.support.PortfolioIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"spring.kafka.listener.auto-startup=false","decorator.datasource.p6spy.enable-logging=false"})
class QueryCompositionIntegrationTest extends PortfolioIntegrationSupport {
	@Autowired CompanyInformationBatches batches;
	@Autowired CompanyInformation singles;
	@Autowired FinancialReferenceQueries references;
	@Autowired CompanyDirectory directory;
	@Autowired QueryCountProbe probe;
	@Autowired JdbcTemplate jdbc;
	@Autowired ObjectMapper mapper;

	@Test void oneAndTwentyActualCompaniesStayBoundedAndMatchSingleReadValues() throws Exception {
		// given: 실제 활성 snapshot의20기업을 같은 순서로 사용한다.
		var ids=jdbc.queryForList("SELECT DISTINCT s.company_id FROM snapshot_report_versions s JOIN active_data_snapshots a ON a.snapshot_id=s.snapshot_id WHERE a.family='FINANCIAL' ORDER BY s.company_id LIMIT 20",Long.class);
		assertThat(ids).hasSize(20);
		var evidence=new LinkedHashMap<String,Object>();
		for(int size:List.of(1,20)) {
			var selected=ids.subList(0,size);
			// when
			probe.begin("company_list");List<CompanyInfoDto> actual;QueryCountProbe.Counts count;
			try { actual=batches.latest(selected); } finally { count=probe.end(); }
			// then: API 경계로 옮겨도 건별 SQL이 늘어나지 않고 업무 응답이 같다.
			assertThat(count.errors()).isZero();assertThat(count.statements()).isBetween(1L,2L);
			var companies=directory.findAllById(selected).stream().collect(java.util.stream.Collectors.toMap(CompanyIdentity::getId,c->c));
			var expected=selected.stream().map(id->singles.getCompanyInfo(id,
				references.findMaxActualQuarterKeyByStockCode(companies.get(id).getStockCode()).orElse(null))).toList();
			com.fasterxml.jackson.databind.JsonNode actualTree=mapper.valueToTree(actual);
			assertThat(actualTree).isEqualTo(mapper.<com.fasterxml.jackson.databind.JsonNode>valueToTree(expected));
			evidence.put("companies"+size,Map.of("sqlStatements",count.statements(),"rows",actual.size(),"parity",true));
		}
		var output=Path.of(System.getenv("PORTFOLIO_K_RUN_DIR"));Files.createDirectories(output);
		Files.writeString(output.resolve("query-composition.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),StandardOpenOption.CREATE_NEW);
	}
}
