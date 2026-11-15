package com.aivle.project.community.internal.post.entity;

import com.aivle.project.identity.api.ActorIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * post_likes 테이블에 매핑되는 게시글 좋아요 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_likes")
public class PostLikesEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private PostsEntity post;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	public ActorIdentity getUser() { return new com.aivle.project.identity.api.CurrentActor(userId, null, null); }

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public PostLikesEntity(PostsEntity post, ActorIdentity user) {
		this.post = post;
		this.userId = user.getId();
	}

	@PrePersist
	private void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
