package com.aivle.project.dataimport;

import com.aivle.project.dataimport.repository.*;
import com.aivle.project.dataimport.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** pointer UPDATE 이후/COMMIT 이전에 별도 JVM을 종료하는 테스트 전용 probe. */
public final class SnapshotActivationCrashProbe {
	public static void main(String[] args)throws Exception {
		HikariConfig c=new HikariConfig();c.setJdbcUrl("jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");c.setUsername("sentinel_local");c.setPassword(Files.readString(com.aivle.project.support.PortfolioSecretPaths.file("mysql-app")));c.setMaximumPoolSize(2);
		try(var ds=new HikariDataSource(c);var context=new AnnotationConfigApplicationContext()) {
			JdbcTemplate jdbc=new JdbcTemplate(ds);ObjectMapper mapper=new ObjectMapper();
			context.registerBean(JdbcTemplate.class,()->jdbc);context.registerBean(ObjectMapper.class,()->mapper);context.registerBean(PlatformTransactionManager.class,()->new JdbcTransactionManager(ds));
			context.registerBean(SnapshotReportRepository.class,()->new SnapshotReportRepository(jdbc,mapper){@Override public void switchPointer(String family,UUID id){super.switchPointer(family,id);Runtime.getRuntime().halt(94);}});
			context.register(ImportCrashProbe.Transactions.class,ImportJournalRepository.class,ImportInputValidator.class,SnapshotVerificationService.class,SnapshotActivationService.class);context.refresh();
			context.getBean(SnapshotActivationService.class).activate(UUID.fromString(args[0]));
		}
	}
}
