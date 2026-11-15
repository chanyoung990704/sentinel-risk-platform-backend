package com.aivle.project.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 계정 저장소를 노출하지 않는 조회 경계. */
public interface IdentityQueries {
	Optional<CurrentActor> findByUuid(UUID uuid);
	boolean isActive(long userId);
	boolean isActiveAdministrator(long userId);
	Map<Long, String> names(Collection<Long> userIds);
	Set<Long> userIdsWithRole(RoleName role);
}
