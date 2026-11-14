package com.aivle.project.dataimport.cli;

import com.aivle.project.dataimport.model.ImportPreflightResult;
import com.aivle.project.dataimport.model.ImportDatasetCommand;
import com.aivle.project.dataimport.model.ImportDatasetCommand.*;
import com.aivle.project.dataimport.service.ImportPreflightService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** DB와 Spring 서버를 시작하지 않는 사전 검증 진입점. */
public final class ImportPreflightCommand {
	public static void main(String[] args) throws Exception {
		if (args.length != 3) throw new IllegalArgumentException("입력 디렉터리, 새 결과 디렉터리, 기준 YYYYQ 필요");
		ImportPreflightResult result = new ImportPreflightService().preflight(dataset(Path.of(args[0]), Integer.parseInt(args[2])));
		writeResult(result, Path.of(args[1]));
		System.out.println(new ObjectMapper().writeValueAsString(Map.of("ready", result.ready(), "counts", result.manifest().counts(), "fingerprint", result.manifest().fingerprint())));
		if (!result.ready()) System.exit(2);
	}

	public static ImportDatasetCommand dataset(Path root, int baseQuarterKey) {
		List<Source> sources = List.of(
			new Source(Role.COMPANY, root.resolve("python/listed_company_codes.csv"), "UTF-8"),
			new Source(Role.INDUSTRY, root.resolve("기업명-업종코드.xlsx"), null),
			new Source(Role.METRIC13, root.resolve("2025Q3_13지표_4분기추이_no_industry.xlsx"), null),
			new Source(Role.METRIC5, root.resolve("2025Q3_5지표_업종상대값_4분기추이.xlsx"), null),
			new Source(Role.REFERENCE13, root.resolve("2025Q3_13지표_4분기추이_no_industry.csv"), "UTF-8"),
			new Source(Role.REFERENCE5, root.resolve("2025Q3_5지표_업종상대값_4분기추이.csv"), "UTF-8"));
		return new ImportDatasetCommand(baseQuarterKey, sources);
	}

	public static void writeResult(ImportPreflightResult result, Path output) throws IOException {
		// 새 실행 경로만 생성하여 이전 시험 근거를 덮어쓰지 않는다.
		output = output.toAbsolutePath().normalize();
		if (Files.exists(output)) throw new IOException("기존 결과 디렉터리 사용 불가: " + output);
		Files.createDirectories(output.getParent());
		Files.createDirectory(output);
		ObjectMapper mapper = new ObjectMapper();
		mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("source-manifest.json").toFile(), result.manifest());
		mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("issues.json").toFile(), result.issues());
		mapper.writeValue(output.resolve("companies.json").toFile(), result.companies());
		mapper.writeValue(output.resolve("industries.json").toFile(), result.industries());
		try (var writer = Files.newBufferedWriter(output.resolve("canonical-cells.jsonl"), StandardCharsets.UTF_8)) {
			for (var cell : result.cells()) { writer.write(mapper.writeValueAsString(cell)); writer.newLine(); }
		}
		try (var writer = Files.newBufferedWriter(output.resolve("invalid-rows.csv"), StandardCharsets.UTF_8)) {
			writer.write("severity,code,source,fileHash,sheet,row,column,key,raw,detail\n");
			for (var issue : result.issues()) {
				var p = issue.provenance();
				writer.write(String.join(",", List.of(issue.severity(), issue.code(), p.source(), p.fileHash(), p.sheet(),
					Integer.toString(p.row()), Integer.toString(p.column()), issue.key(), Objects.toString(issue.raw(), ""), issue.detail()).stream().map(ImportPreflightCommand::csv).toList()));
				writer.newLine();
			}
		}
		// 모든 파일을 닫은 뒤 마지막 요약을 원자적으로 공개한다. 요약 부재는 불완전 실행이다.
		Path pending = output.resolve("preflight.json.partial");
		mapper.writerWithDefaultPrettyPrinter().writeValue(pending.toFile(), Map.of(
			"complete", true, "ready", result.ready(), "counts", result.manifest().counts(), "comparisons", result.comparisons(),
			"issueCounts", result.issues().stream().collect(java.util.stream.Collectors.groupingBy(i -> i.severity() + ":" + i.code(), TreeMap::new, java.util.stream.Collectors.counting())),
			"limitations", List.of("DB 적재/대조 미수행", "원천 재무 사실/산식 정확성 미검증", "오류가 있는 전체 데이터셋은 자동 승인하지 않음")));
		Files.move(pending, output.resolve("preflight.json"), StandardCopyOption.ATOMIC_MOVE);
	}
	private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}
