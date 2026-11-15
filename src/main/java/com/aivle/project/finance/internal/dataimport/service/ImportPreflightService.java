package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.api.ImportDatasetCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.api.CanonicalMetricCell.*;
import com.aivle.project.finance.api.ImportDatasetCommand.*;
import com.aivle.project.finance.internal.dataimport.model.ImportManifest.SourceManifest;
import com.aivle.project.finance.internal.dataimport.model.ImportPreflightResult.*;
import com.aivle.project.finance.internal.dataimport.service.ImportSourceReader.*;
import com.aivle.project.finance.internal.report.importer.MetricHeaderMapping;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** DB 쓰기 없이 원본의 구조와 canonical 값을 검증한다. */
public class ImportPreflightService {
	public static final String PARSER_VERSION = "preflight-v1";
	private static final List<String> NAMES = List.of("ROA", "ROE", "매출액영업이익률", "부채비율",
		"자기자본비율", "자본잠식률", "단기차입금비율", "유동비율", "당좌비율", "유동부채비율",
		"CFO_자산비율", "CFO_매출액비율", "CFO증감률");
	private static final List<String> REL = List.of("ROA", "부채비율", "단기차입금비율", "유동비율", "CFO_자산비율");
	private record Loaded(Source source, String hash, Table table) {
		Provenance at(int row, int column) { return new Provenance(source.role().name(), hash, table.sheet(), row, column + 1); }
	}
	private record Header(int column, String metricCode, int quarter) { }

	public ImportPreflightResult preflight(ImportDatasetCommand command) throws IOException {
		offsetQuarter(command.baseQuarterKey(), 0);
		Map<Role, Loaded> loaded = new EnumMap<>(Role.class);
		List<SourceManifest> sources = new ArrayList<>();
		for (Source source : command.sources().stream().sorted(Comparator.comparing(s -> s.role().name())).toList()) {
			if (loaded.containsKey(source.role())) throw new IllegalArgumentException("중복 source role: " + source.role());
			byte[] bytes = Files.readAllBytes(source.path());
			String hash = sha256(bytes);
			Table table = new ImportSourceReader().read(source, bytes);
			loaded.put(source.role(), new Loaded(source, hash, table));
			sources.add(new SourceManifest(source.role().name(), source.path().getFileName().toString(), hash,
				bytes.length, table.encoding(), table.sheet(), Math.max(0, table.rows().size() - 1)));
		}
		for (Role role : List.of(Role.COMPANY, Role.INDUSTRY, Role.METRIC13, Role.METRIC5)) {
			if (!loaded.containsKey(role)) throw new IllegalArgumentException("필수 source 없음: " + role);
		}
		List<Issue> issues = new ArrayList<>();
		for (Loaded source : loaded.values()) if (rows(source).isEmpty()) {
			issue(issues, "ERROR", "EMPTY_SOURCE", source.at(1, 0), "", "", "데이터 행 없음");
		}
		Map<String, Company> companies = companies(loaded.get(Role.COMPANY), issues);
		Map<String, Industry> industries = industries(loaded.get(Role.INDUSTRY), companies, issues);
		Map<Key, CanonicalMetricCell> primary13 = metrics(loaded.get(Role.METRIC13), false, command.baseQuarterKey(), companies, industries, issues);
		Map<Key, CanonicalMetricCell> primary5 = metrics(loaded.get(Role.METRIC5), true, command.baseQuarterKey(), companies, industries, issues);
		List<Comparison> comparisons = new ArrayList<>();
		for (Role role : List.of(Role.REFERENCE13, Role.REFERENCE5)) if (loaded.containsKey(role)) {
			Map<Key, CanonicalMetricCell> reference = metrics(loaded.get(role), role == Role.REFERENCE5, command.baseQuarterKey(), companies, industries, issues);
			Map<Key, CanonicalMetricCell> primary = role == Role.REFERENCE5 ? primary5 : primary13;
			long missing = primary.keySet().stream().filter(k -> !reference.containsKey(k)).count();
			long unexpected = reference.keySet().stream().filter(k -> !primary.containsKey(k)).count();
			long different = primary.entrySet().stream().filter(e -> reference.containsKey(e.getKey()) && !Objects.equals(e.getValue().value(), reference.get(e.getKey()).value())).count();
			comparisons.add(new Comparison(role.name(), primary.size(), reference.size(), missing, unexpected, different, checksum(primary.values()), checksum(reference.values())));
			if (missing + unexpected + different > 0) issue(issues, "ERROR", "REFERENCE_MISMATCH", loaded.get(role).at(1, 0), "", "", "missing=" + missing + ",unexpected=" + unexpected + ",different=" + different);
		}
		TreeMap<Key, CanonicalMetricCell> all = new TreeMap<>(primary13); all.putAll(primary5);
		Map<String, Long> coverage = new TreeMap<>();
		all.keySet().forEach(k -> coverage.merge(k.stockCode(), 1L, Long::sum));
		coverage.forEach((stock, count) -> {
			if (count != 72) issue(issues, "ERROR", "INCOMPLETE_METRIC_SET", all.values().stream().filter(c -> c.key().stockCode().equals(stock)).findFirst().orElseThrow().provenance().getFirst(), stock, count.toString(), "13+5 지표/4분기 72개 위치 필요");
		});
		long nulls = all.values().stream().filter(c -> c.value() == null).count();
		Map<String, Long> counts = new TreeMap<>();
		counts.put("companies", (long) companies.size()); counts.put("industries", (long) industries.size());
		counts.put("canonicalCells", (long) all.size()); counts.put("nullCells", nulls); counts.put("numericCells", all.size() - nulls);
		counts.put("zeroCells", all.values().stream().filter(c -> c.value() != null && c.value().signum() == 0).count());
		counts.put("metricCompanies", all.keySet().stream().map(Key::stockCode).distinct().count());
		counts.put("errors", issues.stream().filter(i -> i.severity().equals("ERROR")).count());
		counts.put("warnings", issues.stream().filter(i -> i.severity().equals("WARNING")).count());
		StringBuilder identity = new StringBuilder(command.baseQuarterKey() + "|" + PARSER_VERSION + "|" + MetricDecimalPolicy.VERSION + "\n");
		for (SourceManifest s : sources) identity.append(s.role()).append('|').append(s.sha256()).append('|').append(s.encoding()).append('\n');
		ImportManifest manifest = new ImportManifest(sha256(identity.toString().getBytes(StandardCharsets.UTF_8)), command.baseQuarterKey(), PARSER_VERSION,
			MetricDecimalPolicy.VERSION, checksum(all.values()), List.copyOf(sources), Map.copyOf(counts));
		return new ImportPreflightResult(manifest, new ArrayList<>(all.values()), new ArrayList<>(companies.values()), new ArrayList<>(industries.values()), issues, comparisons);
	}
	public static String normalizeStockCode(String raw, boolean numeric) {
		if (raw == null || raw.isBlank()) throw new IllegalArgumentException("종목코드 없음");
		String value = raw.trim();
		if (numeric) try { value = new BigDecimal(value).toBigIntegerExact().toString(); }
		catch (ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("정수가 아닌 종목코드", ex); }
		if (value.matches("[0-9]{1,6}")) value = "0".repeat(6 - value.length()) + value;
		if (!value.matches("[0-9A-Z]{6}")) throw new IllegalArgumentException("종목코드 형식 오류");
		return value;
	}
	public static int offsetQuarter(int base, int offset) {
		int year = base / 10, quarter = base % 10;
		if (year < 1900 || year > 9999 || quarter < 1 || quarter > 4) throw new IllegalArgumentException("분기 형식 오류");
		long index = (long) year * 4 + quarter - 1 + offset;
		if (index < 1900L * 4 || index >= 10000L * 4) throw new IllegalArgumentException("분기 범위 초과");
		return (int) (index / 4 * 10 + index % 4 + 1);
	}

	private Map<String, Integer> headers(Loaded source, Set<String> expected, List<Issue> issues) {
		Map<String, Integer> headers = new LinkedHashMap<>();
		if (!source.table().rows().isEmpty()) {
			DataRow row = source.table().rows().getFirst();
			for (int c = 0; c < row.values().size(); c++) {
				Value v = row.at(c);
				if (v.error() != null) issue(issues, "ERROR", v.error(), source.at(row.number(), c), "", v.raw(), "헤더 셀 오류");
				if (headers.putIfAbsent(v.text(), c) != null) issue(issues, "ERROR", "DUPLICATE_HEADER", source.at(1, c), "", v.raw(), "중복 헤더");
				if (!expected.contains(v.text())) issue(issues, "ERROR", "UNKNOWN_HEADER", source.at(1, c), "", v.raw(), "예상하지 않은 열");
			}
		}
		for (String name : new TreeSet<>(expected)) if (!headers.containsKey(name)) issue(issues, "ERROR", "MISSING_HEADER", source.at(1, 0), "", name, "필수 헤더 없음");
		return headers;
	}
	private List<DataRow> rows(Loaded source) { return source.table().rows().stream().skip(1).filter(r -> !r.blank()).toList(); }
	private boolean validMetadata(Loaded source, DataRow row, Collection<Integer> columns, List<Issue> issues) {
		if (source.table().sheet().equals("CSV") && row.values().size() != source.table().rows().getFirst().values().size()) {
			issue(issues, "ERROR", "CSV_COLUMN_COUNT", source.at(row.number(), 0), "", "", "CSV 열 수 불일치"); return false;
		}
		boolean valid = true;
		for (int column : columns) {
			Value value = row.at(column);
			if (value.error() != null) {
				issue(issues, "ERROR", value.error(), source.at(row.number(), column), "", value.raw(), "검증되지 않은 메타데이터 셀"); valid = false;
			}
		}
		return valid;
	}
	private String stock(Loaded source, DataRow row, int column, List<Issue> issues) {
		Value value = row.at(column);
		try {
			if (value.error() != null) throw new IllegalArgumentException(value.error());
			return normalizeStockCode(value.raw(), value.numeric());
		} catch (IllegalArgumentException ex) {
			issue(issues, "ERROR", "INVALID_STOCK_CODE", source.at(row.number(), column), "", value.raw(), ex.getMessage()); return null;
		}
	}
	private Map<String, Company> companies(Loaded source, List<Issue> issues) {
		Map<String, Company> result = new TreeMap<>(); Set<String> conflicts = new HashSet<>();
		var h = headers(source, Set.of("corp_code", "corp_name", "corp_eng_name", "stock_code", "modify_date"), issues);
		if (!h.keySet().containsAll(Set.of("corp_code", "corp_name", "stock_code"))) return result;
		for (DataRow row : rows(source)) {
			if (!validMetadata(source, row, h.values(), issues)) continue;
			String code = stock(source, row, h.get("stock_code"), issues); if (code == null) continue;
			String corp = row.at(h.get("corp_code")).text(), name = row.at(h.get("corp_name")).text();
			if (!corp.matches("[0-9]{8}") || name.isBlank()) { issue(issues, "ERROR", "INVALID_COMPANY", source.at(row.number(), 0), code, corp, "corp_code 8자리/기업명 필요"); continue; }
			Company next = new Company(code, corp, name, source.at(row.number(), h.get("stock_code")));
			Company old = result.putIfAbsent(code, next);
			if (old != null) {
				boolean same = old.corpCode().equals(corp) && old.name().equals(name);
				issue(issues, same ? "WARNING" : "ERROR", same ? "DUPLICATE_COMPANY" : "CONFLICTING_COMPANY", next.provenance(), code, corp, "기업 마스터 중복");
				if (!same) conflicts.add(code);
			}
		}
		conflicts.forEach(result::remove); return result;
	}
	private Map<String, Industry> industries(Loaded source, Map<String, Company> companies, List<Issue> issues) {
		Map<String, Industry> result = new TreeMap<>(); Set<String> conflicts = new HashSet<>();
		var h = headers(source, Set.of("기업코드", "기업명", "업종코드", "업종명"), issues);
		if (!h.keySet().containsAll(Set.of("기업코드", "업종코드", "업종명"))) return result;
		for (DataRow row : rows(source)) {
			if (!validMetadata(source, row, h.values(), issues)) continue;
			String code = stock(source, row, h.get("기업코드"), issues); if (code == null) continue;
			String industry = row.at(h.get("업종코드")).text(), name = row.at(h.get("업종명")).text();
			try { industry = new BigDecimal(industry).toBigIntegerExact().toString(); } catch (NumberFormatException | ArithmeticException ex) { industry = ""; }
			if (!companies.containsKey(code) || !industry.matches("[0-9]{1,5}")) {
				issue(issues, "ERROR", "INVALID_INDUSTRY", source.at(row.number(), h.get("업종코드")), code, industry, "기업 연결/업종 값 오류"); continue;
			}
			if (name.isBlank()) {
				issue(issues, "WARNING", "MISSING_INDUSTRY_NAME", source.at(row.number(), h.get("업종명")), code, "", "업종명 NULL 허용; 코드와 재무 값은 보존"); name = null;
			}
			Industry next = new Industry(code, industry, name, source.at(row.number(), h.get("업종코드")));
			Industry old = result.putIfAbsent(code, next);
			if (old != null) {
				boolean same = old.code().equals(industry) && Objects.equals(old.name(), name);
				issue(issues, same ? "WARNING" : "ERROR", same ? "DUPLICATE_INDUSTRY" : "CONFLICTING_INDUSTRY", next.provenance(), code, industry, "업종 중복");
				if (!same) conflicts.add(code);
			}
		}
		conflicts.forEach(result::remove); return result;
	}
	private Map<Key, CanonicalMetricCell> metrics(Loaded source, boolean relative, int base,
		Map<String, Company> companies, Map<String, Industry> industries, List<Issue> issues) {
		Set<String> expected = new LinkedHashSet<>(List.of("기업코드", "기업명"));
		if (relative) expected.addAll(List.of("업종코드", "업종명"));
		Map<String, Header> specs = new HashMap<>();
		for (String name : relative ? REL : NAMES) for (int i = 0; i < 4; i++) {
			String label = name + (relative ? "_업종상대" : "") + "_" + (i == 0 ? "현재" : "분기-" + i);
			expected.add(label); specs.put(label, new Header(-1, MetricHeaderMapping.toMetricCode(name) + (relative ? "_IndRel" : ""), offsetQuarter(base, -i)));
		}
		var h = headers(source, expected, issues);
		Map<Key, CanonicalMetricCell> result = new TreeMap<>(); Set<Key> conflicts = new HashSet<>();
		if (!h.containsKey("기업코드")) return result;
		for (DataRow row : rows(source)) {
			List<Integer> metadataColumns = h.entrySet().stream().filter(e -> !specs.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
			if (!validMetadata(source, row, metadataColumns, issues)) continue;
			String code = stock(source, row, h.get("기업코드"), issues); if (code == null) continue;
			if (!companies.containsKey(code) || !industries.containsKey(code)) {
				issue(issues, "ERROR", "UNLINKED_COMPANY", source.at(row.number(), 0), code, "", "기업/업종 마스터 연결 없음"); continue;
			}
			if (relative && h.containsKey("업종코드")) {
				Value rawIndustry = row.at(h.get("업종코드"));
				String industry;
				try { industry = new BigDecimal(rawIndustry.text()).toBigIntegerExact().toString(); }
				catch (ArithmeticException | NumberFormatException ex) { industry = ""; }
				if (rawIndustry.error() != null || !industries.get(code).code().equals(industry)) {
					issue(issues, "ERROR", "INDUSTRY_MISMATCH", source.at(row.number(), h.get("업종코드")), code, rawIndustry.raw(), "업종 마스터와 불일치"); continue;
				}
			}
			for (var entry : h.entrySet()) {
				Header spec = specs.get(entry.getKey()); if (spec == null) continue;
				int column = entry.getValue(); Value value = row.at(column); Provenance at = source.at(row.number(), column);
				Key key = new Key(code, spec.quarter(), spec.metricCode(), "ACTUAL");
				if (value.error() != null) { issue(issues, "ERROR", value.error(), at, key.toString(), value.raw(), "셀 해석 거부"); conflicts.add(key); continue; }
				BigDecimal decimal;
				try { decimal = new MetricDecimalPolicy().normalize(value.raw(), value.percent()); }
				catch (IllegalArgumentException ex) { issue(issues, "ERROR", "INVALID_DECIMAL", at, key.toString(), value.raw(), ex.getMessage()); conflicts.add(key); continue; }
				CanonicalMetricCell old = result.get(key);
				if (old == null) result.put(key, new CanonicalMetricCell(key, decimal, List.of(at)));
				else {
					List<Provenance> origins = new ArrayList<>(old.provenance()); origins.add(at);
					boolean same = Objects.equals(old.value(), decimal);
					issue(issues, same ? "WARNING" : "ERROR", same ? "DUPLICATE_SAME_VALUE" : "CONFLICTING_VALUE", at, key.toString(), value.raw(), "최초 출처=" + old.provenance().getFirst());
					if (!same) conflicts.add(key);
					result.put(key, new CanonicalMetricCell(key, old.value(), origins));
				}
			}
		}
		conflicts.forEach(result::remove); return result;
	}
	private static void issue(List<Issue> issues, String severity, String code, Provenance at, String key, String raw, String detail) {
		issues.add(new Issue(severity, code, at, key, raw, detail));
	}
	public static String checksum(Collection<CanonicalMetricCell> cells) {
		StringBuilder canonical = new StringBuilder();
		cells.stream().sorted(Comparator.comparing(CanonicalMetricCell::key)).forEach(c -> canonical.append(c.key().stockCode()).append('|')
			.append(c.key().quarterKey()).append('|').append(c.key().metricCode()).append('|').append(c.key().valueType()).append('|')
			.append(c.value() == null ? "NULL" : c.value().toPlainString()).append('\n'));
		return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
	}
	public static String sha256(byte[] bytes) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
	}
}
