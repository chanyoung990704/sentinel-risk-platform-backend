package com.aivle.project.dataimport.entity;
import java.util.UUID;
/** JDBC에서 읽은 논리 데이터셋의 불변 메타데이터. */
public record DataSnapshotEntity(UUID id, String fingerprint, String sourceFingerprint,
	String checksum, String metadataChecksum, int chunkSize, long expectedCells, int totalChunks, String state) { }
