package com.aivle.project.dataimport;

import static org.assertj.core.api.Assertions.*;
import com.aivle.project.dataimport.model.*;
import com.aivle.project.dataimport.model.CanonicalMetricCell.*;
import com.aivle.project.dataimport.model.ImportPreflightResult.*;
import com.aivle.project.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.dataimport.service.*;
import com.aivle.project.dataimport.repository.ImportJournalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.time.Duration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("realinfra")
@SpringBootTest(classes=ImportResumeIntegrationTest.Config.class, webEnvironment=SpringBootTest.WebEnvironment.NONE)
class ImportResumeIntegrationTest {
	@Configuration(proxyBeanMethods=false)
	@ImportAutoConfiguration({DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,TransactionAutoConfiguration.class,FlywayAutoConfiguration.class})
	@Import({ImportRunService.class,ImportChunkService.class,ImportInputValidator.class,ImportJournalRepository.class})
	static class Config { @Bean ObjectMapper mapper(){return new ObjectMapper();} }
	@DynamicPropertySource static void database(DynamicPropertyRegistry p) {
		p.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
		p.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
		p.add("spring.datasource.username",()->"sentinel_local");
		p.add("spring.datasource.password",()->{try{return Files.readString(com.aivle.project.support.PortfolioSecretPaths.file("mysql-app"));}catch(Exception e){throw new IllegalStateException("로컬 DB 준비 필요",e);}});
		p.add("spring.flyway.locations",()->"classpath:db/migration");
		p.add("spring.datasource.hikari.maximum-pool-size",()->6);
		p.add("spring.datasource.hikari.connection-init-sql",()->"SET SESSION innodb_lock_wait_timeout=1");
	}
	@Autowired ImportRunService service;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;
	@MockitoSpyBean ImportJournalRepository repository;

	@Test void cleanRunWritesNullZeroAndAllKeysOnce() throws Exception {
		// given
		try(var connection=dataSource.getConnection()){assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");}
		UUID run=service.start(command());
		// when
		for(int i=0;i<10;i++)service.stageNext(run);
		// then
		var status=service.status(run);
		assertThat(status.stagedCells()).isEqualTo(100);
		assertThat(status.committedChunks()).isEqualTo(10);
		assertThat(status.snapshotState()).isEqualTo("STAGED");
		assertThat(service.stageNext(run).writtenCells()).isZero();
		assertThat(checksum(status.snapshotId())).isEqualTo(commandChecksum(run));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_stage_values WHERE snapshot_id=? AND metric_value IS NULL",Long.class,status.snapshotId().toString())).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_stage_values WHERE snapshot_id=? AND metric_value=0",Long.class,status.snapshotId().toString())).isEqualTo(1);
	}
	@Test void repeatedUploadUsesSameSnapshotAndNewRunHistory() {
		// given
		var command=command();UUID a=service.start(command);
		for(int i=0;i<4;i++)service.stageNext(a);
		// when
		UUID b=service.start(command);
		for(int i=0;i<10;i++)service.stageNext(b);
		// then
		assertThat(b).isNotEqualTo(a);
		assertThat(service.status(b).snapshotId()).isEqualTo(service.status(a).snapshotId());
		assertThat(service.status(b).stagedCells()).isEqualTo(100);
		assertThat(service.status(b).committedChunks()).isEqualTo(10);
	}
	@Test void invalidInputDoesNotCreateRun() {
		// given
		var command=command();var r=command.input();
		var bad=new ImportPreflightResult(r.manifest(),r.cells(),r.companies(),r.industries(),List.of(new Issue("ERROR","INVALID_DECIMAL",r.cells().getFirst().provenance().getFirst(),"","NaN","오류 주입")),List.of());
		long before=jdbc.queryForObject("SELECT COUNT(*) FROM import_runs",Long.class);
		// when / then
		assertThatIllegalArgumentException().isThrownBy(()->service.start(new ImportStartCommand(bad,"test",10,QuarantinePolicy.STRICT)));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_runs",Long.class)).isEqualTo(before);
	}
	@ParameterizedTest(name="{displayName}[{index}] point={0}") @ValueSource(strings={"before","after"})
	void abruptJvmExitAtSixthCommitResumesFromDatabase(String point)throws Exception {
		// given: 처음 5묶음은 커밋하고 별도 JVM에서 6번째를 처리한다.
		UUID run=service.start(command());for(int i=0;i<5;i++)service.stageNext(run);
		// when: before는 값 insert 후/체크포인트 전, after는 transaction commit 후 응답 전 halt다.
		assertThat(probe(run,point)).isEqualTo(point.equals("before")?91:92);
		var interrupted=service.status(run);long expected=point.equals("before")?50:60;
		assertThat(interrupted.stagedCells()).isEqualTo(expected);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_stage_values WHERE snapshot_id=?",Long.class,interrupted.snapshotId().toString())).isEqualTo(expected);
		// then: 또 다른 새 JVM에는 원본 입력 없이 runId만 전달한다.
		assertThat(probe(run,"finish")).isZero();var resumed=service.status(run);
		assertThat(resumed.stagedCells()).isEqualTo(100);assertThat(resumed.committedChunks()).isEqualTo(10);
		assertThat(checksum(resumed.snapshotId())).isEqualTo(commandChecksum(run));
		evidence("crash-"+point,Map.of("runId",run.toString(),"interruptedRows",expected,"resumedRows",resumed.stagedCells(),"committedChunks",resumed.committedChunks(),"checksum",checksum(resumed.snapshotId())));
	}
	@Test void concurrentWorkersAndConcurrentUploadsConverge()throws Exception {
		// given
		var command=command();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
		try {
			var a=pool.submit(()->{start.await();return service.start(command);});var b=pool.submit(()->{start.await();return service.start(command);});start.countDown();
			UUID ra=a.get(30,TimeUnit.SECONDS),rb=b.get(30,TimeUnit.SECONDS);
			assertThat(service.status(ra).snapshotId()).isEqualTo(service.status(rb).snapshotId());
			// when
			var fa=pool.submit(()->{for(int i=0;i<15;i++)service.stageNext(ra);});var fb=pool.submit(()->{for(int i=0;i<15;i++)service.stageNext(rb);});
			fa.get(30,TimeUnit.SECONDS);fb.get(30,TimeUnit.SECONDS);
			// then
			var status=service.status(ra);assertThat(status.stagedCells()).isEqualTo(100);assertThat(status.committedChunks()).isEqualTo(10);
			assertThat(checksum(status.snapshotId())).isEqualTo(commandChecksum(ra));
			evidence("concurrency",Map.of("snapshotId",status.snapshotId().toString(),"runs",List.of(ra.toString(),rb.toString()),"stagedRows",100,"committedChunks",10));
		} finally {pool.shutdownNow();}
	}
	@Test void actualMysqlLockTimeoutRetriesInFreshTransaction()throws Exception {
		// given: 별도 connection이 snapshot row를 잠근다.
		UUID run=service.start(command());UUID snapshot=service.status(run).snapshotId();var pool=Executors.newSingleThreadExecutor();
		try(var blocker=dataSource.getConnection()) {
			blocker.setAutoCommit(false);try(var query=blocker.prepareStatement("SELECT id FROM data_snapshots WHERE id=? FOR UPDATE")){query.setString(1,snapshot.toString());query.executeQuery().close();}
			// when: MySQL의 실제 1205 오류가 첫 transaction을 실패시킨 뒤 잠금을 해제한다.
			var future=pool.submit(()->service.stageNext(run));long deadline=System.nanoTime()+Duration.ofSeconds(8).toNanos();
			while(service.status(run).retryCount()==0 && System.nanoTime()<deadline)Thread.sleep(20);
			assertThat(service.status(run).retryCount()).isGreaterThan(0);blocker.commit();future.get(10,TimeUnit.SECONDS);
			for(int i=0;i<10;i++)service.stageNext(run);
			// then
			assertThat(service.status(run).stagedCells()).isEqualTo(100);
			assertThat(checksum(snapshot)).isEqualTo(commandChecksum(run));
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_errors WHERE run_id=? AND disposition='RETRY' AND issue_json LIKE '%1205%'",Long.class,run.toString())).isGreaterThan(0);
			evidence("mysql-retry",Map.of("runId",run.toString(),"retryCount",service.status(run).retryCount(),"vendorCode",1205,"finalRows",100));
		} finally {pool.shutdownNow();}
	}
	@Test void permanentFailureRollsBackAndRequiresExplicitResume() {
		// given
		UUID run=service.start(command());doThrow(new DataIntegrityViolationException("injected non-transient failure")).when(repository).insertStage(anyList());
		// when / then
		assertThatThrownBy(()->service.stageNext(run)).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(service.status(run).stagedCells()).isZero();assertThat(service.status(run).committedChunks()).isZero();
		assertThat(service.status(run).retryCount()).isZero();assertThat(service.status(run).runState()).isEqualTo("FAILED");
		reset(repository);service.resume(run);for(int i=0;i<10;i++)service.stageNext(run);
		assertThat(service.status(run).stagedCells()).isEqualTo(100);
	}
	@Test void modifiedCanonicalPayloadAndChangedChunkSizeAreRejected() {
		// given
		var c=command();UUID run=service.start(c);var r=c.input();var cells=new ArrayList<>(r.cells());var first=cells.getFirst();cells.set(0,new CanonicalMetricCell(first.key(),new BigDecimal("99.0000"),first.provenance()));
		// when / then
		assertThatIllegalArgumentException().isThrownBy(()->service.start(new ImportStartCommand(new ImportPreflightResult(r.manifest(),cells,r.companies(),r.industries(),r.issues(),r.comparisons()),"test",10,QuarantinePolicy.STRICT)));
		assertThatIllegalArgumentException().isThrownBy(()->service.start(new ImportStartCommand(r,"test",20,QuarantinePolicy.STRICT)));
		assertThat(service.status(run).stagedCells()).isZero();
	}
	@Test void retryExhaustionStopsAndCanBeExplicitlyResumed()throws Exception {
		// given: 같은 row 잠금을 4회 시도 동안 유지한다.
		UUID run=service.start(command());UUID snapshot=service.status(run).snapshotId();var pool=Executors.newSingleThreadExecutor();
		try(var blocker=dataSource.getConnection()) {
			blocker.setAutoCommit(false);try(var query=blocker.prepareStatement("SELECT id FROM data_snapshots WHERE id=? FOR UPDATE")){query.setString(1,snapshot.toString());query.executeQuery().close();}
			// when
			var future=pool.submit(()->service.stageNext(run));assertThatThrownBy(()->future.get(12,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
			// then
			assertThat(service.status(run).retryCount()).isEqualTo(3);assertThat(service.status(run).runState()).isEqualTo("FAILED");
			assertThat(service.status(run).stagedCells()).isZero();blocker.commit();
			service.resume(run);for(int i=0;i<10;i++)service.stageNext(run);
			assertThat(service.status(run).stagedCells()).isEqualTo(100);
			evidence("retry-exhaustion",Map.of("runId",run.toString(),"retries",3,"attempts",4,"resumedRows",100));
		} finally {pool.shutdownNow();}
	}
	@Test void interruptedInputPreparationCanBeReuploadedWithoutDuplicatePlan() {
		// given: 준비 6번째 묶음에서 저장 전 예외를 주입한다.
		var original=command();String actor="prepare-"+UUID.randomUUID();var command=new ImportStartCommand(original.input(),actor,10,QuarantinePolicy.STRICT);
		doAnswer(call->{com.aivle.project.dataimport.entity.ImportChunkEntity c=call.getArgument(2);if(c.number()==6)throw new IllegalStateException("injected preparation failure");return call.callRealMethod();}).when(repository).plan(any(),any(),any());
		// when
		assertThatThrownBy(()->service.start(command)).isInstanceOf(IllegalStateException.class);
		UUID old=UUID.fromString(jdbc.queryForObject("SELECT id FROM import_runs WHERE submitted_by=?",String.class,actor));UUID snapshot=service.status(old).snapshotId();
		assertThat(repository.planCount(snapshot)).isEqualTo(5);assertThat(service.status(old).snapshotState()).isEqualTo("PREPARING");
		assertThatThrownBy(()->service.resume(old)).isInstanceOf(IllegalStateException.class);
		reset(repository);UUID next=service.start(command);service.resume(old);for(int i=0;i<10;i++)service.stageNext(next);
		// then
		assertThat(service.status(next).snapshotId()).isEqualTo(snapshot);assertThat(repository.planCount(snapshot)).isEqualTo(10);
		assertThat(service.status(next).stagedCells()).isEqualTo(100);assertThat(service.status(old).runState()).isEqualTo("COMPLETED");
	}
	@Test void corruptedDurablePayloadIsNotSilentlyLoaded() {
		// given
		UUID run=service.start(command());UUID snapshot=service.status(run).snapshotId();
		jdbc.update("UPDATE import_chunks SET payload_json='[]' WHERE snapshot_id=? AND chunk_number=1",snapshot.toString());
		// when / then
		assertThatThrownBy(()->service.stageNext(run)).isInstanceOf(IllegalStateException.class);
		assertThat(service.status(run).runState()).isEqualTo("FAILED");assertThat(service.status(run).stagedCells()).isZero();
	}
	@Test void transientInitialLookupIsAlsoRetried() {
		// given
		UUID run=service.start(command());var calls=new java.util.concurrent.atomic.AtomicInteger();
		doAnswer(call->{if(calls.getAndIncrement()==0)throw new TransientDataAccessResourceException("injected initial read outage");return call.callRealMethod();}).when(repository).run(run);
		// when
		service.stageNext(run);
		// then
		assertThat(service.status(run).stagedCells()).isEqualTo(10);assertThat(service.status(run).retryCount()).isEqualTo(1);
	}
	@Test void exhaustedAuditFailureKeepsOriginalCauseAndSignalsUnrecordedRecovery() {
		// given
		UUID run=service.start(command());var original=new DataIntegrityViolationException("injected stage failure");
		doThrow(original).when(repository).insertStage(anyList());
		doThrow(new TransientDataAccessResourceException("injected audit outage")).when(repository).error(any());
		// when / then: FAILED가 기록됐다고 주장하지 않으며 원인과 감사 오류를 모두 보존한다.
		assertThatThrownBy(()->service.stageNext(run)).isInstanceOf(ImportRecoveryRequiredException.class).hasCause(original).satisfies(e->assertThat(e.getSuppressed()).isNotEmpty());
		reset(repository);assertThat(service.status(run).stagedCells()).isZero();service.resume(run);for(int i=0;i<10;i++)service.stageNext(run);
		assertThat(service.status(run).stagedCells()).isEqualTo(100);
	}
	@Test void failureAuditAndSameRunWorkerCannotOverwriteFailedState()throws Exception {
		// given: 실패 표기 직전과 경쟁 호출의 commit 직전에 barrier를 둔다.
		UUID run=service.start(command());var auditEntered=new CountDownLatch(1);var committing=new CountDownLatch(1);var allowFailure=new CountDownLatch(1);var allowCommit=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
		doAnswer(call->{if(Thread.currentThread().getName().equals("import-failing"))throw new DataIntegrityViolationException("injected competing failure");return call.callRealMethod();}).when(repository).insertStage(anyList());
		doAnswer(call->{auditEntered.countDown();if(!allowFailure.await(8,TimeUnit.SECONDS))throw new AssertionError("실패 barrier timeout");return call.callRealMethod();}).when(repository).failed(eq(run),anyString());
		doAnswer(call->{committing.countDown();if(!allowCommit.await(8,TimeUnit.SECONDS))throw new AssertionError("commit barrier timeout");return call.callRealMethod();}).when(repository).commitChunk(any(),anyInt(),any(),anyInt());
		try {
			// when
			var failing=pool.submit(()->{Thread.currentThread().setName("import-failing");return service.stageNext(run);});
			assertThat(auditEntered.await(5,TimeUnit.SECONDS)).isTrue();var competing=pool.submit(()->service.stageNext(run));
			committing.await(300,TimeUnit.MILLISECONDS);allowFailure.countDown();
			assertThatThrownBy(()->failing.get(12,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
			allowCommit.countDown();
			try{competing.get(12,TimeUnit.SECONDS);}catch(ExecutionException expected){/* FAILED 후 호출은 거부될 수 있다. */}
			// then
			assertThat(service.status(run).runState()).isEqualTo("FAILED");assertThat(service.status(run).stagedCells()).isZero();
		} finally {allowFailure.countDown();allowCommit.countDown();pool.shutdownNow();}
	}
	private int probe(UUID run,String mode)throws Exception {
		Path directory=Path.of(System.getenv().getOrDefault("PORTFOLIO_D02_EVIDENCE",System.getProperty("java.io.tmpdir")));Files.createDirectories(directory);
		var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx256m","-cp",System.getProperty("sentinel.test.classpath"),ImportCrashProbe.class.getName(),run.toString(),mode).redirectErrorStream(true).redirectOutput(directory.resolve("probe-"+run+"-"+mode+".log").toFile()).start();
		if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("별도 JVM timeout");}return process.exitValue();
	}
	private void evidence(String name,Object value)throws Exception {
		String destination=System.getenv("PORTFOLIO_D02_EVIDENCE");if(destination!=null)Files.writeString(Path.of(destination,name+".json"),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value));
	}

	private ImportStartCommand command() {
		List<CanonicalMetricCell> cells=new ArrayList<>();String sourceHash=ImportPreflightService.sha256(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		for(int i=0;i<100;i++)cells.add(new CanonicalMetricCell(new Key(String.format("%06d",i+1),20253,"ROA","ACTUAL"),i==0?null:BigDecimal.valueOf(i-1).setScale(4),List.of(new Provenance("METRIC13",sourceHash,"fixture",i+2,3))));
		var sources=List.of(new ImportManifest.SourceManifest("METRIC13","fixture.xlsx",sourceHash,100,"OOXML","fixture",100));
		String fingerprint=ImportPreflightService.sha256(("20253|preflight-v1|decimal20-4-half-up-v1\nMETRIC13|"+sourceHash+"|OOXML\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
		var manifest=new ImportManifest(fingerprint,20253,"preflight-v1","decimal20-4-half-up-v1",ImportPreflightService.checksum(cells),sources,Map.of("canonicalCells",100L));
		return new ImportStartCommand(new ImportPreflightResult(manifest,cells,List.of(),List.of(),List.of(),List.of()),"integration-test",10,QuarantinePolicy.STRICT);
	}
	private String commandChecksum(UUID run) {return jdbc.queryForObject("SELECT canonical_checksum FROM data_snapshots WHERE id=?",String.class,service.status(run).snapshotId().toString());}
	private String checksum(UUID snapshot) {
		List<CanonicalMetricCell> cells=jdbc.query("SELECT * FROM import_stage_values WHERE snapshot_id=? ORDER BY stock_code,quarter_key,metric_code,value_type",(r,n)->new CanonicalMetricCell(new Key(r.getString("stock_code"),r.getInt("quarter_key"),r.getString("metric_code"),r.getString("value_type")),r.getBigDecimal("metric_value"),List.of()),snapshot.toString());
		return ImportPreflightService.checksum(cells);
	}
}
