package com.aivle.project.dataimport;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.api.CanonicalMetricCell.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.finance.internal.dataimport.repository.SnapshotReportRepository;
import com.aivle.project.finance.internal.dataimport.service.*;
import com.aivle.project.support.PortfolioIntegrationSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("realinfra") @ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
	"PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets",
	"PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio","spring.kafka.listener.auto-startup=false"})
class ImportReconciliationIntegrationTest extends PortfolioIntegrationSupport {
	@Autowired ImportRunService imports;
	@Autowired SnapshotMaterializationService materializer;
	@Autowired SnapshotReportRepository reports;
	@Autowired com.aivle.project.finance.internal.dataimport.repository.ImportReconciliationRepository reconciliation;
	@Autowired ImportVerificationService verification;
	@Autowired ImportMasterService masters;
	@Autowired JdbcTemplate jdbc;
	private UUID snapshot;
	private long company,version,quarter;
	private ImportPreflightResult source;
	@BeforeEach void prepare() {
		String stock="Y"+UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase(Locale.ROOT);
		String corp=String.format("%08d",new Random().nextInt(90000000)+10000000);
		jdbc.update("INSERT INTO companies(corp_code,corp_name,stock_code) VALUES(?,?,?)",corp,"reconciliation-"+stock,stock);
		company=jdbc.queryForObject("SELECT id FROM companies WHERE stock_code=?",Long.class,stock);
		String hash=ImportPreflightService.sha256(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
		List<CanonicalMetricCell> cells=new ArrayList<>();
		for(int n=0;n<4;n++)for(int m=0;m<SnapshotVisibilityIntegrationTest.CODES.size();m++)cells.add(new CanonicalMetricCell(new Key(stock,ImportPreflightService.offsetQuarter(20253,-n),SnapshotVisibilityIntegrationTest.CODES.get(m),"ACTUAL"),n==0&&m==0?null:n==0&&m==1?BigDecimal.ZERO.setScale(4):new BigDecimal("1.2345"),List.of(new Provenance("METRIC13",hash,"fixture",cells.size()+2,3))));
		String identity="20253|preflight-v1|decimal20-4-half-up-v1\nMETRIC13|"+hash+"|OOXML\n";
		var manifest=new ImportManifest(ImportPreflightService.sha256(identity.getBytes(StandardCharsets.UTF_8)),20253,"preflight-v1","decimal20-4-half-up-v1",ImportPreflightService.checksum(cells),List.of(new ImportManifest.SourceManifest("METRIC13","fixture.xlsx",hash,100,"OOXML","fixture",72)),Map.of("canonicalCells",72L));
		source=new ImportPreflightResult(manifest,cells,List.of(),List.of(),List.of(),List.of());
		UUID run=imports.start(new ImportStartCommand(source,"D04-fixture",10,QuarantinePolicy.STRICT));
		while(!imports.status(run).snapshotState().equals("STAGED"))imports.stageNext(run);
		snapshot=imports.status(run).snapshotId();for(int n=0;n<4;n++)materializer.materialize(snapshot,company,ImportPreflightService.offsetQuarter(20253,-n));
		quarter=reports.quarter(20253);version=materializer.materialize(snapshot,company,20253);
	}
	@Test void matchesEveryKeyValueNullAndChecksum() {
		// given / when
		var result=verification.verify(snapshot,source);
		// then
		assertThat(result.valid()).isTrue();assertThat(result.expectedKeys()).isEqualTo(72);
		assertThat(result.report().nulls()).isEqualTo(1);assertThat(result.report().zeros()).isEqualTo(1);
		assertThat(result.report().checksum()).isEqualTo(source.manifest().canonicalChecksum());
	}
	@Test void findsMissingStageKey() {
		// given
		jdbc.update("DELETE FROM import_stage_values WHERE snapshot_id=? AND quarter_key=20253 AND metric_code='ROA'",snapshot.toString());
		// when / then
		var result=verification.verify(snapshot);assertThat(result.stage().missing()).isEqualTo(1);assertThat(result.valid()).isFalse();
	}
	@Test void findsDuplicateKeyAcrossReportVersions() {
		// given: 같은 버전 내 중복은 DB unique가 막지만 다른 버전에 같은 키가 생길 수 있다.
		long report=jdbc.queryForObject("SELECT company_report_id FROM company_report_versions WHERE id=?",Long.class,version);
		jdbc.update("INSERT INTO company_report_versions(company_report_id,version_no,generated_at,is_published,data_snapshot_id) VALUES(?,99,CURRENT_TIMESTAMP,0,?)",report,snapshot.toString());
		long extra=jdbc.queryForObject("SELECT id FROM company_report_versions WHERE company_report_id=? AND version_no=99",Long.class,report);
		jdbc.update("INSERT INTO company_report_metric_values(report_version_id,metric_id,quarter_id,metric_value,value_type) SELECT ?,metric_id,quarter_id,metric_value,value_type FROM company_report_metric_values WHERE report_version_id=? AND metric_id=(SELECT id FROM metrics WHERE metric_code='ROA')",extra,version);
		// when / then
		assertThat(verification.verify(snapshot).report().duplicateKeys()).isEqualTo(1);
	}
	@Test void findsWrongQuarter() {
		// given
		long future=reports.quarter(20254);jdbc.update("UPDATE company_report_metric_values SET quarter_id=? WHERE report_version_id=? AND metric_id=(SELECT id FROM metrics WHERE metric_code='ROA')",future,version);
		// when / then
		var result=verification.verify(snapshot);assertThat(result.report().invalidQuarters()).isEqualTo(1);assertThat(result.report().missing()).isEqualTo(1);
	}
	@Test void distinguishesNullFromZero() {
		// given
		jdbc.update("UPDATE company_report_metric_values SET metric_value=0 WHERE report_version_id=? AND metric_id=(SELECT id FROM metrics WHERE metric_code='ROA')",version);
		// when / then
		assertThat(verification.verify(snapshot).report().different()).isEqualTo(1);
	}
	@Test void findsStoredRoundingDifference() {
		// given: DB 반올림 후 값도 원본 정규화 기대값과 달라지면 탐지한다.
		jdbc.update("UPDATE company_report_metric_values SET metric_value=1.23456 WHERE report_version_id=? AND metric_id=(SELECT id FROM metrics WHERE metric_code='OpMargin')",version);
		// when / then
		assertThat(verification.verify(snapshot).report().different()).isEqualTo(1);
	}
	@Test void rejectsPrecisionViolationInExpectedSource() {
		// given
		var changed=new ArrayList<>(source.cells());var old=changed.get(2);changed.set(2,new CanonicalMetricCell(old.key(),new BigDecimal("1.23456"),old.provenance()));
		var m=source.manifest();var bad=new ImportPreflightResult(new ImportManifest(m.fingerprint(),m.baseQuarterKey(),m.parserVersion(),m.decimalPolicyVersion(),ImportPreflightService.checksum(changed),m.sources(),m.counts()),changed,source.companies(),source.industries(),source.issues(),source.comparisons());
		// when / then
		assertThat(verification.verify(snapshot,bad).unexpectedDifferenceCount()).isPositive();
	}
	@Test void masterLoadIsIdempotentAndPreservesUnknownIndustryName() {
		// given
		String code=String.valueOf(90000+new Random().nextInt(9999));UUID id=masterSnapshot(false,code);
		// when
		masters.load(id);masters.load(id);
		// then
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM industry_codes WHERE industry_code=?",Integer.class,code)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT industry_name FROM industry_codes WHERE industry_code=?",String.class,code)).isNull();
		assertThat(jdbc.queryForObject("SELECT i.industry_code FROM companies c JOIN industry_codes i ON i.id=c.industry_code_id WHERE c.id=?",String.class,company)).isEqualTo(code);
	}
	@Test void conflictingMasterRollsBackInsteadOfOverwritingCompany() {
		// given
		String before=jdbc.queryForObject("SELECT corp_name FROM companies WHERE id=?",String.class,company),code=String.valueOf(90000+new Random().nextInt(9999));UUID id=masterSnapshot(true,code);
		// when / then
		assertThatThrownBy(()->masters.load(id)).isInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject("SELECT corp_name FROM companies WHERE id=?",String.class,company)).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM industry_codes WHERE industry_code=?",Integer.class,code)).isZero();
	}
	@Test void corruptedMasterMetadataIsRejectedBeforeWriting() {
		// given
		String code=String.valueOf(90000+new Random().nextInt(9999));UUID id=masterSnapshot(false,code);
		jdbc.update("UPDATE data_snapshots SET companies_json='[]' WHERE id=?",id.toString());
		// when / then
		assertThatThrownBy(()->masters.load(id)).isInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM industry_codes WHERE industry_code=?",Integer.class,code)).isZero();
	}
	private UUID masterSnapshot(boolean conflict,String industry) {
		String stock=source.cells().getFirst().key().stockCode(),corp=jdbc.queryForObject("SELECT corp_code FROM companies WHERE id=?",String.class,company),name=jdbc.queryForObject("SELECT corp_name FROM companies WHERE id=?",String.class,company);
		String hash=ImportPreflightService.sha256(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));var sources=new ArrayList<>(source.manifest().sources());sources.add(new ImportManifest.SourceManifest("INDUSTRY","industry.xlsx",hash,100,"OOXML","fixture",1));
		StringBuilder identity=new StringBuilder("20253|preflight-v1|decimal20-4-half-up-v1\n");sources.stream().sorted(Comparator.comparing(ImportManifest.SourceManifest::role)).forEach(s->identity.append(s.role()).append('|').append(s.sha256()).append('|').append(s.encoding()).append('\n'));
		var m=source.manifest();var manifest=new ImportManifest(ImportPreflightService.sha256(identity.toString().getBytes(StandardCharsets.UTF_8)),20253,m.parserVersion(),m.decimalPolicyVersion(),m.canonicalChecksum(),sources,m.counts());
		var p=new Provenance("INDUSTRY",hash,"fixture",2,1);var input=new ImportPreflightResult(manifest,source.cells(),List.of(new ImportPreflightResult.Company(stock,corp,conflict?"conflicting-name":name,p)),List.of(new ImportPreflightResult.Industry(stock,industry,null,p)),List.of(),List.of());
		return imports.status(imports.start(new ImportStartCommand(input,"D04-master-fixture",10,QuarantinePolicy.STRICT))).snapshotId();
	}
	@Test void cliVerifiesInSeparateLocalJvm()throws Exception {
		// given
		Path output=cliOutput();
		// when / then
		assertThat(cli("local",output)).isZero();
		var result=new com.fasterxml.jackson.databind.ObjectMapper().readValue(output.resolve("verification.json").toFile(),ImportVerificationResult.class);
		assertThat(result.basis()).isEqualTo("DB_PAYLOAD");assertThat(result.valid()).isTrue();assertThat(Files.exists(output.resolve("complete.json"))).isTrue();
	}
	@Test void cliRefusesExistingOutput()throws Exception {
		// given
		Path output=cliOutput();Files.createDirectories(output);Files.writeString(output.resolve("keep.txt"),"existing");
		// when / then
		assertThat(cli("local",output)).isEqualTo(1);assertThat(Files.readString(output.resolve("keep.txt"))).isEqualTo("existing");
	}
	@Test void cliDoesNotMarkDifferentDataAsSuccessful()throws Exception {
		// given
		jdbc.update("UPDATE company_report_metric_values SET metric_value=0 WHERE report_version_id=? AND metric_id=(SELECT id FROM metrics WHERE metric_code='ROA')",version);Path output=cliOutput();
		// when / then
		assertThat(cli("local",output)).isEqualTo(2);assertThat(Files.exists(output.resolve("complete.json"))).isFalse();
	}
	@Test void cliRejectsNonLocalProfile()throws Exception {
		// given
		Path output=cliOutput();
		// when / then
		assertThat(cli("prod",output)).isEqualTo(1);assertThat(Files.exists(output)).isFalse();
	}
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
	void rejectsSoftDeletedIndustryInBothVerificationModes(boolean rawSource) {
		// given
		String code=String.valueOf(90000+new Random().nextInt(9999));UUID id=masterSnapshot(false,code);masters.load(id);
		UUID run=UUID.fromString(jdbc.queryForObject("SELECT id FROM import_runs WHERE snapshot_id=?",String.class,id.toString()));while(!imports.status(run).snapshotState().equals("STAGED"))imports.stageNext(run);
		for(int n=0;n<4;n++)materializer.materialize(id,company,ImportPreflightService.offsetQuarter(20253,-n));
		var meta=reconciliation.metadata(id);var raw=new ImportPreflightResult(meta.manifest(),source.cells(),meta.companies(),meta.industries(),meta.issues(),List.of());assertThat(verification.verify(id).valid()).isTrue();
		// when
		jdbc.update("UPDATE industry_codes SET deleted_at=CURRENT_TIMESTAMP WHERE industry_code=?",code);
		try {
			// then
			assertThat((rawSource?verification.verify(id,raw):verification.verify(id)).valid()).isFalse();
		} finally {jdbc.update("UPDATE industry_codes SET deleted_at=NULL WHERE industry_code=?",code);}
	}
	@Test void cliRefusesOutputThroughSymlinkIntoSource()throws Exception {
		// given: 실제 아카이브 대신 Linux 임시 원본 경로를 사용한다.
		Path root=Files.createTempDirectory("d04-source-link-"),raw=Files.createDirectory(root.resolve("raw")),alias=Files.createSymbolicLink(root.resolve("alias"),raw),output=alias.resolve("new-output");
		// when / then
		assertThat(cliArguments("local",output,"import",raw.toString(),output.toString(),"20253","STRICT")).isEqualTo(1);
		assertThat(Files.exists(raw.resolve("new-output"))).isFalse();
	}
	private Path cliOutput()throws Exception {
		Path root=Path.of(System.getenv().getOrDefault("PORTFOLIO_D04_EVIDENCE",System.getProperty("java.io.tmpdir")));Files.createDirectories(root);return root.resolve("cli-"+UUID.randomUUID());
	}
	private int cli(String profile,Path output)throws Exception {
		return cliArguments(profile,output,"verify",snapshot.toString(),output.toString());
	}
	private int cliArguments(String profile,Path output,String... arguments)throws Exception {
		var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx256m","-cp",System.getProperty("sentinel.test.classpath"),"com.aivle.project.finance.internal.dataimport.cli.ImportCommand"));command.addAll(List.of(arguments));var builder=new ProcessBuilder(command);
		builder.environment().keySet().removeIf(k->!Set.of("PATH","HOME","USER","LOGNAME","LANG","LC_ALL","LC_CTYPE","JAVA_HOME","TMPDIR").contains(k));
		builder.environment().put("SPRING_PROFILES_ACTIVE",profile);builder.environment().put("PORTFOLIO_SECRET_DIR",com.aivle.project.support.PortfolioSecretPaths.directory().toString());
		Path log=cliOutput().resolveSibling("cli-process-"+UUID.randomUUID()+".log");var process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
		if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("D04 CLI timeout");}return process.exitValue();
	}
}
