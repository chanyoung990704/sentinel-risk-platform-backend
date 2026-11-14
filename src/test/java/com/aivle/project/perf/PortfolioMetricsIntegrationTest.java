package com.aivle.project.perf;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.support.PortfolioIntegrationSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles({"local","portfolio-perf"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets",
	"PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false"})
class PortfolioMetricsIntegrationTest extends PortfolioIntegrationSupport {
	@Autowired QueryCountProbe probe;
	@Autowired JdbcTemplate jdbc;
	@Test void actualMysqlCallbacksCountTwoQueriesAndNoOutsideQuery() {
		// given: 준비 SQL은 범위 밖이다.
		assertThat(jdbc.queryForObject("SELECT @@version",String.class)).startsWith("8.0.");
		probe.begin("company_overview");
		try {
			// when
			assertThat(jdbc.queryForObject("SELECT 1",Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT 2",Integer.class)).isEqualTo(2);
		} finally {
			// then: 실제 datasource callback 연결을 검증한다.
			var counts=probe.end();assertThat(counts.statements()).isEqualTo(2);assertThat(counts.elapsedNanos()).isPositive();assertThat(counts.errors()).isZero();
		}
	}
	@Test void actualMysqlErrorsCountAndNextScopeIsEmpty() {
		// given
		probe.begin("company_search");
		try {
			// when
			assertThatThrownBy(()->jdbc.execute("SELECT * FROM m01_missing_table_fixture")).isInstanceOf(org.springframework.dao.DataAccessException.class);
		} finally {
			// then
			var counts=probe.end();assertThat(counts.statements()).isEqualTo(1);assertThat(counts.errors()).isEqualTo(1);
		}
		probe.begin("company_search");assertThat(probe.end().statements()).isZero();
	}
}
