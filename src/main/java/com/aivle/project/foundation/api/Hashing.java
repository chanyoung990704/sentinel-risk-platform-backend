package com.aivle.project.foundation.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 업무의 소유권과 무관한 바이트 지문 계산. */
public final class Hashing {
	private Hashing() { }
	public static String sha256(byte[] bytes) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
	}
}
