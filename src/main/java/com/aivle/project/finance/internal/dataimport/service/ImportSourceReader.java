package com.aivle.project.finance.internal.dataimport.service;

import com.aivle.project.finance.api.ImportDatasetCommand.Source;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;

/** 파일을 한 번 읽은 바이트로 해시와 해석을 일치시킨다. */
final class ImportSourceReader {
	static record Value(String raw, boolean numeric, boolean percent, String error) {
		String text() { return raw == null ? "" : raw.trim(); }
	}
	static record DataRow(int number, List<Value> values) {
		Value at(int column) { return column < values.size() ? values.get(column) : new Value("", false, false, null); }
		boolean blank() { return values.stream().allMatch(v -> v.text().isEmpty() && v.error() == null); }
	}
	static record Table(String sheet, String encoding, List<DataRow> rows) { }

	Table read(Source source, byte[] bytes) throws IOException {
		if (source.path().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) return csv(source, bytes);
		if (!source.path().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new IOException("지원 형식은 csv/xlsx");
		try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
			if (workbook.getNumberOfSheets() != 1) throw new IOException("단일 시트 파일만 지원");
			Sheet sheet = workbook.getSheetAt(0);
			List<DataRow> rows = new ArrayList<>();
			for (int n = 0; n <= sheet.getLastRowNum(); n++) {
				Row row = sheet.getRow(n);
				List<Value> values = new ArrayList<>();
				if (row != null) for (int c = 0; c < row.getLastCellNum(); c++) values.add(excelValue(row.getCell(c)));
				rows.add(new DataRow(n + 1, List.copyOf(values)));
			}
			return new Table(sheet.getSheetName(), "OOXML", List.copyOf(rows));
		}
	}

	private Value excelValue(Cell cell) {
		if (cell == null) return new Value("", false, false, null);
		return switch (cell.getCellType()) {
			case BLANK -> new Value("", false, false, null);
			case STRING -> new Value(cell.getStringCellValue(), false, false, null);
			case NUMERIC -> {
				String format = cell.getCellStyle().getDataFormatString();
				String effective = format == null ? "" : format.replaceAll("\"[^\"]*\"|\\\\.", "");
				String raw = cell instanceof XSSFCell x ? x.getRawValue() : Double.toString(cell.getNumericCellValue());
				yield new Value(raw, true, effective.contains("%"), DateUtil.isCellDateFormatted(cell) ? "DATE_CELL" : null);
			}
			case FORMULA -> new Value(cell.getCellFormula(), false, false, "UNVERIFIED_FORMULA");
			default -> new Value(cell.toString(), false, false, "INVALID_CELL_TYPE");
		};
	}

	private Table csv(Source source, byte[] bytes) throws IOException {
		String encoding = source.encoding();
		if (!"UTF-8".equals(encoding) && !"MS949".equals(encoding)) throw new IOException("CSV 인코딩을 UTF-8 또는 MS949로 명시해야 함");
		String text = Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		boolean bom = text.startsWith("\ufeff");
		if (bom) text = text.substring(1);
		List<DataRow> rows = new ArrayList<>();
		List<Value> fields = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false, closed = false, started = false;
		int line = 1, rowLine = 1;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') { value.append('"'); i++; }
					else { quoted = false; closed = true; }
				} else { value.append(ch); if (ch == '\n') line++; }
			} else if (ch == ',' || ch == '\r' || ch == '\n') {
				fields.add(new Value(value.toString(), false, false, null)); value.setLength(0); closed = false; started = false;
				if (ch != ',') {
					rows.add(new DataRow(rowLine, List.copyOf(fields))); fields.clear();
					if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
					line++; rowLine = line;
				}
			} else if (ch == '"' && !started && !closed) { quoted = true; started = true; }
			else {
				if (closed || ch == '"') throw new IOException("잘못된 CSV 따옴표: line=" + line);
				value.append(ch); started = true;
			}
		}
		if (quoted) throw new IOException("닫히지 않은 CSV 따옴표");
		if (started || closed || !fields.isEmpty()) {
			fields.add(new Value(value.toString(), false, false, null)); rows.add(new DataRow(rowLine, List.copyOf(fields)));
		}
		return new Table("CSV", bom ? encoding + "-BOM" : encoding, List.copyOf(rows));
	}
}
