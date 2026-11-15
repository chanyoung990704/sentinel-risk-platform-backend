package com.aivle.project.identity.api;

import org.springframework.jdbc.core.JdbcTemplate;

/** 키 발급·웹 서버를 기동하지 않는 관리 CLI의 읽기 전용 조립이다. */
public final class IdentityReaders {
	private IdentityReaders() {}
	public static IdentityQueries jdbc(JdbcTemplate jdbc) {
		return new com.aivle.project.identity.internal.JdbcIdentityQueries(jdbc);
	}
}
