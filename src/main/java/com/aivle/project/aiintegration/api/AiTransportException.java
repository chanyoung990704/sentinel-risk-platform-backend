package com.aivle.project.aiintegration.api;

public class AiTransportException extends RuntimeException {
	private final String code;
	private final boolean retryable;
	public AiTransportException(String code, boolean retryable) { super(code); this.code = code; this.retryable = retryable; }
	public String code() { return code; }
	public boolean retryable() { return retryable; }
}
