package com.aivle.project.dataimport;

import static org.assertj.core.api.Assertions.*;
import static com.aivle.project.finance.api.ImportDatasetCommand.Role.*;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.api.ImportDatasetCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.api.ImportDatasetCommand.Role;
import com.aivle.project.finance.api.ImportDatasetCommand.Source;
import com.aivle.project.finance.internal.dataimport.service.*;
import com.aivle.project.finance.internal.dataimport.cli.ImportPreflightCommand;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ImportPreflightTest {
	@TempDir Path dir;
	private final ImportPreflightService service = new ImportPreflightService();
	private final MetricDecimalPolicy decimal = new MetricDecimalPolicy();
	private static final List<String> NAMES = List.of("ROA", "ROE", "매출액영업이익률", "부채비율",
		"자기자본비율", "자본잠식률", "단기차입금비율", "유동비율", "당좌비율", "유동부채비율",
		"CFO_자산비율", "CFO_매출액비율", "CFO증감률");
	private static final List<String> REL = List.of("ROA", "부채비율", "단기차입금비율", "유동비율", "CFO_자산비율");

	@ParameterizedTest
	@CsvSource({"20,true,000020", "000020,false,000020", "0008Z0,false,0008Z0", "20.0,true,000020"})
	void stockCodePreservesIdentity(String raw, boolean numeric, String expected) {
		// given / when / then: 숫자 절삭이나 영문 소실을 탐지한다.
		assertThat(ImportPreflightService.normalizeStockCode(raw, numeric)).isEqualTo(expected);
	}
	@ParameterizedTest @NullAndEmptySource @ValueSource(strings={"null", "20.5", "1234567", "8Z0", "NaN"})
	void invalidCodeIsRejected(String raw) {
		// given / when / then
		assertThatIllegalArgumentException().isThrownBy(() -> ImportPreflightService.normalizeStockCode(raw, false));
	}
	@Test void quarterCrossesYearAndRejectsInvalidQuarter() {
		// given / when / then: YYYYQ를 정수 뺄셈하면 깨지는 경계다.
		assertThat(ImportPreflightService.offsetQuarter(20253, -3)).isEqualTo(20244);
		assertThatIllegalArgumentException().isThrownBy(() -> ImportPreflightService.offsetQuarter(20250, 0));
	}
	@Test void decimalSeparatesNullZeroPercentAndRoundsOnce() {
		// given / when / then
		assertThat(decimal.normalize(null, false)).isNull();
		assertThat(decimal.normalize("NULL", false)).isNull();
		assertThat(decimal.normalize("-", false)).isNull();
		assertThat(decimal.normalize("0", false)).isEqualByComparingTo("0.0000");
		assertThat(decimal.normalize("12.34565%", false)).isEqualByComparingTo("12.3457");
		assertThat(decimal.normalize("0.1234565", true)).isEqualByComparingTo("12.3457");
		assertThat(decimal.normalize("-1.23445", false)).isEqualByComparingTo("-1.2345");
	}
	@ParameterizedTest @ValueSource(strings={"NaN", "Infinity", "1,2", "abc", "10000000000000000", "9999999999999999.99995"})
	void invalidDecimalAndRoundedOverflowAreRejected(String raw) {
		// given / when / then
		assertThatIllegalArgumentException().isThrownBy(() -> decimal.normalize(raw, false));
	}
	@Test void fullFixtureKeepsNullZeroQuarterAndProvenance() throws Exception {
		// given
		ImportDatasetCommand cmd = fixture("20", 0.1234565, true, false);
		// when
		var result = service.preflight(cmd);
		// then
		assertThat(result.ready()).isTrue();
		assertThat(result.cells()).hasSize(72);
		var first = cell(result, "ROA", 20253);
		assertThat(first.value()).isEqualByComparingTo("12.3457");
		assertThat(first.provenance().getFirst().row()).isEqualTo(2);
		assertThat(first.provenance().getFirst().column()).isEqualTo(3);
		assertThat(first.provenance().getFirst().fileHash()).hasSize(64);
		assertThat(cell(result, "ROA", 20252).value()).isNull();
		assertThat(cell(result, "ROA", 20251).value()).isEqualByComparingTo("0");
		assertThat(cell(result, "ROA", 20244).value()).isEqualByComparingTo("1.0000");
		assertThat(result.manifest().counts().get("nullCells")).isEqualTo(1);
	}
	@Test void csvComparisonUsesCanonicalScaleAndQuotedComma() throws Exception {
		// given
		var cmd = fixture("0008Z0", 1.2344499999, false, false);
		List<Source> sources = new ArrayList<>(cmd.sources());
		Path ref = dir.resolve("reference.csv");
		List<String> header = headers(false);
		List<String> row = new ArrayList<>(Collections.nCopies(header.size(), "1"));
		row.set(0,"0008Z0"); row.set(1,"\"이름, 줄바꿈\n기업\""); row.set(2,"1.23444"); row.set(3,""); row.set(4,"0");
		Files.writeString(ref, "\ufeff" + String.join(",", header)+"\r\n"+String.join(",",row)+"\r\n");
		sources.add(new Source(REFERENCE13,ref,"UTF-8"));
		// when
		var result = service.preflight(new ImportDatasetCommand(20253,sources));
		// then
		assertThat(result.ready()).isTrue();
		assertThat(result.comparisons()).singleElement().satisfies(c -> {
			assertThat(c.different()).isZero(); assertThat(c.missing()).isZero(); assertThat(c.unexpected()).isZero();
			assertThat(c.primaryChecksum()).isEqualTo(c.referenceChecksum());
		});
	}
	@Test void duplicateSameValueRetainsOriginsAndConflictQuarantinesWholeKey() throws Exception {
		// given
		var cmd = fixture("20", 1.0, false, false);
		Path metric = cmd.sources().get(2).path();
		try (var input = Files.newInputStream(metric); Workbook wb = WorkbookFactory.create(input)) {
			Sheet s = wb.getSheetAt(0);
			for (int n=2;n<=3;n++) { Row r=s.createRow(n); r.createCell(0).setCellValue(20); r.createCell(1).setCellValue("기업");
				for (int c=2;c<54;c++) { Cell original=s.getRow(1).getCell(c); if(original!=null) r.createCell(c).setCellValue(original.getNumericCellValue()); }
			}
			s.getRow(2).getCell(2).setCellValue(9);
			try(OutputStream out=Files.newOutputStream(metric)){wb.write(out);}
		}
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.cells()).noneMatch(c -> c.key().metricCode().equals("ROA") && c.key().quarterKey()==20253);
		assertThat(cell(result,"ROA",20251).provenance()).hasSize(3);
		assertThat(result.issues()).anyMatch(i -> i.code().equals("CONFLICTING_VALUE"));
		assertThat(result.issues()).anyMatch(i -> i.code().equals("DUPLICATE_SAME_VALUE"));
	}
	@Test void invalidCodeAndFormulaAreErrorsRatherThanNull() throws Exception {
		// given
		var cmd=fixture("null",1,false,true);
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.cells()).isEmpty();
		assertThat(result.issues()).anyMatch(i -> i.code().equals("INVALID_STOCK_CODE"));
		var formula=service.preflight(fixture("20",1,false,true));
		assertThat(formula.issues()).anyMatch(i -> i.code().equals("UNVERIFIED_FORMULA"));
		assertThat(formula.cells()).hasSize(71);
	}
	@Test void sourceChangeChangesFingerprintWhileRepeatIsStable() throws Exception {
		// given
		var cmd=fixture("20",1,false,false);
		// when
		var a=service.preflight(cmd); var b=service.preflight(cmd);
		Files.writeString(cmd.sources().getFirst().path(),Files.readString(cmd.sources().getFirst().path())+"\n");
		var c=service.preflight(cmd);
		// then
		assertThat(a.manifest().fingerprint()).isEqualTo(b.manifest().fingerprint()).isNotEqualTo(c.manifest().fingerprint());
		assertThat(a.manifest().canonicalChecksum()).isEqualTo(c.manifest().canonicalChecksum());
	}
	@Test void referenceDifferenceAndMissingKeysAreDetected() throws Exception {
		// given: CSV에서 한 값만 바꾼다.
		var cmd=fixture("20",1,false,false);
		Path reference=dir.resolve("reference.csv");
		List<String> row=new ArrayList<>(Collections.nCopies(54,"1"));
		row.set(0,"000020");row.set(1,"기업");row.set(2,"9");row.set(3,"");row.set(4,"0");
		Files.writeString(reference,String.join(",",headers(false))+"\n"+String.join(",",row)+"\n");
		List<Source> sources=new ArrayList<>(cmd.sources());sources.add(new Source(REFERENCE13,reference,"UTF-8"));
		// when
		var result=service.preflight(new ImportDatasetCommand(20253,sources));
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.comparisons().getFirst().different()).isEqualTo(1);
		assertThat(result.issues()).anyMatch(i -> i.code().equals("REFERENCE_MISMATCH"));
	}
	@Test void missingHeaderAndEmptyMetricSourceCannotPass() throws Exception {
		// given
		var cmd=fixture("20",1,false,false);Path file=cmd.sources().get(2).path();
		try(var in=Files.newInputStream(file);Workbook wb=WorkbookFactory.create(in)) {
			wb.getSheetAt(0).getRow(0).getCell(2).setCellValue("未知");
			try(var out=Files.newOutputStream(file)){wb.write(out);}
		}
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.code().equals("MISSING_HEADER"));
		try(var in=Files.newInputStream(file);Workbook wb=WorkbookFactory.create(in)) {
			wb.getSheetAt(0).removeRow(wb.getSheetAt(0).getRow(1));
			try(var out=Files.newOutputStream(file)){wb.write(out);}
		}
		assertThat(service.preflight(cmd).issues()).anyMatch(i -> i.code().equals("EMPTY_SOURCE"));
	}
	@Test void industryMismatchInRelativeFileIsQuarantined() throws Exception {
		// given
		var cmd=fixture("20",1,false,false);Path file=cmd.sources().get(3).path();
		try(var in=Files.newInputStream(file);Workbook wb=WorkbookFactory.create(in)) {
			wb.getSheetAt(0).getRow(1).getCell(2).setCellValue(999);
			try(var out=Files.newOutputStream(file)){wb.write(out);}
		}
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.issues()).anyMatch(i -> i.code().equals("INDUSTRY_MISMATCH"));
		assertThat(result.cells()).hasSize(52);
	}
	@Test void malformedCsvCannotBecomeMissingData() throws Exception {
		// given
		var cmd=fixture("20",1,false,false);Path file=cmd.sources().getFirst().path();
		Files.writeString(file,"corp_code,corp_name,corp_eng_name,stock_code,modify_date\n00123456,\"닫히지 않은 이름");
		// when / then
		assertThatIOException().isThrownBy(() -> service.preflight(cmd));
	}
	@Test void exportPreservesEvidenceAndRefusesOverwrite() throws Exception {
		// given
		var result=service.preflight(fixture("20",1,false,false));Path output=dir.resolve("results");
		// when
		ImportPreflightCommand.writeResult(result,output);
		// then
		assertThat(Files.readString(output.resolve("preflight.json"))).contains("canonicalCells", "72");
		assertThat(Files.readAllLines(output.resolve("canonical-cells.jsonl"))).hasSize(72);
		assertThat(Files.readString(output.resolve("source-manifest.json"))).contains("sha256", "decimal20-4-half-up-v1");
		assertThatIOException().isThrownBy(() -> ImportPreflightCommand.writeResult(result,output));
	}
	@Test void optionalIndustryNameDoesNotHideFinancialCells() throws Exception {
		// given: V13 스키마는 industry_name NULL을 허용한다.
		var cmd=fixture("20",1,false,false);Path file=cmd.sources().get(1).path();
		try(var in=Files.newInputStream(file);Workbook wb=WorkbookFactory.create(in)) {
			wb.getSheetAt(0).getRow(1).getCell(3).setBlank();
			try(var out=Files.newOutputStream(file)){wb.write(out);}
		}
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isTrue();
		assertThat(result.cells()).hasSize(72);
		assertThat(result.industries()).singleElement().satisfies(i -> assertThat(i.name()).isNull());
		assertThat(result.issues()).anyMatch(i -> i.severity().equals("WARNING") && i.code().equals("MISSING_INDUSTRY_NAME"));
	}
	@ParameterizedTest @CsvSource({"1,2,212", "1,3,212", "2,1,212", "3,3,212"})
	void metadataFormulaIsNeverTrusted(int sourceIndex,int column,String formula) throws Exception {
		// given: 수식 문자열 자체가 숫자여도 계산/검증 없이 메타데이터로 수용하면 안 된다.
		var cmd=fixture("20",1,false,false);Path file=cmd.sources().get(sourceIndex).path();
		try(var in=Files.newInputStream(file);Workbook wb=WorkbookFactory.create(in)) {
			wb.getSheetAt(0).getRow(1).getCell(column).setCellFormula(formula);
			try(var out=Files.newOutputStream(file)){wb.write(out);}
		}
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.code().equals("UNVERIFIED_FORMULA"));
	}
	@Test void truncatedCompanyCsvRowIsRejected() throws Exception {
		// given: stock_code는 남았지만 마지막 필드가 누락된 CSV다.
		var cmd=fixture("20",1,false,false);
		Files.writeString(cmd.sources().getFirst().path(),"corp_code,corp_name,corp_eng_name,stock_code,modify_date\n00123456,기업,,000020\n");
		// when
		var result=service.preflight(cmd);
		// then
		assertThat(result.ready()).isFalse();
		assertThat(result.issues()).anyMatch(i -> i.code().equals("CSV_COLUMN_COUNT"));
	}
	@Test void failedCanonicalExportCannotLeaveReadySummary() throws Exception {
		// given: canonical 직렬화 도중 발생하는 출력 실패를 주입한다.
		var normal=service.preflight(fixture("20",1,false,false));
		java.math.BigDecimal broken=new java.math.BigDecimal("1") {
			@Override public String toString() { throw new IllegalStateException("injected serialization failure"); }
		};
		var cell=normal.cells().getFirst();
		var result=new ImportPreflightResult(normal.manifest(),List.of(new CanonicalMetricCell(cell.key(),broken,cell.provenance())),normal.companies(),normal.industries(),normal.issues(),normal.comparisons());
		Path output=dir.resolve("failed-export");
		// when / then
		assertThatThrownBy(() -> ImportPreflightCommand.writeResult(result,output)).isInstanceOf(Exception.class);
		assertThat(output.resolve("preflight.json")).doesNotExist();
	}

	private CanonicalMetricCell cell(ImportPreflightResult r,String code,int q) {
		return r.cells().stream().filter(c -> c.key().metricCode().equals(code) && c.key().quarterKey()==q).findFirst().orElseThrow();
	}
	private ImportDatasetCommand fixture(String stock,double value,boolean percent,boolean formula) throws Exception {
		Path master=dir.resolve("company.csv");
		Files.writeString(master,"corp_code,corp_name,corp_eng_name,stock_code,modify_date\n00123456,기업,,000020,20250101\n00123457,기업,,0008Z0,20250101\n");
		Path industry=dir.resolve("industry.xlsx");
		try(Workbook wb=new XSSFWorkbook()) { Sheet s=wb.createSheet("업종"); Row h=s.createRow(0);
			String[] names={"기업코드","기업명","업종코드","업종명"};for(int i=0;i<4;i++)h.createCell(i).setCellValue(names[i]);
			Row r=s.createRow(1);r.createCell(0).setCellValue(stock);r.createCell(1).setCellValue("기업");r.createCell(2).setCellValue(212);r.createCell(3).setCellValue("의약품");
			try(OutputStream out=Files.newOutputStream(industry)){wb.write(out);}
		}
		List<Source> sources=new ArrayList<>();sources.add(new Source(COMPANY,master,"UTF-8"));sources.add(new Source(INDUSTRY,industry,null));
		for(boolean rel:List.of(false,true)) { Path file=dir.resolve(rel?"metric5.xlsx":"metric13.xlsx");
			try(Workbook wb=new XSSFWorkbook()){ Sheet s=wb.createSheet("지표"); Row h=s.createRow(0);List<String> headers=headers(rel);
				for(int c=0;c<headers.size();c++)h.createCell(c).setCellValue(headers.get(c));
				Row r=s.createRow(1);if(stock.equals("20"))r.createCell(0).setCellValue(20);else r.createCell(0).setCellValue(stock);
				r.createCell(1).setCellValue("기업");int start=rel?4:2;
				if(rel){r.createCell(2).setCellValue(212);r.createCell(3).setCellValue("의약품");}
				for(int c=start;c<headers.size();c++)r.createCell(c).setCellValue(1);
				if(!rel){r.getCell(2).setCellValue(value);r.removeCell(r.getCell(3));r.getCell(4).setCellValue(0);
					if(percent){CellStyle style=wb.createCellStyle();style.setDataFormat(wb.createDataFormat().getFormat("0.000%"));r.getCell(2).setCellStyle(style);}
					if(formula)r.getCell(2).setCellFormula("1+1");}
				try(OutputStream out=Files.newOutputStream(file)){wb.write(out);}
			} sources.add(new Source(rel?METRIC5:METRIC13,file,null));
		}
		return new ImportDatasetCommand(20253,sources);
	}
	private List<String> headers(boolean rel) {
		List<String> h=new ArrayList<>(List.of("기업코드","기업명"));if(rel)h.addAll(List.of("업종코드","업종명"));
		for(String name:rel?REL:NAMES)for(String q:List.of("현재","분기-1","분기-2","분기-3"))h.add(name+(rel?"_업종상대":"")+"_"+q);
		return h;
	}
}
