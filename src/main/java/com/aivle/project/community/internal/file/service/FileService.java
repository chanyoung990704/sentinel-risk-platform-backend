package com.aivle.project.community.internal.file.service;

import com.aivle.project.foundation.api.error.CommonErrorCode;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.community.api.FileResponse;
import com.aivle.project.storage.api.FileUsageType;
import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.community.internal.file.entity.PostFilesEntity;
import com.aivle.project.storage.api.FileErrorCode;
import com.aivle.project.storage.api.FileException;
import com.aivle.project.storage.api.FileCatalog;
import com.aivle.project.community.internal.file.repository.PostFilesRepository;
import com.aivle.project.storage.api.FileStorageService;
import com.aivle.project.storage.api.StoredFile;
import com.aivle.project.storage.api.UploadValidation;
import com.aivle.project.community.internal.post.entity.PostsEntity;
import com.aivle.project.community.internal.post.repository.PostsRepository;
import com.aivle.project.community.internal.post.service.PostReadAccessPolicy;
import com.aivle.project.identity.api.ActorIdentity;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드 처리 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FileService {

	private final FileStorageService fileStorageService;
	private final UploadValidation fileValidator;
	private final FileCatalog filesRepository;
	private final PostFilesRepository postFilesRepository;
	private final PostsRepository postsRepository;
	private final PostReadAccessPolicy postReadAccessPolicy;
	private final com.aivle.project.community.internal.file.mapper.FileMapper fileMapper;

	public List<FileResponse> upload(Long postId, ActorIdentity user, List<MultipartFile> files) {
		PostsEntity post = findPost(postId);
		Long userId = requireUserId(user);
		validateOwner(post, userId);
		fileValidator.validateMultiple(files);

		String keyPrefix = "posts/" + postId;
		List<FileResponse> responses = new ArrayList<>();

		for (MultipartFile file : files) {
			StoredFile stored = fileStorageService.store(file, keyPrefix);
			FileMetadata entity = FileMetadata.create(
				FileUsageType.POST_ATTACHMENT,
				stored.storageUrl(),
				stored.storageKey(),
				stored.originalFilename(),
				stored.fileSize(),
				stored.contentType()
			);
			FileMetadata saved = filesRepository.save(entity);
			postFilesRepository.save(PostFilesEntity.create(post, saved));
			responses.add(fileMapper.toResponse(post.getId(), saved));
		}

		return responses;
	}

	@Transactional(readOnly = true)
	public List<FileResponse> list(Long postId, ActorIdentity user) {
		PostsEntity post = findPost(postId);
		requireUserId(user);
		postReadAccessPolicy.validateReadable(post, user);
		return AttachmentMetadata.active(postFilesRepository.findAllActiveByPostIdOrderByCreatedAtAsc(postId), filesRepository).stream()
			.map(mapping -> fileMapper.toResponse(postId, mapping.getFile()))
			.toList();
	}

	@Transactional(readOnly = true)
	public FileMetadata getFile(Long fileId, ActorIdentity user) {
		PostFilesEntity mapping = postFilesRepository.findByFileId(fileId)
			.orElseThrow(() -> new FileException(FileErrorCode.FILE_404_NOT_FOUND));
		FileMetadata file = filesRepository.findById(mapping.getFileId())
			.orElseThrow(() -> new FileException(FileErrorCode.FILE_404_NOT_FOUND));
		if (file.isDeleted()) {
			throw new FileException(FileErrorCode.FILE_404_NOT_FOUND);
		}
		requireUserId(user);
		postReadAccessPolicy.validateReadable(mapping.getPost(), user);
		return file;
	}

	private PostsEntity findPost(Long postId) {
		return postsRepository.findByIdAndDeletedAtIsNull(postId)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private void validateOwner(PostsEntity post, Long userId) {
		if (!post.getUser().getId().equals(userId)) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
	}

	private Long requireUserId(ActorIdentity user) {
		if (user == null || user.getId() == null) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
		return user.getId();
	}
}
