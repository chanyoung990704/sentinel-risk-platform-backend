package com.aivle.project.community.internal.post.service;

import com.aivle.project.community.internal.category.entity.CategoriesEntity;
import com.aivle.project.community.internal.category.repository.CategoriesRepository;
import com.aivle.project.community.internal.comment.repository.CommentsRepository;
import com.aivle.project.foundation.api.dto.PageRequest;
import com.aivle.project.foundation.api.dto.PageResponse;
import com.aivle.project.foundation.api.error.CommonErrorCode;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.community.internal.file.repository.PostFilesRepository;
import com.aivle.project.community.internal.file.mapper.FileMapper;
import com.aivle.project.community.api.PostAdminCreateRequest;
import com.aivle.project.community.api.PostAdminUpdateRequest;
import com.aivle.project.community.api.PostDetailResponse;
import com.aivle.project.community.api.PostFileResponse;
import com.aivle.project.community.api.PostCreateRequest;
import com.aivle.project.community.internal.post.dto.PostResponse;
import com.aivle.project.community.api.PostUpdateRequest;
import com.aivle.project.community.api.PostUserCreateRequest;
import com.aivle.project.community.api.PostUserUpdateRequest;
import com.aivle.project.community.api.PostStatus;
import com.aivle.project.community.internal.post.entity.PostsEntity;
import com.aivle.project.community.internal.post.entity.PostViewCountsEntity;
import com.aivle.project.community.internal.post.repository.PostViewCountsRepository;
import com.aivle.project.community.internal.post.repository.PostsRepository;
import com.aivle.project.identity.api.RoleName;
import com.aivle.project.identity.api.ActorIdentity;
import com.aivle.project.identity.api.IdentityQueries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 및 관리자 게시글 CRUD 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

	private final PostsRepository postsRepository;
	private final PostViewCountsRepository postViewCountsRepository;
	private final CategoriesRepository categoriesRepository;
	private final IdentityQueries identityQueries;
	private final CommentsRepository commentsRepository;
	private final com.aivle.project.community.internal.post.mapper.PostMapper postMapper;
	private final PostFilesRepository postFilesRepository;
	private final FileMapper fileMapper;
	private final com.aivle.project.storage.api.FileCatalog fileCatalog;

	private static final String BOARD_NOTICES = "notices";
	private static final String BOARD_QNA = "qna";

	/**
	 * [사용자] 보드별 게시글 목록 조회.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PostResponse> list(String categoryName, PageRequest pageRequest, ActorIdentity user) {
		CategoriesEntity category = findCategoryByName(categoryName);
		
		Page<PostsEntity> page;
		if (BOARD_QNA.equalsIgnoreCase(categoryName)) {
			// QnA 보드는 본인 글만 조회 가능 (비로그인 접근 불가)
			if (user == null) {
				throw new CommonException(CommonErrorCode.COMMON_403);
			}
			page = postsRepository.findAllByCategoryNameAndUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
				categoryName, user.getId(), PostStatus.PUBLISHED, pageRequest.toPageable()
			);
		} else {
			page = postsRepository.findAllByCategoryNameAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
				categoryName, PostStatus.PUBLISHED, pageRequest.toPageable()
			);
		}

		resolveAuthors(page.getContent());
		Map<Long, String> qnaStatusByPostId = resolveQnaStatusByPosts(page.getContent());
		return PageResponse.of(
			page.map(post -> postMapper.toResponseWithQnaStatus(post, qnaStatusByPostId.get(post.getId())))
		);
	}

	/**
	 * [사용자] 게시글 상세 조회.
	 */
	@Transactional(readOnly = true)
	public PostDetailResponse get(String categoryName, Long postId, ActorIdentity user) {
		PostsEntity post = findPostInBoard(postId, categoryName);
		
		if (BOARD_QNA.equalsIgnoreCase(categoryName)) {
			if (user == null) {
				throw new CommonException(CommonErrorCode.COMMON_403);
			}
			validateOwner(post, user.getId());
		}

		PostResponse response = toResponseWithQnaStatus(post);
		boolean downloadable = user != null && user.getId() != null;
		List<PostFileResponse> files = com.aivle.project.community.internal.file.service.AttachmentMetadata.active(
			postFilesRepository.findAllActiveByPostIdOrderByCreatedAtAsc(postId), fileCatalog).stream()
			.map(mapping -> fileMapper.toResponse(postId, mapping.getFile()))
			.map(file -> new PostFileResponse(
				file.id(),
				file.originalFilename(),
				file.fileSize(),
				file.contentType(),
				file.createdAt(),
				downloadable,
				downloadable ? buildDownloadUrl(file.id()) : null
			))
			.toList();

		return new PostDetailResponse(
			response.id(),
			response.name(),
			response.categoryId(),
			response.title(),
			response.content(),
			response.viewCount(),
			response.isPinned(),
			response.status(),
			response.qnaStatus(),
			response.createdAt(),
			response.updatedAt(),
			files
		);
	}

	/**
	 * [사용자] 게시글 상세 조회(카테고리 경로 없이 ID 기준).
	 */
	@Transactional(readOnly = true)
	public PostDetailResponse getById(Long postId, ActorIdentity user) {
		PostsEntity post = findPost(postId);
		String categoryName = post.getCategory().getName();
		if (BOARD_QNA.equalsIgnoreCase(categoryName)) {
			if (user == null) {
				throw new CommonException(CommonErrorCode.COMMON_403);
			}
			validateOwner(post, user.getId());
		}

		PostResponse response = toResponseWithQnaStatus(post);
		boolean downloadable = user != null && user.getId() != null;
		List<PostFileResponse> files = com.aivle.project.community.internal.file.service.AttachmentMetadata.active(
			postFilesRepository.findAllActiveByPostIdOrderByCreatedAtAsc(postId), fileCatalog).stream()
			.map(mapping -> fileMapper.toResponse(postId, mapping.getFile()))
			.map(file -> new PostFileResponse(
				file.id(),
				file.originalFilename(),
				file.fileSize(),
				file.contentType(),
				file.createdAt(),
				downloadable,
				downloadable ? buildDownloadUrl(file.id()) : null
			))
			.toList();

		return new PostDetailResponse(
			response.id(),
			response.name(),
			response.categoryId(),
			response.title(),
			response.content(),
			response.viewCount(),
			response.isPinned(),
			response.status(),
			response.qnaStatus(),
			response.createdAt(),
			response.updatedAt(),
			files
		);
	}

	/**
	 * [사용자] 게시글 생성.
	 */
	public PostResponse create(String categoryName, ActorIdentity user, PostUserCreateRequest request) {
		validateUserWriteAccess(categoryName);
		CategoriesEntity category = findCategoryByName(categoryName);

		PostsEntity post = PostsEntity.create(
			user,
			category,
			request.getTitle().trim(),
			request.getContent().trim(),
			false,
			PostStatus.PUBLISHED
		);

		PostsEntity saved = postsRepository.save(post);
		postViewCountsRepository.save(PostViewCountsEntity.create(saved));
		return toResponseWithQnaStatus(saved);
	}

	/**
	 * [사용자] 게시글 수정.
	 */
	public PostResponse update(String categoryName, ActorIdentity user, Long postId, PostUserUpdateRequest request) {
		PostsEntity post = findPostInBoard(postId, categoryName);
		validateOwner(post, requireUserId(user));

		String nextTitle = request.getTitle() != null ? request.getTitle().trim() : post.getTitle();
		String nextContent = request.getContent() != null ? request.getContent().trim() : post.getContent();

		post.update(nextTitle, nextContent, post.getCategory(), null, null);
		return toResponseWithQnaStatus(post);
	}

	/**
	 * [사용자] 게시글 수정(카테고리 경로 없이 ID 기준).
	 */
	public PostResponse updateById(ActorIdentity user, Long postId, PostUserUpdateRequest request) {
		PostsEntity post = findPost(postId);
		validateOwner(post, requireUserId(user));

		String nextTitle = request.getTitle() != null ? request.getTitle().trim() : post.getTitle();
		String nextContent = request.getContent() != null ? request.getContent().trim() : post.getContent();

		post.update(nextTitle, nextContent, post.getCategory(), null, null);
		return toResponseWithQnaStatus(post);
	}

	/**
	 * [사용자] 게시글 삭제.
	 */
	public void delete(String categoryName, ActorIdentity user, Long postId) {
		PostsEntity post = findPostInBoard(postId, categoryName);
		validateOwner(post, requireUserId(user));
		post.markDeleted();
	}

	/**
	 * [사용자] 게시글 삭제(카테고리 경로 없이 ID 기준).
	 */
	public void deleteById(ActorIdentity user, Long postId) {
		PostsEntity post = findPost(postId);
		validateOwner(post, requireUserId(user));
		post.markDeleted();
	}

	// ============================================
	// Admin Operations
	// ============================================

	/**
	 * [관리자] 보드별 전체 게시글 목록 조회.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PostResponse> listAdmin(String categoryName, PageRequest pageRequest) {
		Page<PostsEntity> page = postsRepository.findAllByCategoryNameAndDeletedAtIsNullOrderByCreatedAtDesc(
			categoryName, pageRequest.toPageable()
		);
		resolveAuthors(page.getContent());
		Map<Long, String> qnaStatusByPostId = resolveQnaStatusByPosts(page.getContent());
		return PageResponse.of(
			page.map(post -> postMapper.toResponseWithQnaStatus(post, qnaStatusByPostId.get(post.getId())))
		);
	}

	/**
	 * [관리자] 게시글 상세 조회.
	 */
	@Transactional(readOnly = true)
	public PostResponse getAdmin(String categoryName, Long postId) {
		PostsEntity post = findPostInBoard(postId, categoryName);
		return toResponseWithQnaStatus(post);
	}

	/**
	 * [관리자] 게시글 생성.
	 */
	public PostResponse createAdmin(String categoryName, ActorIdentity admin, PostAdminCreateRequest request) {
		CategoriesEntity category = findCategoryByName(categoryName);

		PostsEntity post = PostsEntity.create(
			admin,
			category,
			request.getTitle().trim(),
			request.getContent().trim(),
			request.isPinned(),
			request.getStatus()
		);

		PostsEntity saved = postsRepository.save(post);
		postViewCountsRepository.save(PostViewCountsEntity.create(saved));
		return toResponseWithQnaStatus(saved);
	}

	/**
	 * [관리자] 게시글 수정.
	 */
	public PostResponse updateAdmin(String categoryName, Long postId, PostAdminUpdateRequest request) {
		PostsEntity post = findPostInBoard(postId, categoryName);

		String nextTitle = request.getTitle() != null ? request.getTitle().trim() : post.getTitle();
		String nextContent = request.getContent() != null ? request.getContent().trim() : post.getContent();

		post.update(nextTitle, nextContent, post.getCategory(), request.getIsPinned(), request.getStatus());
		return toResponseWithQnaStatus(post);
	}

	/**
	 * [관리자] 게시글 삭제.
	 */
	public void deleteAdmin(String categoryName, Long postId) {
		PostsEntity post = findPostInBoard(postId, categoryName);
		post.markDeleted();
	}

	// ============================================
	// Helpers
	// ============================================

	private CategoriesEntity findCategoryByName(String name) {
		return categoriesRepository.findByNameAndDeletedAtIsNull(name)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private PostsEntity findPostInBoard(Long postId, String categoryName) {
		return postsRepository.findByIdAndCategoryNameAndDeletedAtIsNull(postId, categoryName)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private PostsEntity findPost(Long postId) {
		return postsRepository.findByIdAndDeletedAtIsNull(postId)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private void validateUserWriteAccess(String categoryName) {
		if (BOARD_NOTICES.equalsIgnoreCase(categoryName)) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
	}

	private void validateOwner(PostsEntity post, Long userId) {
		if (!post.getUser().getId().equals(userId)) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
	}

	private String buildDownloadUrl(Long fileId) {
		return "/api/files/" + fileId;
	}

	private Long requireUserId(ActorIdentity user) {
		if (user == null || user.getId() == null) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
		return user.getId();
	}

	private PostResponse toResponseWithQnaStatus(PostsEntity post) {
		resolveAuthors(List.of(post));
		if (!isQnaCategory(post) || post.getId() == null) {
			return postMapper.toResponse(post);
		}
		boolean answered = commentsRepository.countByPostIdAndRole(post.getId(), identityQueries.userIdsWithRole(RoleName.ROLE_ADMIN)) > 0;
		String qnaStatus = answered ? "answered" : "pending";
		return postMapper.toResponseWithQnaStatus(post, qnaStatus);
	}

	private Map<Long, String> resolveQnaStatusByPosts(List<PostsEntity> posts) {
		List<Long> qnaPostIds = posts.stream()
			.filter(this::isQnaCategory)
			.map(PostsEntity::getId)
			.filter(Objects::nonNull)
			.toList();

		if (qnaPostIds.isEmpty()) {
			return Map.of();
		}

		Set<Long> answeredPostIds = commentsRepository.findAnsweredPostIdsByPostIdsAndRole(qnaPostIds, identityQueries.userIdsWithRole(RoleName.ROLE_ADMIN));
		Map<Long, String> qnaStatusByPostId = new HashMap<>();
		for (Long qnaPostId : qnaPostIds) {
			qnaStatusByPostId.put(qnaPostId, answeredPostIds.contains(qnaPostId) ? "answered" : "pending");
		}
		return qnaStatusByPostId;
	}

	private boolean isQnaCategory(PostsEntity post) {
		return post.getCategory() != null && "qna".equalsIgnoreCase(post.getCategory().getName());
	}

	private ActorIdentity getCurrentUserOrThrow() {
		org.springframework.security.core.Authentication auth = 
			org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
		
		Object principal = auth.getPrincipal();
		if (!(principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}

		java.util.UUID userUuid = java.util.UUID.fromString(jwt.getSubject());
		return identityQueries.findByUuid(userUuid)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private void resolveAuthors(List<PostsEntity> posts) {
		var names = identityQueries.names(posts.stream().map(PostsEntity::getUserId).filter(Objects::nonNull).distinct().toList());
		posts.forEach(post -> post.resolveAuthor(names.get(post.getUserId())));
	}
}
