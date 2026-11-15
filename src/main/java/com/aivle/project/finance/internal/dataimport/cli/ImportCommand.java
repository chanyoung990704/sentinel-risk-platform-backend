package com.aivle.project.finance.internal.dataimport.cli;

import com.aivle.project.finance.internal.dataimport.model.*;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand;
import com.aivle.project.finance.internal.dataimport.model.ImportStartCommand.QuarantinePolicy;
import com.aivle.project.finance.internal.dataimport.repository.*;
import com.aivle.project.finance.internal.dataimport.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** local 전용 DB에서 서버·외부 AI 없이 실행하는 별도 JVM 명령. */
public final class ImportCommand {
	private static final String LOCAL_URL="jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
	@Configuration(proxyBeanMethods=false) @EnableTransactionManagement @Profile("local")
	static class Transactions { }
	public static void main(String[] args) {
		try {System.exit(execute(args));}
		catch(Exception failure) {System.err.println("D04 실패: "+failure.getClass().getSimpleName()+": "+failure.getMessage());System.exit(1);}
	}
	static int execute(String[] args)throws Exception {
		if(!"local".equals(System.getenv("SPRING_PROFILES_ACTIVE")))throw new IllegalArgumentException("local 프로필만 지원");
		if(System.getenv("SPRING_DATASOURCE_URL")!=null && !LOCAL_URL.equals(System.getenv("SPRING_DATASOURCE_URL")))throw new IllegalArgumentException("전용 local DB만 지원");
		if(args.length<3 || !Set.of("import","resume","verify").contains(args[0]) || (args[0].equals("import")?args.length!=5:args[0].equals("resume")?args.length!=3:args.length>4))throw new IllegalArgumentException("import sourceDir outputDir baseQuarter policy | resume runId outputDir | verify snapshotId outputDir [sourceDir]");
		String action=args[0];Path source=action.equals("import")?Path.of(args[1]).toRealPath():args.length==4?Path.of(args[3]).toRealPath():null;
		Path output=Path.of(args[2]).toAbsolutePath().normalize();
		// 아직 없는 하위 경로는 남기고 기존 상위 경로의 링크를 실제 위치로 해석한다.
		Path existing=output;while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS))existing=existing.getParent();
		output=existing.toRealPath().resolve(existing.relativize(output)).normalize();
		if(source!=null && output.startsWith(source))throw new IllegalArgumentException("원본 내부에 결과를 쓸 수 없음");
		Files.createDirectories(output.getParent());Files.createDirectory(output);
		ObjectMapper mapper=new ObjectMapper();Map<String,Long> elapsed=new LinkedHashMap<>();
		HikariConfig config=new HikariConfig();config.setJdbcUrl(LOCAL_URL);config.setUsername("sentinel_local");
		Path secrets=Path.of(Objects.requireNonNull(System.getenv("PORTFOLIO_SECRET_DIR"),"PORTFOLIO_SECRET_DIR 필요"));
		config.setPassword(Files.readString(secrets.resolve("mysql-app")));config.setMaximumPoolSize(4);
		try(var dataSource=new HikariDataSource(config);var context=new AnnotationConfigApplicationContext()) {
			Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
			context.getEnvironment().setActiveProfiles("local");context.registerBean(JdbcTemplate.class,()->new JdbcTemplate(dataSource));context.registerBean(ObjectMapper.class,()->mapper);
			context.registerBean(PlatformTransactionManager.class,()->new JdbcTransactionManager(dataSource));
			context.register(Transactions.class,ImportJournalRepository.class,SnapshotReportRepository.class,ImportReconciliationRepository.class,ImportMasterRepository.class,ImportInputValidator.class,ImportChunkService.class,ImportRunService.class,ImportMasterService.class,SnapshotMaterializationService.class,SnapshotVerificationService.class,SnapshotActivationService.class,ImportVerificationService.class);context.refresh();
			var runs=context.getBean(ImportRunService.class);var snapshots=context.getBean(SnapshotReportRepository.class);var verification=context.getBean(ImportVerificationService.class);
			if(action.equals("verify")) {
				UUID id=UUID.fromString(args[1]);long start=System.nanoTime();
				ImportPreflightResult input=source==null?null:new ImportPreflightService().preflight(ImportPreflightCommand.dataset(source,((Number)snapshots.metadata(id).get("base_quarter_key")).intValue()));
				var result=input==null?verification.verify(id):verification.verify(id,input);elapsed.put("verifyMs",millis(start));write(mapper,output,"verification",result);
				if(!result.valid())return 2;write(mapper,output,"complete",Map.of("action",action,"snapshotId",id,"basis",result.basis(),"elapsed",elapsed,"finishedAt",Instant.now().toString()));return 0;
			}
			UUID run;ImportPreflightResult input=null;long start=System.nanoTime();
			if(action.equals("import")) {
				input=new ImportPreflightService().preflight(ImportPreflightCommand.dataset(source,Integer.parseInt(args[3])));elapsed.put("preflightMs",millis(start));ImportPreflightCommand.writeResult(input,output.resolve("preflight"));
				start=System.nanoTime();run=runs.start(new ImportStartCommand(input,"local-cli",500,QuarantinePolicy.valueOf(args[4])));elapsed.put("prepareMs",millis(start));
			} else {run=UUID.fromString(args[1]);runs.resume(run);}
			var initial=runs.status(run);UUID id=initial.snapshotId();write(mapper,output,"accepted",Map.of("status",initial,"startedAt",Instant.now().toString(),"elapsed",elapsed));
			start=System.nanoTime();context.getBean(ImportMasterService.class).load(id);elapsed.put("masterMs",millis(start));
			start=System.nanoTime();
			while(true) {var result=runs.stageNext(run);progress(mapper,output,Map.of("phase","stage","status",runs.status(run),"chunk",result.chunkNumber()));if(result.complete())break;}
			elapsed.put("stageMs",millis(start));start=System.nanoTime();
			var targets=snapshots.targets(id);int completed=0;var materializer=context.getBean(SnapshotMaterializationService.class);
			for(var target:targets) {
				if(target.companyId()==null)throw new IllegalStateException("기업 마스터 연결 없음");materializer.materialize(id,target.companyId(),target.quarterKey());completed++;
				if(completed%100==0 || completed==targets.size())progress(mapper,output,Map.of("phase","materialize","completedGroups",completed,"totalGroups",targets.size()));
			}
			elapsed.put("materializeMs",millis(start));start=System.nanoTime();
			var result=input==null?verification.verify(id):verification.verify(id,input);elapsed.put("verifyMs",millis(start));write(mapper,output,"verification",result);if(!result.valid())return 2;
			start=System.nanoTime();var activation=context.getBean(SnapshotActivationService.class);activation.verify(id);activation.activate(id);elapsed.put("activateMs",millis(start));
			write(mapper,output,"complete",Map.of("action",action,"status",runs.status(run),"basis",result.basis(),"elapsed",elapsed,"finishedAt",Instant.now().toString()));
			System.out.println(mapper.writeValueAsString(Map.of("runId",run,"snapshotId",id,"state","VERIFIED","elapsed",elapsed)));return 0;
		}
	}
	private static long millis(long start) {return (System.nanoTime()-start)/1_000_000;}
	private static void write(ObjectMapper mapper,Path output,String name,Object value)throws Exception {
		Path pending=output.resolve(name+".json.partial");mapper.writerWithDefaultPrettyPrinter().writeValue(pending.toFile(),value);Files.move(pending,output.resolve(name+".json"),StandardCopyOption.ATOMIC_MOVE);
	}
	private static void progress(ObjectMapper mapper,Path output,Object value)throws Exception {Files.writeString(output.resolve("progress.jsonl"),mapper.writeValueAsString(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
}
