package com.aivle.project.storage.internal;

import com.aivle.project.storage.api.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** 스토리지 소유 SQL만 실행하는 관리 CLI용 읽기 어댑터다. */
public final class JdbcFileCatalogReader implements FileCatalog {
	private final JdbcTemplate jdbc;
	public JdbcFileCatalogReader(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public FileMetadata save(FileMetadata specification) { throw new UnsupportedOperationException("읽기 전용 파일 카탈로그입니다"); }
	public Optional<FileMetadata> findById(Long id) {
		return jdbc.query("SELECT * FROM files WHERE id=?", this::map, id).stream().findFirst();
	}
	public Map<Long, FileMetadata> findAllByIds(Collection<Long> ids) {
		if (ids.isEmpty()) return Map.of();
		var rows = jdbc.query("SELECT * FROM files WHERE id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
			this::map, ids.toArray());
		var result = new LinkedHashMap<Long, FileMetadata>();
		rows.forEach(row -> result.put(row.getId(), row));
		return Map.copyOf(result);
	}
	private FileMetadata map(ResultSet row, int index) throws SQLException {
		var created = row.getTimestamp("created_at");
		return new FileMetadataSnapshot(row.getLong("id"), FileUsageType.valueOf(row.getString("usage_type")),
			row.getString("storage_url"), row.getString("storage_key"), row.getString("original_filename"),
			row.getLong("file_size"), row.getString("content_type"), created == null ? null : created.toLocalDateTime(),
			row.getTimestamp("deleted_at") != null);
	}
}
