package com.aivle.project.finance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

/** 웹 앱을 기동하지 않는 로컬 관리 명령에 동일한 원천 조회 구현을 제공한다. */
public final class FinancialSourceReaders {
	private FinancialSourceReaders() {}
	public static FinancialSources jdbc(JdbcTemplate jdbc,ObjectMapper mapper) {
		return new com.aivle.project.finance.internal.FinancialSourceService(jdbc,
			new com.aivle.project.finance.internal.dataimport.repository.SnapshotReportRepository(jdbc,mapper));
	}
}
