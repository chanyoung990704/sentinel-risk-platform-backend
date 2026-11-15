package com.aivle.project.reporting.internal.company.reportjob.cli;
import com.aivle.project.reporting.internal.company.reportjob.repository.*;
import com.aivle.project.reporting.internal.company.reportjob.service.ReportRecoveryService;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.reporting.internal.company.reportrequest.service.ReportAdmissionService;
import com.aivle.project.reporting.api.ReportRequestException;
import com.aivle.project.reporting.internal.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
/** 관리자 전용 local JVM 진입점. 웹 관리 API를 제공하지 않는다. */
public final class ReportRecoveryCommand {
	private static final String LOCAL_URL="jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
	public static void main(String[] args){
		try{execute(args);}
		catch(ReportRequestException rejected){System.err.println(rejected.getMessage());System.exit(2);}
		catch(Exception failure){System.err.println("관리 재개 실패: "+failure.getClass().getSimpleName());System.exit(1);}
	}
	static void execute(String[] args)throws Exception {
		if(args.length!=4||!"local".equals(System.getenv("SPRING_PROFILES_ACTIVE")))throw new IllegalArgumentException("local 명령 인수 확인 필요");
		if(System.getenv("SPRING_DATASOURCE_URL")!=null&&!LOCAL_URL.equals(System.getenv("SPRING_DATASOURCE_URL")))throw new IllegalArgumentException("전용 local DB만 지원");
		UUID request=UUID.fromString(args[0]),command=UUID.fromString(args[1]);long actor=Long.parseLong(args[2]);
		Path secret=Path.of(Objects.requireNonNull(System.getenv("PORTFOLIO_SECRET_DIR"))).resolve("mysql-app");
		var config=new HikariConfig();config.setJdbcUrl(LOCAL_URL);config.setUsername("sentinel_local");config.setPassword(Files.readString(secret));config.setMaximumPoolSize(2);config.setConnectionTimeout(5000);
		var mapper=new ObjectMapper().findAndRegisterModules();
		try(var dataSource=new HikariDataSource(config);var context=new AnnotationConfigApplicationContext()){
			context.registerBean(JdbcTemplate.class,()->new JdbcTemplate(dataSource));
			context.registerBean(ObjectMapper.class,()->mapper);
			context.registerBean(PlatformTransactionManager.class,()->new JdbcTransactionManager(dataSource));
			context.registerBean(com.aivle.project.identity.api.IdentityQueries.class,()->
				com.aivle.project.identity.api.IdentityReaders.jdbc(context.getBean(JdbcTemplate.class)));
			context.registerBean(com.aivle.project.finance.api.FinancialSources.class,()->
				com.aivle.project.finance.api.FinancialSourceReaders.jdbc(context.getBean(JdbcTemplate.class),mapper));
			context.register(ReportRequestRepository.class,ReportJobRepository.class,ReportRecoveryRepository.class,OutboxWriter.class,ReportRecoveryService.class);
			context.registerBean(ReportAdmissionService.class,()->new ReportAdmissionService(context.getBean(ReportRequestRepository.class),limit("CUSTOM_REPORT_ADMISSION_GLOBAL_LIMIT",1000),limit("CUSTOM_REPORT_ADMISSION_USER_LIMIT",20)));
			context.refresh();
			var result=context.getBean(ReportRecoveryService.class).recover(actor,request,command,args[3]);System.out.println(mapper.writeValueAsString(result));
		}
	}
	private static int limit(String name,int fallback){String value=System.getenv(name);int result=value==null?fallback:Integer.parseInt(value);if(result<1||result>100000)throw new IllegalArgumentException("수용량 범위 확인 필요");return result;}
}
