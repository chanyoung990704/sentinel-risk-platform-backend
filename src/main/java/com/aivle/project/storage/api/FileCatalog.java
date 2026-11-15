package com.aivle.project.storage.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** 범용 파일 메타의 소유 경계. 도메인별 열람 권한은 호출자가 검증한다. */
public interface FileCatalog {
	FileMetadata save(FileMetadata specification);
	Optional<FileMetadata> findById(Long id);
	Map<Long, FileMetadata> findAllByIds(Collection<Long> ids);
}
