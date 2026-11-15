package com.aivle.project.support;

import com.aivle.project.identity.api.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** JPA 없이 기동하는 강제 종료 시험의 계정 포트. 실제 서비스 경로의 인증 시험은 별도로 수행한다. */
public class CrashIdentityQueries implements IdentityQueries {
	private final JdbcTemplate jdbc;
	public CrashIdentityQueries(JdbcTemplate jdbc) { this.jdbc=jdbc; }
	public boolean isActive(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE' AND deleted_at IS NULL",Integer.class,userId)==1;
	}
	public boolean isActiveAdministrator(long userId) { throw new UnsupportedOperationException("이 프로세스는 접수 경계만 검증한다"); }
	public Optional<CurrentActor> findByUuid(UUID uuid) { throw new UnsupportedOperationException(); }
	public Map<Long,String> names(Collection<Long> ids) { throw new UnsupportedOperationException(); }
	public Set<Long> userIdsWithRole(RoleName role) { throw new UnsupportedOperationException(); }
}
