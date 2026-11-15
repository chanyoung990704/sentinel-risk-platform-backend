package com.aivle.project.identity.api;

import java.util.UUID;

/** 다른 모듈에 전달하는 사용자 식별 정보. 인증 비밀과 JPA 상태는 포함하지 않는다. */
public record CurrentActor(Long id, UUID uuid, String name) implements ActorIdentity {
	public Long getId() { return id; }
	public UUID getUuid() { return uuid; }
	public String getName() { return name; }
}
