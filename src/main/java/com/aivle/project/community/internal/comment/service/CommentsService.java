package com.aivle.project.community.internal.comment.service;

import com.aivle.project.community.api.CommentCreateRequest;
import com.aivle.project.community.internal.comment.dto.CommentResponse;
import com.aivle.project.community.api.CommentUpdateRequest;
import com.aivle.project.community.internal.comment.entity.CommentsEntity;
import com.aivle.project.community.internal.comment.repository.CommentsRepository;
import com.aivle.project.foundation.api.error.CommonErrorCode;
import com.aivle.project.foundation.api.error.CommonException;
import com.aivle.project.community.internal.post.entity.PostsEntity;
import com.aivle.project.community.internal.post.repository.PostsRepository;
import com.aivle.project.community.internal.post.service.PostReadAccessPolicy;
import com.aivle.project.identity.api.ActorIdentity;
import com.aivle.project.identity.api.IdentityQueries;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 CRUD 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentsService {
	private final IdentityQueries identityQueries;

	private final CommentsRepository commentsRepository;
	private final PostsRepository postsRepository;
	private final PostReadAccessPolicy postReadAccessPolicy;
	private final com.aivle.project.community.internal.comment.mapper.CommentMapper commentMapper;
	private final com.aivle.project.community.internal.post.mapper.PostMapper postMapper;

	@Transactional(readOnly = true)
	public List<CommentResponse> listByPost(Long postId, ActorIdentity user) {
		PostsEntity post = findPost(postId);
		postReadAccessPolicy.validateReadable(post, user);

		var comments = commentsRepository.findByPostIdAndDeletedAtIsNullOrderByDepthAscSequenceAsc(postId);
		var names = identityQueries.names(comments.stream().map(CommentsEntity::getUserId).distinct().toList());
		comments.forEach(comment -> comment.resolveAuthor(names.get(comment.getUserId())));
		return comments.stream()
			.map(commentMapper::toResponse)
			.toList();
	}

	public CommentResponse create(ActorIdentity user, CommentCreateRequest request) {
		Long userId = requireUserId(user);
		PostsEntity post = findPost(request.getPostId());

		CommentsEntity parent = null;
		int depth = 0;
		int sequence;

		if (request.getParentId() != null) {
			parent = findComment(request.getParentId());
			if (!parent.getPost().getId().equals(post.getId())) {
				throw new CommonException(CommonErrorCode.COMMON_400); // 부모 댓글이 같은 게시글이 아님
			}
			depth = parent.getDepth() + 1;
			sequence = commentsRepository.findMaxSequenceByParentId(parent.getId()) + 1;
		} else {
			sequence = commentsRepository.findMaxSequenceByPostIdAndParentIsNull(post.getId()) + 1;
		}

		CommentsEntity comment = CommentsEntity.create(
			post,
			user,
			parent,
			request.getContent().trim(),
			depth,
			sequence
		);

		CommentsEntity saved = commentsRepository.save(comment);
		return commentMapper.toResponse(saved);
	}

	/**
	 * [관리자] QnA 게시글에 대한 답변 작성.
	 */
	public com.aivle.project.community.api.QaReplyResponse createAdminReply(ActorIdentity admin, Long postId, com.aivle.project.community.api.QaReplyInput input) {
		PostsEntity post = findPost(postId);
		
		// QnA 보드인지 확인 (선택적이지만 안전을 위해)
		if (!"qna".equalsIgnoreCase(post.getCategory().getName())) {
			throw new CommonException(CommonErrorCode.COMMON_400);
		}

		// 관리자 답변은 항상 최상위 댓글(depth 0)로 작성되거나, 비즈니스 요구에 따라 조정 가능.
		// 여기서는 일반적인 '댓글' 형태로 저장하되 depth 0으로 생성.
		int sequence = commentsRepository.findMaxSequenceByPostIdAndParentIsNull(post.getId()) + 1;

		CommentsEntity comment = CommentsEntity.create(
			post,
			admin,
			null, // 부모 없음
			input.getContent().trim(),
			0,    // depth 0
			sequence
		);

		CommentsEntity saved = commentsRepository.save(comment);
		return postMapper.toQaReplyResponse(saved);
	}

	public CommentResponse update(Long userId, Long commentId, CommentUpdateRequest request) {
		validateUserId(userId);
		CommentsEntity comment = findComment(commentId);
		validateOwner(comment, userId);

		comment.update(request.getContent().trim());
		// 재조회한 댓글에는 이름이 없으므로 응답 직전에 소유 모듈에서 조회한다.
		comment.resolveAuthor(identityQueries.names(List.of(comment.getUserId())).get(comment.getUserId()));
		return commentMapper.toResponse(comment);
	}

	public void delete(Long userId, Long commentId) {
		validateUserId(userId);
		CommentsEntity comment = findComment(commentId);
		validateOwner(comment, userId);
		comment.markDeleted();
	}

	private CommentsEntity findComment(Long commentId) {
		return commentsRepository.findById(commentId)
			.filter(c -> !c.isDeleted())
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private PostsEntity findPost(Long postId) {
		return postsRepository.findByIdAndDeletedAtIsNull(postId)
			.orElseThrow(() -> new CommonException(CommonErrorCode.COMMON_404));
	}

	private void validateOwner(CommentsEntity comment, Long userId) {
		if (!comment.getUser().getId().equals(userId)) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CommonException(CommonErrorCode.COMMON_400);
		}
	}

	private Long requireUserId(ActorIdentity user) {
		if (user == null || user.getId() == null) {
			throw new CommonException(CommonErrorCode.COMMON_403);
		}
		return user.getId();
	}
}
