package com.aivle.project.identity.api;

import java.util.UUID;

/** 업무가 사용하는 최소 사용자 정보. 웹 경계에서는 CurrentActor 값으로 전달한다. */
public interface ActorIdentity {
	Long getId();
	UUID getUuid();
	String getName();
}
