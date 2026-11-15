package com.aivle.project.dataimport;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.api.CanonicalMetricCell;
import com.aivle.project.finance.api.CanonicalMetricCell.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.finance.internal.dataimport.service.*;
import com.aivle.project.finance.internal.report.repository.CompanyReportMetricValuesRepository;
import com.aivle.project.finance.internal.dataimport.repository.SnapshotReportRepository;
import com.aivle.project.finance.internal.company.service.CompanyOverviewService;
import com.aivle.project.app.internal.dashboard.service.DashboardSummaryService;
import com.aivle.project.watchlist.internal.repository.CompanyWatchlistRepository;
import com.aivle.project.finance.api.MetricValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.nio.file.*;
import com.aivle.project.support.PortfolioIntegrationSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
class SnapshotVisibilityIntegrationTest extends PortfolioIntegrationSupport {
	@Autowired ImportRunService imports;
	@Autowired SnapshotMaterializationService materializer;
	@Autowired SnapshotActivationService activation;
	@Autowired CompanyReportMetricValuesRepository values;
	@Autowired JdbcTemplate jdbc;
	@Autowired CompanyOverviewService overview;
	@Autowired DashboardSummaryService dashboard;
	@Autowired CompanyWatchlistRepository watchlists;
	@Autowired ObjectMapper mapper;
	@Autowired com.aivle.project.finance.internal.metricaverage.service.MetricAverageCalculationService averages;
	@Autowired com.aivle.project.finance.internal.risk.service.RiskScoreCalculationService riskCalculation;
	@MockitoSpyBean com.aivle.project.finance.internal.metricaverage.repository.MetricAverageRepository averageStore;
	@MockitoSpyBean SnapshotReportRepository reportStore;
	@Autowired com.aivle.project.finance.internal.report.service.CompanyReportMetricImportService reportImport;
	private UUID original;
	private final List<UUID> owned=new ArrayList<>();
	private final List<Long> companies=new ArrayList<>();
	private final Map<Long,String> stocks=new LinkedHashMap<>();
	private final Map<UUID,ImportStartCommand> acceptedInputs=new HashMap<>();
	static final List<String> CODES=List.of("ROA","ROE","OpMargin","DbRatio","EqRatio","CapImpRatio","STDebtRatio","CurRatio","QkRatio","CurLibRatio","CFO_AsRatio","CFO_Sale","CFO_GR","ROA_IndRel","DbRatio_IndRel","STDebtRatio_IndRel","CurRatio_IndRel","CFO_AsRatio_IndRel");
	@BeforeEach void prepare() {
		try{original=activation.resolveActiveOrLegacy("FINANCIAL").orElse(null);}catch(UnsupportedOperationException absent){original=null;}
		for(int i=0;i<2;i++) {
			// 반복 실행에서 누적한 합성 종목과 충돌하면 새 키를 생성한다. 기존 행은 보존한다.
			for(int attempt=0;attempt<20;attempt++){
			String stock="Z"+UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase(Locale.ROOT);
			String corp=String.format("%08d",new java.util.Random().nextInt(90000000)+10000000);
			try{jdbc.update("INSERT INTO companies(corp_code,corp_name,stock_code) VALUES(?,?,?)",corp,"snapshot-fixture-"+stock,stock);}
			catch(org.springframework.dao.DuplicateKeyException collision){if(attempt==19)throw collision;continue;}
			Long id=jdbc.queryForObject("SELECT id FROM companies WHERE stock_code=?",Long.class,stock);companies.add(id);stocks.put(id,stock);
			break;
			}
		}
	}
	@AfterEach void restorePointer() {
		for(UUID id:owned)try{jdbc.update("UPDATE active_data_snapshots SET snapshot_id=? WHERE family='FINANCIAL' AND snapshot_id=?",original==null?null:original.toString(),id.toString());}catch(org.springframework.jdbc.BadSqlGrammarException absent){/* RED 단계 V29 부재 */}
	}
	@Test void partialNewSnapshotDoesNotChangeCurrentValuesOrLatestQuarter() throws Exception {
		// given
		UUID a=staged(20253,100,false,18);all(a,20253);activation.verify(a);activation.activate(a);
		String stock=stocks.get(companies.getFirst());var before=values.findLatestMetricsByStockCodeAndQuarterRange(stock,20244,20254);
		long user=watchlistUser();String beforeOverview=mapper.writeValueAsString(overview.getOverview(companies.getFirst(),null));var beforeDashboard=dashboard.getSummary(user);
		var beforeWatchlist=watchlists.findWatchlistMetricValues(user,(short)2025,(byte)3,MetricValueType.ACTUAL).stream().map(r->r.getMetricCode()+":"+r.getMetricValue()).toList();
		// when: 미래 분기를 가진 B의 한 기업/분기만 materialize한다.
		UUID b=staged(20254,200,false,18);materializer.materialize(b,companies.getFirst(),20254);
		// then
		assertThat(values.findMaxActualQuarterKeyByStockCode(stock)).contains(20253);
		assertThat(values.findMaxActualQuarterKeyByCompanyId(companies.getFirst())).contains(20253);
		assertThat(values.findMaxActualQuarterKeyByStockCodes(List.of(stock))).contains(20253);
		assertThat(values.findLatestMetricsByStockCodeAndQuarterRange(stock,20244,20254)).extracting(r->r.getQuarterKey()+":"+r.getMetricCode()+":"+r.getMetricValue()).containsExactlyElementsOf(before.stream().map(r->r.getQuarterKey()+":"+r.getMetricCode()+":"+r.getMetricValue()).toList());
		assertThatThrownBy(()->activation.verify(b)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(()->activation.activate(b)).isInstanceOf(IllegalStateException.class);
		assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(a);
		assertThat(mapper.writeValueAsString(overview.getOverview(companies.getFirst(),null))).isEqualTo(beforeOverview);
		assertThat(dashboard.getSummary(user)).isEqualTo(beforeDashboard);
		assertThat(watchlists.findWatchlistMetricValues(user,(short)2025,(byte)3,MetricValueType.ACTUAL).stream().map(r->r.getMetricCode()+":"+r.getMetricValue()).toList()).containsExactlyElementsOf(beforeWatchlist);
		evidence("partial-visibility",Map.of("active",a,"pending",b,"visibleLatestQuarter",values.findMaxActualQuarterKeyByCompanyId(companies.getFirst()).orElseThrow(),"pendingQuarter",20254,"overviewUnchanged",true,"dashboardUnchanged",true,"watchlistUnchanged",true));
	}
	@Test void verifiedSwitchKeepsExplicitOldSnapshotAndCombinesAll18Codes() {
		// given
		UUID a=staged(20253,100,false,18);all(a,20253);activation.verify(a);activation.activate(a);
		var old=activation.load(a,companies.getFirst(),20244,20253);
		UUID b=staged(20253,200,true,18);all(b,20253);activation.verify(b);
		// when
		activation.activate(b);
		// then
		assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(b);
		assertThat(activation.load(a,companies.getFirst(),20244,20253)).isEqualTo(old);
		assertThat(activation.load(b,companies.getFirst(),20244,20253)).hasSize(72).extracting(c->c.key().metricCode()).containsAll(CODES);
		assertThat(activation.load(b,companies.getFirst(),20244,20253).stream().filter(c->c.key().metricCode().equals("ROA")).findFirst().orElseThrow().value()).isGreaterThan(new BigDecimal("199"));
		long version=materializer.materialize(b,companies.getFirst(),20253);
		assertThat(materializer.materialize(b,companies.getFirst(),20253)).isEqualTo(version);
		evidence("verified-switch",Map.of("previous",a,"active",b,"stocks",stocks.values(),"rowsPerCompany",old.size(),"previousReadUnchanged",true,"codes",CODES));
	}
	@Test void missingRelativeMetricsCannotBeVerifiedOrActivated() {
		// given
		UUID incomplete=staged(20253,100,false,13);all(incomplete,20253);
		// when / then
		assertThatThrownBy(()->activation.verify(incomplete)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(()->activation.activate(incomplete)).isInstanceOf(IllegalStateException.class);
	}
	@Test void concurrentSnapshotsDoNotAllocateSameGlobalReportVersion()throws Exception {
		// given: 서로 다른 snapshot이 기존 보고서의 같은 분기를 동시에 materialize한다.
		UUID a=staged(20253,100,false,18),b=staged(20253,200,false,18);var barrier=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
		doAnswer(call->{barrier.countDown();if(!barrier.await(5,TimeUnit.SECONDS))throw new AssertionError("stock read barrier timeout");return call.callRealMethod();}).when(reportStore).stock(companies.getFirst());
		try {
			// when
			var fa=pool.submit(()->materializer.materialize(a,companies.getFirst(),20253));var fb=pool.submit(()->materializer.materialize(b,companies.getFirst(),20253));
			long va=fa.get(30,TimeUnit.SECONDS),vb=fb.get(30,TimeUnit.SECONDS);
			// then
			assertThat(va).isNotEqualTo(vb);assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT version_no) FROM company_report_versions WHERE id IN (?,?)",Integer.class,va,vb)).isEqualTo(2);
		} finally {pool.shutdownNow();}
	}
	@Test void activationRollbackKeepsPreviouslyActivePointer() {
		// given
		UUID a=staged(20253,100,false,18);all(a,20253);activation.verify(a);activation.activate(a);
		UUID b=staged(20253,200,false,18);all(b,20253);activation.verify(b);
		doAnswer(call->{call.callRealMethod();throw new IllegalStateException("injected failure after pointer update");}).when(reportStore).switchPointer("FINANCIAL",b);
		// when / then
		assertThatThrownBy(()->activation.activate(b)).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class).hasRootCauseMessage("injected failure after pointer update");
		assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(a);
	}
	@Test void changedMaterializedValueCannotBeActivated() {
		// given
		UUID b=staged(20253,200,false,18);all(b,20253);activation.verify(b);long version=materializer.materialize(b,companies.getFirst(),20253);
		jdbc.update("UPDATE company_report_metric_values SET metric_value=999 WHERE report_version_id=?",version);
		// when / then
		assertThatThrownBy(()->activation.activate(b)).isInstanceOf(IllegalStateException.class);
		assertThat(activation.resolveActiveOrLegacy("FINANCIAL").orElse(null)).isNotEqualTo(b);
	}
	@Test void activationJvmExitBeforeCommitKeepsOldPointer()throws Exception {
		// given
		UUID a=staged(20253,100,false,18);all(a,20253);activation.verify(a);activation.activate(a);
		UUID b=staged(20254,200,false,18);all(b,20254);activation.verify(b);
		long generation=jdbc.queryForObject("SELECT generation FROM active_data_snapshots WHERE family='FINANCIAL'",Long.class);
		Path directory=Path.of(System.getenv().getOrDefault("PORTFOLIO_D03_EVIDENCE",System.getProperty("java.io.tmpdir")));Files.createDirectories(directory);
		// when: child가 pointer UPDATE 이후 transaction commit 전에 halt한다.
		var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx256m","-cp",System.getProperty("sentinel.test.classpath"),SnapshotActivationCrashProbe.class.getName(),b.toString()).redirectErrorStream(true).redirectOutput(directory.resolve("activation-crash-"+b+".log").toFile()).start();
		if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("activation probe timeout");}
		// then
		assertThat(process.exitValue()).isEqualTo(94);assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(a);
		assertThat(jdbc.queryForObject("SELECT generation FROM active_data_snapshots WHERE family='FINANCIAL'",Long.class)).isEqualTo(generation);
		activation.activate(b);assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(b);
		Files.writeString(directory.resolve("activation-crash.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("previous",a.toString(),"candidate",b.toString(),"haltCode",94,"generationBefore",generation,"afterRetry",b.toString(),"materializedRows",144)));
	}
	@Test void newImportEntryVerifiesAndPublishesIdempotently() {
		// given
		UUID id=staged(20253,100,false,18);
		// when
		assertThat(reportImport.importSnapshot(id)).isEqualTo(id);reportImport.importSnapshot(id);
		// then
		assertThat(activation.resolveActiveOrLegacy("FINANCIAL")).contains(id);
		assertThat(activation.load(id,companies.getFirst(),20244,20253)).hasSize(72);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM snapshot_report_versions WHERE snapshot_id=?",Long.class,id.toString())).isEqualTo(8);
	}
	@Test void reuploadAfterVerificationIsStillCompletedAndDoesNotReopenSnapshot() {
		// given
		UUID id=staged(20253,100,false,18);reportImport.importSnapshot(id);
		// when
		UUID replay=imports.start(acceptedInputs.get(id));
		// then
		assertThat(imports.status(replay).snapshotState()).isEqualTo("VERIFIED");
		assertThat(imports.status(replay).runState()).isEqualTo("COMPLETED");
		assertThat(imports.stageNext(replay).complete()).isTrue();imports.resume(replay);
		assertThat(imports.status(replay).runState()).isEqualTo("COMPLETED");
	}
	@Test void currentDashboardMustNotMixOldSnapshotAveragesOrRiskSummaries() {
		// given: A에서 파생 집계를 생성한 뒤 B만 활성화한다.
		UUID a=staged(20253,100,false,18);reportImport.importSnapshot(a);long user=watchlistUser(),quarter=reportStore.quarter(20253);
		averages.calculateAndUpsertByQuarter(quarter);
		long va=materializer.materialize(a,companies.getFirst(),20253);riskCalculation.calculateAndUpsert(companies.getFirst(),quarter,va);
		UUID b=staged(20253,200,false,18);reportImport.importSnapshot(b);
		// when / then: 출처가 다른 기존 평균은 NULL이며 B로 재계산한 뒤에만 노출된다.
		var before=watchlists.findDashboardMetrics(user,(short)2025,(byte)3,MetricValueType.ACTUAL,List.of("ROA"),false);
		assertThat(before).hasSize(2).allMatch(r->r.metricValue().compareTo(new BigDecimal("200"))==0 && r.marketAvg()==null);
		averages.calculateAndInsertMissingByQuarter(quarter);
		assertThat(watchlists.findDashboardMetrics(user,(short)2025,(byte)3,MetricValueType.ACTUAL,List.of("ROA"),false)).allMatch(r->r.marketAvg()!=null && r.marketAvg().compareTo(new BigDecimal("200"))==0);
		long vb=materializer.materialize(b,companies.getFirst(),20253);var score=riskCalculation.calculateAndUpsert(companies.getFirst(),quarter,vb);
		assertThat(watchlists.findDashboardRisks(user,quarter,null)).singleElement().satisfies(r->assertThat(r.riskScore()).isEqualByComparingTo(score.riskScore()));
	}
	@Test void delayedExplicitRiskVersionRecalculationKeepsOldVerifiedValues() {
		// given: A target이 선택된 뒤 B로 전환된 상황을 재현한다.
		UUID a=staged(20253,100,false,18);reportImport.importSnapshot(a);long q=reportStore.quarter(20253),version=materializer.materialize(a,companies.getFirst(),20253);
		var first=riskCalculation.calculateAndUpsert(companies.getFirst(),q,version);
		UUID b=staged(20253,200,false,18);reportImport.importSnapshot(b);
		// when
		var late=riskCalculation.calculateAndUpsert(companies.getFirst(),q,version);
		// then
		assertThat(late.riskMetricsCount()).isEqualTo(5);assertThat(late.riskMetricsAvg()).isEqualByComparingTo(first.riskMetricsAvg());
	}
	@Test void currentRiskRowsExcludePreviousSnapshotSummary() {
		// given
		UUID a=staged(20253,100,false,18);reportImport.importSnapshot(a);long user=watchlistUser(),q=reportStore.quarter(20253);
		riskCalculation.calculateAndUpsert(companies.getFirst(),q,materializer.materialize(a,companies.getFirst(),20253));
		UUID b=staged(20253,200,false,18);reportImport.importSnapshot(b);var score=riskCalculation.calculateAndUpsert(companies.getFirst(),q,materializer.materialize(b,companies.getFirst(),20253));
		// when / then
		assertThat(watchlists.findDashboardRisks(user,q,null)).singleElement().satisfies(r->assertThat(r.riskScore()).isEqualByComparingTo(score.riskScore()));
	}
	@Test void lateOldAverageWriteIsHiddenUntilCurrentSourceIsRecalculated()throws Exception {
		// given: A의 입력을 읽은 집계가 멈춘 동안 B 전환/집계를 완료한다.
		UUID a=staged(20253,100,false,18);reportImport.importSnapshot(a);long user=watchlistUser(),q=reportStore.quarter(20253);
		UUID b=staged(20253,200,false,18);all(b,20253);activation.verify(b);
		var paused=new CountDownLatch(1);var release=new CountDownLatch(1);var once=new java.util.concurrent.atomic.AtomicBoolean();var pool=Executors.newSingleThreadExecutor();
		// Spring Data 인터페이스 spy는 실제 프록시로 위임하는 기본 answer를 유지한다.
		var repositoryAnswer=mockingDetails(averageStore).getMockCreationSettings().getDefaultAnswer();
		doAnswer(call->{if(Thread.currentThread().getName().equals("late-average-A")&&once.compareAndSet(false,true)){paused.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("late average barrier timeout");}return repositoryAnswer.answer(call);}).when(averageStore).findAnyByQuarterIdAndMetricIdForUpdate(anyLong(),anyLong());
		try {
			var old=pool.submit(()->{Thread.currentThread().setName("late-average-A");return averages.calculateAndUpsertByQuarter(q);});assertThat(paused.await(5,TimeUnit.SECONDS)).isTrue();
			// when
			activation.activate(b);averages.calculateAndUpsertByQuarter(q);release.countDown();old.get(20,TimeUnit.SECONDS);
			// then: 오래된 A가 늦게 써도 B의 평균으로 오인해 반환하지 않는다.
			assertThat(watchlists.findDashboardMetrics(user,(short)2025,(byte)3,MetricValueType.ACTUAL,List.of("ROA"),false)).allMatch(r->r.marketAvg()==null);
			averages.calculateAndInsertMissingByQuarter(q);
			assertThat(watchlists.findDashboardMetrics(user,(short)2025,(byte)3,MetricValueType.ACTUAL,List.of("ROA"),false)).allMatch(r->r.marketAvg().compareTo(new BigDecimal("200"))==0);
			evidence("late-average",Map.of("previous",a,"active",b,"oldSourceHidden",true,"recalculatedCurrentMean",200));
		} finally {release.countDown();pool.shutdownNow();}
	}
	@Test void legacyFallbackAndPredictedValuesRemainSeparateFromActiveActual() {
		// given: legacy 조회 경계와 예측 값의 보존을 직접 확인한다.
		jdbc.update("UPDATE active_data_snapshots SET snapshot_id=NULL WHERE family='FINANCIAL'");
		long q=reportStore.quarter(20253);jdbc.update("INSERT INTO company_reports(company_id,quarter_id) VALUES(?,?)",companies.getFirst(),q);
		long report=jdbc.queryForObject("SELECT id FROM company_reports WHERE company_id=? AND quarter_id=?",Long.class,companies.getFirst(),q);
		jdbc.update("INSERT INTO company_report_versions(company_report_id,version_no,generated_at,is_published) VALUES(?,1,CURRENT_TIMESTAMP,0)",report);
		long version=jdbc.queryForObject("SELECT id FROM company_report_versions WHERE company_report_id=?",Long.class,report),metric=jdbc.queryForObject("SELECT id FROM metrics WHERE metric_code='ROA'",Long.class);
		jdbc.update("INSERT INTO company_report_metric_values(report_version_id,metric_id,quarter_id,metric_value,value_type) VALUES(?,?,?,?,?)",version,metric,q,new BigDecimal("7"),"ACTUAL");
		jdbc.update("INSERT INTO company_report_metric_values(report_version_id,metric_id,quarter_id,metric_value,value_type) VALUES(?,?,?,?,?)",version,metric,q,new BigDecimal("9"),"PREDICTED");
		assertThat(values.findMaxActualQuarterKeyByCompanyId(companies.getFirst())).contains(20253);
		UUID pending=staged(20254,300,false,18);materializer.materialize(pending,companies.getFirst(),20254);
		assertThat(values.findMaxActualQuarterKeyByCompanyId(companies.getFirst())).contains(20253);
		UUID active=staged(20253,100,false,18);all(active,20253);activation.verify(active);activation.activate(active);
		// when / then
		var rows=values.findLatestMetricsByStockCodeAndQuarterRange(stocks.get(companies.getFirst()),20253,20253);
		assertThat(rows).anyMatch(r->r.getValueType()==MetricValueType.ACTUAL && r.getMetricCode().equals("ROA") && r.getMetricValue().compareTo(new BigDecimal("100"))==0);
		assertThat(rows).anyMatch(r->r.getValueType()==MetricValueType.PREDICTED && r.getMetricCode().equals("ROA") && r.getMetricValue().compareTo(new BigDecimal("9"))==0);
		assertThat(rows).noneMatch(r->r.getValueType()==MetricValueType.ACTUAL && r.getMetricValue().compareTo(new BigDecimal("7"))==0);
	}
	private void evidence(String name,Object value) {
		String directory=System.getenv("PORTFOLIO_D03_EVIDENCE");if(directory==null)return;
		try {Files.writeString(Path.of(directory,name+".json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));}
		catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
	}
	private long watchlistUser() {
		String email="snapshot-"+UUID.randomUUID()+"@example.invalid";
		jdbc.update("INSERT INTO users(uuid,email,password,name,status) VALUES(?,?,?,?,?)",UUID.randomUUID().toString().substring(0,16).getBytes(StandardCharsets.US_ASCII),email,UUID.randomUUID().toString(),"snapshot fixture","ACTIVE");
		long user=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
		for(Long company:companies)jdbc.update("INSERT INTO company_watchlists(user_id,company_id) VALUES(?,?)",user,company);return user;
	}
	private void all(UUID id,int base) {for(Long c:companies)for(int n=0;n<4;n++)materializer.materialize(id,c,ImportPreflightService.offsetQuarter(base,-n));}
	private UUID staged(int base,int amount,boolean reverse,int codeCount) {
		String hash=ImportPreflightService.sha256(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));List<CanonicalMetricCell> cells=new ArrayList<>();
		for(Long company:companies)for(int n=0;n<4;n++)for(int m=0;m<codeCount;m++)cells.add(new CanonicalMetricCell(new Key(stocks.get(company),ImportPreflightService.offsetQuarter(base,-n),CODES.get(m),"ACTUAL"),BigDecimal.valueOf(amount+m+n).setScale(4),List.of(new Provenance("METRIC13",hash,"fixture",cells.size()+2,3))));
		if(reverse)Collections.reverse(cells);
		var sources=List.of(new ImportManifest.SourceManifest("METRIC13","snapshot-fixture.xlsx",hash,100,"OOXML","fixture",cells.size()));
		String identity=base+"|preflight-v1|decimal20-4-half-up-v1\nMETRIC13|"+hash+"|OOXML\n";
		var manifest=new ImportManifest(ImportPreflightService.sha256(identity.getBytes(StandardCharsets.UTF_8)),base,"preflight-v1","decimal20-4-half-up-v1",ImportPreflightService.checksum(cells),sources,Map.of("canonicalCells",(long)cells.size()));
		var command=new ImportStartCommand(new ImportPreflightResult(manifest,cells,List.of(),List.of(),List.of(),List.of()),"snapshot-fixture",10,QuarantinePolicy.STRICT);
		UUID run=imports.start(command);
		while(!imports.status(run).snapshotState().equals("STAGED"))imports.stageNext(run);
		UUID snapshot=imports.status(run).snapshotId();owned.add(snapshot);acceptedInputs.put(snapshot,command);return snapshot;
	}
}
