package com.aivle.project.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aivle.project.finance.api.FinancialSources;
import com.aivle.project.finance.api.SourceRef;
import com.aivle.project.finance.internal.FinancialSourceService;
import com.aivle.project.finance.internal.dataimport.repository.SnapshotReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 제한 계정은 별도 소유 실행 설정의 실험이며 운영 DataSource 분리를 뜻하지 않는다. */
@Tag("realinfra")
class ModuleLeastPrivilegeIntegrationTest {
	@Test
	void financePublicSourceWorksWithOnlyFinanceSelectAndRejectsForeignReadAndWrite() throws Exception {
		// given: 시험 환경에서 준비한 읽기 전용 계정. 비밀번호는 비공개 파일에서만 읽는다.
		var root = Path.of(System.getenv("PORTFOLIO_DATA_DIR")).toRealPath();
		assertThat(root.getFileName().toString()).isIn("operations", "restore", "modulith");
		var limited = new DriverManagerDataSource(url(), "sentinel_module_finance",
			Files.readString(root.resolve("module-accounts/finance-password")).strip());
		var jdbc = new JdbcTemplate(limited);
		FinancialSources source = new FinancialSourceService(jdbc, new SnapshotReportRepository(jdbc, new ObjectMapper()));
		var active = source.active().orElseThrow();
		long company = jdbc.queryForObject("SELECT MIN(company_id) FROM snapshot_report_versions WHERE snapshot_id=?", Long.class, active.id().toString());
		// when: 실제 공개 계약을 통해 소유 모듈의 SQL만 실행한다.
		var snapshot = source.read(new SourceRef(active.id(), company, active.baseQuarter()));
		// then
		assertThat(snapshot.schemaVersion()).isEqualTo(1);
		assertThat(snapshot.cells()).isNotEmpty();
		assertThat(source.hasValues(active.id(), company, active.baseQuarter())).isTrue();
		assertDenied(() -> jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class));
		assertDenied(() -> jdbc.update("UPDATE report_requests SET state_version=state_version WHERE 1=0"));
		assertDenied(() -> jdbc.update("UPDATE companies SET corp_name=corp_name WHERE 1=0"));
	}

	private void assertDenied(Runnable operation) {
		assertThatThrownBy(operation::run).isInstanceOf(DataAccessException.class)
			.satisfies(error -> assertThat(((SQLException) error.getCause()).getErrorCode()).isEqualTo(1142));
	}
	private String url() {
		return "jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
	}
}
