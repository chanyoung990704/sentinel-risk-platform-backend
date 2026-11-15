package com.aivle.project.community.internal.post.repository;

import com.aivle.project.community.internal.post.entity.PostsEntity;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시글 조회/저장 리포지토리.
 */
public interface PostsRepository extends JpaRepository<PostsEntity, Long> {

	Optional<PostsEntity> findByIdAndDeletedAtIsNull(Long id);

	List<PostsEntity> findAllByDeletedAtIsNullOrderByCreatedAtDesc();

	List<PostsEntity> findAllByCategoryNameAndDeletedAtIsNullOrderByCreatedAtDesc(String categoryName);

	List<PostsEntity> findAllByCategoryNameAndUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String categoryName, Long userId);

	Page<PostsEntity> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

	Page<PostsEntity> findAllByCategoryNameAndDeletedAtIsNullOrderByCreatedAtDesc(String categoryName, Pageable pageable);

	Page<PostsEntity> findAllByCategoryNameAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String categoryName, com.aivle.project.community.api.PostStatus status, Pageable pageable);

	Page<PostsEntity> findAllByCategoryNameAndUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(String categoryName, Long userId, com.aivle.project.community.api.PostStatus status, Pageable pageable);

	Optional<PostsEntity> findByIdAndCategoryNameAndDeletedAtIsNull(Long id, String categoryName);
}
