package com.aivle.project.storage.api;

import com.aivle.project.storage.api.FileUsageType;
import java.time.LocalDateTime;

public record FileMetadataSnapshot(Long id, FileUsageType usageType, String storageUrl, String storageKey,
	String originalFilename, long fileSize, String contentType, LocalDateTime createdAt, boolean deleted) implements FileMetadata {
	public static FileMetadataSnapshot copyOf(FileMetadata source) {
		return source == null ? null : new FileMetadataSnapshot(source.getId(), source.getUsageType(), source.getStorageUrl(),
			source.getStorageKey(), source.getOriginalFilename(), source.getFileSize(), source.getContentType(), source.getCreatedAt(), source.isDeleted());
	}
	public Long getId() { return id; }
	public FileUsageType getUsageType() { return usageType; }
	public String getStorageUrl() { return storageUrl; }
	public String getStorageKey() { return storageKey; }
	public String getOriginalFilename() { return originalFilename; }
	public long getFileSize() { return fileSize; }
	public String getContentType() { return contentType; }
	public LocalDateTime getCreatedAt() { return createdAt; }
	public boolean isDeleted() { return deleted; }
}
