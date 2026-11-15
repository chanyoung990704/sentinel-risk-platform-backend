package com.aivle.project.identity.internal;

import com.aivle.project.identity.api.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** JPA 계정 조회와 같은 정책을 적용하는 관리 CLI용 읽기 어댑터다. */
public final class JdbcIdentityQueries implements IdentityQueries {
	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate named;
	public JdbcIdentityQueries(JdbcTemplate jdbc) { this.jdbc=jdbc;this.named=new NamedParameterJdbcTemplate(jdbc); }
	public Optional<CurrentActor> findByUuid(UUID uuid) {
		if(uuid==null)return Optional.empty();
		byte[] bytes=ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
		return jdbc.query("SELECT id,name FROM users WHERE uuid=? AND deleted_at IS NULL",
			(row,n)->new CurrentActor(row.getLong("id"),uuid,row.getString("name")),bytes).stream().findFirst();
	}
	public boolean isActive(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE' AND deleted_at IS NULL",Integer.class,userId)>0;
	}
	public boolean isActiveAdministrator(long userId) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.status='ACTIVE' AND u.deleted_at IS NULL AND r.deleted_at IS NULL AND r.name='ROLE_ADMIN'",Integer.class,userId)>0;
	}
	public Map<Long,String> names(Collection<Long> ids) {
		if(ids.isEmpty())return Map.of();
		var result=new HashMap<Long,String>();
		named.query("SELECT id,name FROM users WHERE id IN (:ids)",Map.of("ids",ids),
			(org.springframework.jdbc.core.RowCallbackHandler)row->result.put(row.getLong("id"),row.getString("name")));
		return Map.copyOf(result);
	}
	public Set<Long> userIdsWithRole(RoleName role) {
		return Set.copyOf(jdbc.queryForList("SELECT DISTINCT ur.user_id FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.name=?",Long.class,role.name()));
	}
}
