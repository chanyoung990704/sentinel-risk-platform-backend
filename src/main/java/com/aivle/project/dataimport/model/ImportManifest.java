package com.aivle.project.dataimport.model;

import java.util.List;
import java.util.Map;

/** 같은 파일과 같은 정책은 같은 데이터셋 지문을 만든다. */
public record ImportManifest(String fingerprint, int baseQuarterKey, String parserVersion,
	String decimalPolicyVersion, String canonicalChecksum, List<SourceManifest> sources,
	Map<String, Long> counts) {
	public record SourceManifest(String role, String fileName, String sha256, long bytes,
		String encoding, String sheet, int rows) { }
}
