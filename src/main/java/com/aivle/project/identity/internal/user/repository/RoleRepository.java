package com.aivle.project.identity.internal.user.repository;

import com.aivle.project.identity.internal.user.entity.RoleEntity;
import com.aivle.project.identity.api.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 역할 조회용 리포지토리.
 */
public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

	Optional<RoleEntity> findByName(RoleName name);
}
