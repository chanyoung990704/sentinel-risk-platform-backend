package com.aivle.project.community.internal.file.entity;
import com.aivle.project.storage.api.FileMetadata;
import com.aivle.project.storage.api.FileMetadataSnapshot;

import com.aivle.project.community.internal.post.entity.PostsEntity;
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
 * post_files 테이블에 매핑되는 게시글-파일 매핑 엔티티.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_files")
public class PostFilesEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private PostsEntity post;

	@Column(name = "file_id", nullable = false)
	private Long fileId;

	@jakarta.persistence.Transient
	private FileMetadata file;

	public void resolveFile(FileMetadata metadata) { this.file = FileMetadataSnapshot.copyOf(metadata); }

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public static PostFilesEntity create(PostsEntity post, FileMetadata file) {
		PostFilesEntity mapping = new PostFilesEntity();
		mapping.post = post;
		mapping.fileId = file.getId();
		mapping.file = FileMetadataSnapshot.copyOf(file);
		return mapping;
	}

	@PrePersist
	private void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
