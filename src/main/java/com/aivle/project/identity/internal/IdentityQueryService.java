package com.aivle.project.identity.internal;

import com.aivle.project.identity.api.*;
import com.aivle.project.identity.internal.user.repository.UserRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class IdentityQueryService implements IdentityQueries {
	private final UserRepository users;
	@Override public Optional<CurrentActor> findByUuid(UUID uuid) {
		return users.findByUuidAndDeletedAtIsNull(uuid).map(user -> new CurrentActor(user.getId(), user.getUuid(), user.getName()));
	}
	@Override public boolean isActive(long userId) {
		return users.existsByIdAndStatusAndDeletedAtIsNull(userId, UserStatus.ACTIVE);
	}
	@Override public boolean isActiveAdministrator(long userId) {
		return users.existsActiveAdministrator(userId, UserStatus.ACTIVE, RoleName.ROLE_ADMIN);
	}
	@Override public Map<Long, String> names(Collection<Long> ids) {
		if (ids.isEmpty()) return Map.of();
		return users.findAllById(ids).stream().collect(Collectors.toUnmodifiableMap(user -> user.getId(), user -> user.getName()));
	}
	@Override public Set<Long> userIdsWithRole(RoleName role) {
		return Set.copyOf(users.findUserIdsWithRole(role));
	}
}
