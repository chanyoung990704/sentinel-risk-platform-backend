package com.aivle.project.identity.api;
import reactor.core.publisher.Mono;
/** 도메인의 공개 업무 계약. 저장소 구현은 모듈 내부에 둔다. */
public interface TurnstileVerification {
	Mono<Boolean> verifyToken(String token, String remoteIp);
	boolean verifyTokenSync(String token, String remoteIp);
}
