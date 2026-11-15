package com.aivle.project.storage.internal;

import com.aivle.project.storage.api.*;
import com.aivle.project.storage.internal.file.entity.FilesEntity;
import com.aivle.project.storage.internal.file.repository.FilesRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FileCatalogService implements FileCatalog {
	private final FilesRepository files;
	@Override @Transactional public FileMetadata save(FileMetadata specification) {
		if (specification.getId() != null) throw new IllegalArgumentException("새 파일 사양에는 ID를 지정할 수 없습니다.");
		var entity = FilesEntity.create(specification.getUsageType(), specification.getStorageUrl(), specification.getStorageKey(),
			specification.getOriginalFilename(), specification.getFileSize(), specification.getContentType());
		return FileMetadataSnapshot.copyOf(files.save(entity));
	}
	@Override @Transactional(readOnly = true) public Optional<FileMetadata> findById(Long id) {
		return files.findById(id).map(file -> (FileMetadata) FileMetadataSnapshot.copyOf(file));
	}
	@Override @Transactional(readOnly = true) public Map<Long, FileMetadata> findAllByIds(Collection<Long> ids) {
		if (ids.isEmpty()) return Map.of();
		return files.findAllById(ids).stream().collect(Collectors.toUnmodifiableMap(FilesEntity::getId, FileMetadataSnapshot::copyOf));
	}
}
