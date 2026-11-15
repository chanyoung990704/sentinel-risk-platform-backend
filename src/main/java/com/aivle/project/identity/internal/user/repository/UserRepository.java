package com.aivle.project.identity.internal.user.repository;

import com.aivle.project.identity.api.AdminUserListItemDto;
import com.aivle.project.identity.api.RoleName;
import com.aivle.project.identity.internal.user.entity.UserEntity;
import com.aivle.project.identity.api.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 조회용 리포지토리.
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByIdAndDeletedAtIsNull(Long id);

	Optional<UserEntity> findByUuidAndDeletedAtIsNull(UUID uuid);

	@Query("""
		select new com.aivle.project.identity.api.AdminUserListItemDto(u.id, u.name, u.email)
		from UserEntity u
		where u.status = :status
				and u.deletedAt is null
		order by u.id asc
		""")
	List<AdminUserListItemDto> findListByStatusAndDeletedAtIsNullOrderByIdAsc(@Param("status") UserStatus status);

	@Query("""
		select new com.aivle.project.identity.api.AdminUserListItemDto(u.id, u.name, u.email)
		from UserEntity u
		where u.status = :status
			and u.deletedAt is null
			and not exists (
				select 1
				from UserRoleEntity ur
				join ur.role r
				where ur.user = u
					and r.name = :excludedRole
			)
		order by u.id asc
		""")
	List<AdminUserListItemDto> findListByStatusAndDeletedAtIsNullOrderByIdAscExcludingRole(
		@Param("status") UserStatus status,
		@Param("excludedRole") RoleName excludedRole
	);

	boolean existsByIdAndStatusAndDeletedAtIsNull(Long id, UserStatus status);

	@Query("select distinct ur.user.id from UserRoleEntity ur where ur.role.name = :role")
	List<Long> findUserIdsWithRole(@Param("role") RoleName role);

	@Query("select (count(u) > 0) from UserEntity u join UserRoleEntity ur on ur.user = u join ur.role r where u.id = :id and u.status = :status and u.deletedAt is null and r.deletedAt is null and r.name = :role")
	boolean existsActiveAdministrator(@Param("id") Long id, @Param("status") UserStatus status, @Param("role") RoleName role);
}
