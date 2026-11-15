package com.aivle.project.identity.api;
import com.aivle.project.identity.api.AuthErrorCode;

/**
 * 인증 관련 런타임 예외.
 */
public class AuthException extends RuntimeException {

	private final AuthErrorCode errorCode;

	public AuthException(AuthErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public AuthErrorCode getErrorCode() {
		return errorCode;
	}
}
