package com.aivle.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.project.finance.api.FinancialSources;
import com.aivle.project.finance.api.SourceRef;
import com.aivle.project.identity.api.IdentityQueries;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.api.ReportingCommands;
import com.aivle.project.reporting.api.ReportingQueries;
import com.aivle.project.support.CustomReportIntegrationSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.p6spy.engine.common.PreparedStatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** JDBC의 실제 SQL과 가장 가까운 업무 호출 프레임을 대조한다. 바인딩 값은 읽지 않는다. */
@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"spring.kafka.listener.auto-startup=false", "decorator.datasource.p6spy.enable-logging=false"})
@Import(ModuleSqlOwnershipIntegrationTest.CaptureConfig.class)
class ModuleSqlOwnershipIntegrationTest extends CustomReportIntegrationSupport {
	@Autowired FinancialSources finance;
	@Autowired IdentityQueries identities;
	@Autowired ReportingCommands commands;
	@Autowired ReportingQueries queries;
	@Autowired OwnedSql capture;

	@Test void sourceIdentityAndAcceptanceStayWithinTheirOwnSqlTables() {
		// given: 준비·정리 SQL은 캡처하지 않는다.
		capture.enabled.set(true);
		try {
			// when: 한 공개 API 안에서 다른 공개 API를 호출해도 실제 SQL 소유자를 따라간다.
			var source = finance.active().orElseThrow();
			assertThat(finance.read(new SourceRef(source.id(), company, source.baseQuarter())).cells()).isNotEmpty();
			assertThat(identities.isActive(users.getFirst())).isTrue();
			var request = commands.accept(users.getFirst(), company, "module-sql-" + UUID.randomUUID(),
				new CustomReportCommand(null, "SQL 경계 검증"));
			assertThat(queries.getOwned(users.getFirst(), request.requestId())).isNotNull();
		} finally { capture.enabled.remove(); }
		// then: 소유권을 판정할 수 없는 실행도 성공으로 넘기지 않는다.
		assertThat(capture.observed).contains("finance", "identity", "reporting");
		assertThat(capture.violations).isEmpty();
		assertThat(capture.statements).isPositive();
	}

	@TestConfiguration(proxyBeanMethods = false) static class CaptureConfig {
		@Bean OwnedSql ownedSql() throws Exception { return new OwnedSql(); }
	}
	static class OwnedSql extends JdbcEventListener {
		private static final Pattern TABLE = Pattern.compile("\\b(?:from|join|update|into)\\s+`?([a-z_]+)", Pattern.CASE_INSENSITIVE);
		final ThreadLocal<Boolean> enabled = new ThreadLocal<>();
		final Set<String> observed = new TreeSet<>();
		final List<String> violations = new ArrayList<>();
		final Map<String,String> owners = new TreeMap<>();
		int statements;
		OwnedSql() throws Exception {
			try (var resource = getClass().getResourceAsStream("/architecture/table-ownership.json")) {
				new ObjectMapper().readTree(resource).get("tables").properties().forEach(entry -> owners.put(entry.getKey(), entry.getValue().asText()));
			}
		}
		private void record(PreparedStatementInformation statement, SQLException error) {
			if (!Boolean.TRUE.equals(enabled.get()) || error != null) return;
			statements++;
			String owner = StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName)
				.filter(name -> name.startsWith("com.aivle.project.") && name.contains(".internal."))
				.map(name -> name.split("\\.")[3]).findFirst().orElse("unknown"));
			var matcher = TABLE.matcher(statement.getSql());
			boolean matched = false;
			while (matcher.find()) {
				String table = matcher.group(1).toLowerCase(Locale.ROOT);
				if (!owners.containsKey(table)) continue;
				matched = true;
				if (!owners.get(table).equals(owner)) violations.add(owner + "->" + table);
			}
			if (!matched) violations.add(owner + ":소유 테이블 미판정");
			observed.add(owner);
		}
		@Override public void onAfterExecute(PreparedStatementInformation s,long n,SQLException e) { record(s,e); }
		@Override public void onAfterExecuteQuery(PreparedStatementInformation s,long n,SQLException e) { record(s,e); }
		@Override public void onAfterExecuteUpdate(PreparedStatementInformation s,long n,int rows,SQLException e) { record(s,e); }
	}
}
