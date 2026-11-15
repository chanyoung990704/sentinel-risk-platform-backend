package com.aivle.project.storage.api;

import com.aivle.project.storage.api.FileUsageType;
import java.time.LocalDateTime;

/** 저장 객체를 참조하는 읽기 계약. 외부로 반환할 때는 불변 사본을 사용한다. */
public interface FileMetadata {
	Long getId();
	FileUsageType getUsageType();
	String getStorageUrl();
	String getStorageKey();
	String getOriginalFilename();
	long getFileSize();
	String getContentType();
	LocalDateTime getCreatedAt();
	boolean isDeleted();

	static FileMetadata create(FileUsageType usage, String url, String key, String name, long size, String type) {
		return new FileMetadataSnapshot(null, usage, url, key, name, size, type, null, false);
	}
}
