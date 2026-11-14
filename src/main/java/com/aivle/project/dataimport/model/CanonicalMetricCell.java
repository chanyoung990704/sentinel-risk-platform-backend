package com.aivle.project.dataimport.model;

import java.math.BigDecimal;
import java.util.List;

/** NULL도 보존하며, 중복 값의 모든 출처를 함께 유지한다. */
public record CanonicalMetricCell(Key key, BigDecimal value, List<Provenance> provenance) {

	public CanonicalMetricCell {
		provenance = List.copyOf(provenance);
	}

	public record Key(String stockCode, int quarterKey, String metricCode, String valueType)
		implements Comparable<Key> {
		@Override
		public int compareTo(Key other) {
			int c = stockCode.compareTo(other.stockCode);
			if (c == 0) c = Integer.compare(quarterKey, other.quarterKey);
			if (c == 0) c = metricCode.compareTo(other.metricCode);
			if (c == 0) c = valueType.compareTo(other.valueType);
			return c;
		}
	}

	public record Provenance(String source, String fileHash, String sheet, int row, int column) { }
}
