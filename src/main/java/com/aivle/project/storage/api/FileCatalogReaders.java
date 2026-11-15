package com.aivle.project.storage.api;

import org.springframework.jdbc.core.JdbcTemplate;

/** 웹 앱을 기동하지 않는 읽기 전용 관리 CLI의 조립 지점이다. */
public final class FileCatalogReaders {
	private FileCatalogReaders() {}
	public static FileCatalog jdbc(JdbcTemplate jdbc) {
		return new com.aivle.project.storage.internal.JdbcFileCatalogReader(jdbc);
	}
}
