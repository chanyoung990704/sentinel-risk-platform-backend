package com.aivle.project.community.internal.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aivle.project.community.api.CommentUpdateRequest;
import com.aivle.project.community.internal.comment.entity.CommentsEntity;
import com.aivle.project.community.internal.comment.mapper.CommentMapper;
import com.aivle.project.community.internal.comment.repository.CommentsRepository;
import com.aivle.project.community.internal.post.mapper.PostMapper;
import com.aivle.project.community.internal.post.repository.PostsRepository;
import com.aivle.project.community.internal.post.service.PostReadAccessPolicy;
import com.aivle.project.identity.api.CurrentActor;
import com.aivle.project.identity.api.IdentityQueries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CommentAuthorContractTest {
	@Test
	void updateReloadedCommentRetainsMaskedAuthor() {
		// given: 재조회 시 작성자 이름은 엔티티에 저장되지 않는다.
		var identities = mock(IdentityQueries.class);
		var repository = mock(CommentsRepository.class);
		var comment = CommentsEntity.create(null, new CurrentActor(7L, null, null), null, "이전 내용", 0, 0);
		when(repository.findById(3L)).thenReturn(Optional.of(comment));
		when(identities.names(List.of(7L))).thenReturn(Map.of(7L, "홍길동"));
		var service = new CommentsService(identities, repository, mock(PostsRepository.class),
			mock(PostReadAccessPolicy.class), Mappers.getMapper(CommentMapper.class), mock(PostMapper.class));
		var request = new CommentUpdateRequest();
		request.setContent("변경 내용");
		// when
		var response = service.update(7L, 3L, request);
		// then
		assertThat(response.name()).isEqualTo("홍*동");
		assertThat(response.content()).isEqualTo("변경 내용");
	}
}
