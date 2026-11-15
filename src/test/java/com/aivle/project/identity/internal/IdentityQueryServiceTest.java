package com.aivle.project.identity.internal;

import com.aivle.project.identity.api.*;
import com.aivle.project.identity.internal.user.entity.UserEntity;
import com.aivle.project.identity.internal.user.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityQueryServiceTest {
	@Test void actorIsAnImmutableSnapshotWithoutCredentials() {
		// given
		var users = mock(UserRepository.class);
		var uuid = UUID.randomUUID();
		var user = UserEntity.create("fixture@example.invalid", "secret-test-hash", "이전 이름", null, UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", 7L);
		ReflectionTestUtils.setField(user, "uuid", uuid);
		when(users.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.of(user));
		// when
		var actor = new IdentityQueryService(users).findByUuid(uuid).orElseThrow();
		ReflectionTestUtils.setField(user, "name", "변경 이름");
		// then
		assertThat(actor).isEqualTo(new CurrentActor(7L, uuid, "이전 이름"));
		assertThat(CurrentActor.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
			.containsExactly("id", "uuid", "name");
	}

	@Test void missingOrDeletedAccountDoesNotResolve() {
		// given
		var users = mock(UserRepository.class);
		var uuid = UUID.randomUUID();
		when(users.findByUuidAndDeletedAtIsNull(uuid)).thenReturn(Optional.empty());
		// when / then
		assertThat(new IdentityQueryService(users).findByUuid(uuid)).isEmpty();
	}

	@Test void batchNamesAreImmutableAndPreserveMissingUsers() {
		// given
		var users = mock(UserRepository.class);
		var user = UserEntity.create("fixture@example.invalid", "unused", "작성자", null, UserStatus.ACTIVE);
		ReflectionTestUtils.setField(user, "id", 7L);
		when(users.findAllById(List.of(7L, 8L))).thenReturn(List.of(user));
		// when
		var names = new IdentityQueryService(users).names(List.of(7L, 8L));
		// then
		assertThat(names).containsExactlyEntriesOf(Map.of(7L, "작성자"));
		assertThatThrownBy(() -> names.put(8L, "추가")).isInstanceOf(UnsupportedOperationException.class);
	}
}
