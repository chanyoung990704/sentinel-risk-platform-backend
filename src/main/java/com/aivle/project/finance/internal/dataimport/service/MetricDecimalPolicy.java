package com.aivle.project.finance.internal.dataimport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 저장 정밀도와 퍼센트 포인트 해석 정책. */
public final class MetricDecimalPolicy {
	public static final String VERSION = "decimal20-4-half-up-v1";
	public BigDecimal normalize(String raw, boolean excelPercent) {
		if (raw == null || raw.isBlank() || raw.trim().equalsIgnoreCase("null") || raw.trim().equals("-")) return null;
		String value = raw.trim();
		boolean explicitPercent = value.endsWith("%");
		if (explicitPercent) value = value.substring(0, value.length() - 1).trim();
		if (!value.matches("[+-]?(?:(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
			throw new IllegalArgumentException("유효한 유한 소수가 아님");
		}
		try {
			BigDecimal number = new BigDecimal(value.replace(",", ""));
			if (excelPercent && !explicitPercent) number = number.movePointRight(2);
			// 반올림으로 정수 자릿수가 증가하는 경우도 범위를 검사한다.
			number = number.setScale(4, RoundingMode.HALF_UP);
			if (number.precision() > 20) throw new IllegalArgumentException("DECIMAL(20,4) 범위 초과");
			return number;
		} catch (ArithmeticException | NumberFormatException ex) {
			throw new IllegalArgumentException("소수 변환 또는 범위 오류", ex);
		}
	}
}
