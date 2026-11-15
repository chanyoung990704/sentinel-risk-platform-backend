package com.aivle.project.identity;

import static org.assertj.core.api.Assertions.assertThat;
import com.aivle.project.common.config.EnableJpaReadAdapters;
import com.aivle.project.identity.api.*;
import com.aivle.project.identity.internal.user.entity.UserEntity;
import com.aivle.project.identity.internal.user.entity.UserRoleEntity;
import com.aivle.project.identity.internal.user.repository.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 웹 JPA와 관리 CLI의 읽기 정책이 서로 달라지지 않도록 대조한다. */
@DataJpaTest @EnableJpaReadAdapters @ActiveProfiles("test")
class IdentityReaderParityTest {
	@Autowired IdentityQueries jpa;
	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@Autowired UserRoleRepository assignments;
	@Autowired EntityManager entityManager;

	@Test void uuidNamesAndRolesMatchJpaForActiveInactiveAndDeletedUsers() {
		// given
		var user=users.save(UserEntity.create(UUID.randomUUID()+"@sentinel.invalid","fixture","관리자",null,UserStatus.ACTIVE));
		var admin=roles.findByName(RoleName.ROLE_ADMIN).orElseThrow();
		assignments.save(new UserRoleEntity(user,admin));entityManager.flush();entityManager.clear();
		var reader=IdentityReaders.jdbc(jdbc);
		// when / then
		assertParity(reader,user.getId(),user.getUuid());
		assertThat(reader.isActiveAdministrator(user.getId())).isTrue();
		jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?",user.getId());entityManager.clear();
		assertParity(reader,user.getId(),user.getUuid());
		assertThat(reader.isActive(user.getId())).isFalse();
		assertThat(reader.isActiveAdministrator(user.getId())).isFalse();
		jdbc.update("UPDATE users SET status='ACTIVE',deleted_at=CURRENT_TIMESTAMP WHERE id=?",user.getId());entityManager.clear();
		assertParity(reader,user.getId(),user.getUuid());
		assertThat(reader.findByUuid(user.getUuid())).isEmpty();
	}

	@Test void deletedRoleAndMissingAccountRemainIneligibleInBothAdapters() {
		// given
		var user=users.save(UserEntity.create(UUID.randomUUID()+"@sentinel.invalid","fixture","관리자",null,UserStatus.ACTIVE));
		var admin=roles.findByName(RoleName.ROLE_ADMIN).orElseThrow();
		assignments.save(new UserRoleEntity(user,admin));entityManager.flush();entityManager.clear();
		var reader=IdentityReaders.jdbc(jdbc);
		// when
		jdbc.update("UPDATE roles SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",admin.getId());entityManager.clear();
		// then
		assertParity(reader,user.getId(),user.getUuid());
		assertThat(reader.isActiveAdministrator(user.getId())).isFalse();
		assertThat(reader.isActive(Long.MAX_VALUE)).isFalse();
		assertThat(reader.isActiveAdministrator(Long.MAX_VALUE)).isFalse();
		assertThat(reader.names(List.of())).isEmpty();
		assertThat(reader.findByUuid(UUID.randomUUID())).isEmpty();
	}
	private void assertParity(IdentityQueries reader,long id,UUID uuid) {
		assertThat(reader.findByUuid(uuid)).isEqualTo(jpa.findByUuid(uuid));
		assertThat(reader.isActive(id)).isEqualTo(jpa.isActive(id));
		assertThat(reader.isActiveAdministrator(id)).isEqualTo(jpa.isActiveAdministrator(id));
		assertThat(reader.names(List.of(id))).isEqualTo(jpa.names(List.of(id)));
		assertThat(reader.userIdsWithRole(RoleName.ROLE_ADMIN)).isEqualTo(jpa.userIdsWithRole(RoleName.ROLE_ADMIN));
	}
}
