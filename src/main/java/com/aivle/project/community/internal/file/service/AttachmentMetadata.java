package com.aivle.project.community.internal.file.service;

import com.aivle.project.community.internal.file.entity.PostFilesEntity;
import com.aivle.project.storage.api.FileCatalog;
import java.util.List;

/** 첨부 관계와 저장 메타를 일괄 결합하고 삭제 파일은 노출하지 않는다. */
public final class AttachmentMetadata {
	private AttachmentMetadata() { }
	public static List<PostFilesEntity> active(List<PostFilesEntity> mappings, FileCatalog files) {
		if (mappings.isEmpty()) return List.of();
		var metadata = files.findAllByIds(mappings.stream().map(PostFilesEntity::getFileId).distinct().toList());
		mappings.forEach(mapping -> mapping.resolveFile(metadata.get(mapping.getFileId())));
		return mappings.stream().filter(mapping -> mapping.getFile() != null && !mapping.getFile().isDeleted()).toList();
	}
}
