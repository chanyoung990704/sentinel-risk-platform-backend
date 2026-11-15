package com.aivle.project.community.internal.file.repository;

import com.aivle.project.community.internal.file.entity.PostFilesEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 게시글-파일 매핑 저장소.
 */
public interface PostFilesRepository extends JpaRepository<PostFilesEntity, Long> {

	@Query("select pf from PostFilesEntity pf where pf.post.id = :postId order by pf.createdAt asc")
	List<PostFilesEntity> findAllActiveByPostIdOrderByCreatedAtAsc(@Param("postId") Long postId);

	@Query("select pf from PostFilesEntity pf join fetch pf.post p where pf.fileId = :fileId and p.deletedAt is null")
	Optional<PostFilesEntity> findByFileId(@Param("fileId") Long fileId);
}
