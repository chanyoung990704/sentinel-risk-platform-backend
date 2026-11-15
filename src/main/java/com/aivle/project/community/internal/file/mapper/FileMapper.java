package com.aivle.project.community.internal.file.mapper;

import com.aivle.project.community.api.FileResponse;
import com.aivle.project.storage.api.FileMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileMapper {

	// 게시글 id는 엔티티가 아니라 호출 시 전달되는 값으로 채운다.
	@Mapping(target = "id", source = "entity.id")
	@Mapping(target = "postId", source = "postId")
	@Mapping(target = "storageUrl", source = "entity.storageUrl")
	@Mapping(target = "originalFilename", source = "entity.originalFilename")
	@Mapping(target = "fileSize", source = "entity.fileSize")
	@Mapping(target = "contentType", source = "entity.contentType")
	@Mapping(target = "createdAt", source = "entity.createdAt")
	FileResponse toResponse(Long postId, FileMetadata entity);
}
