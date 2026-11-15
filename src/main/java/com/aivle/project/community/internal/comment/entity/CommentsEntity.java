package com.aivle.project.community.internal.comment.entity;

import com.aivle.project.foundation.api.entity.BaseEntity;
import com.aivle.project.community.internal.post.entity.PostsEntity;
import com.aivle.project.identity.api.ActorIdentity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * comments 테이블에 매핑되는 댓글 엔티티 (계층형 - Adjacency List Model).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments")
public class CommentsEntity extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private PostsEntity post;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@jakarta.persistence.Transient
	private String authorName;

	public ActorIdentity getUser() { return new com.aivle.project.identity.api.CurrentActor(userId, null, authorName); }
	public void resolveAuthor(String name) { this.authorName = name; }

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private CommentsEntity parent;

	@Lob
	@Column(name = "content", nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "depth", nullable = false)
	private int depth = 0;

	@Column(name = "sequence", nullable = false)
	private int sequence = 0;

	@OneToMany(mappedBy = "parent")
	private List<CommentsEntity> replies = new ArrayList<>();

	public static CommentsEntity create(
		PostsEntity post,
		ActorIdentity user,
		CommentsEntity parent,
		String content,
		int depth,
		int sequence
	) {
		CommentsEntity comment = new CommentsEntity();
		comment.post = post;
		comment.userId = user.getId();
		comment.authorName = user.getName();
		comment.parent = parent;
		comment.content = content;
		comment.depth = depth;
		comment.sequence = sequence;
		return comment;
	}

	public void update(String content) {
		this.content = content;
	}

	public void markDeleted() {
		delete();
	}
}
