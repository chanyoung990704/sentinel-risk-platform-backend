package com.aivle.project.community.internal.post.repository;

import com.aivle.project.community.internal.post.entity.PostViewCountsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시글 조회수 저장소.
 */
public interface PostViewCountsRepository extends JpaRepository<PostViewCountsEntity, Long> {
}
