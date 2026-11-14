package com.aivle.project.dataimport;

import com.aivle.project.dataimport.repository.ImportJournalRepository;
import com.aivle.project.dataimport.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 테스트 전용 별도 JVM. runId만 받아 DB 입력/진행 기록으로 처리한다. */
public final class ImportCrashProbe {
	@Configuration(proxyBeanMethods=false) @EnableTransactionManagement
	static class Transactions { }
	public static void main(String[] args)throws Exception {
		UUID run=UUID.fromString(args[0]);String mode=args[1];
		HikariConfig config=new HikariConfig();config.setJdbcUrl("jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
		config.setUsername("sentinel_local");config.setPassword(Files.readString(com.aivle.project.support.PortfolioSecretPaths.file("mysql-app")));config.setMaximumPoolSize(2);
		try(var ds=new HikariDataSource(config);var context=new AnnotationConfigApplicationContext()) {
			JdbcTemplate jdbc=new JdbcTemplate(ds);ObjectMapper mapper=new ObjectMapper();
			context.registerBean(ObjectMapper.class,()->mapper);
			context.registerBean(PlatformTransactionManager.class,()->new JdbcTransactionManager(ds));
			context.registerBean(ImportJournalRepository.class,()->new ImportJournalRepository(jdbc,mapper) {
				@Override public void commitChunk(UUID snapshot,int number,UUID runId,int count) {
					if(mode.equals("before") && number==6)Runtime.getRuntime().halt(91);
					super.commitChunk(snapshot,number,runId,count);
				}
			});
			context.register(Transactions.class,ImportInputValidator.class,ImportChunkService.class,ImportRunService.class);context.refresh();
			var service=context.getBean(ImportRunService.class);service.resume(run);
			while(!service.status(run).snapshotState().equals("STAGED")) {
				var result=service.stageNext(run);
				if(mode.equals("after") && result.chunkNumber()==6)Runtime.getRuntime().halt(92);
			}
			System.out.println(mapper.writeValueAsString(service.status(run)));
		}
	}
}
