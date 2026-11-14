package com.aivle.project.messaging.quarantine;
import com.aivle.project.company.reportjob.repository.*;
import com.aivle.project.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.company.reportrequest.model.ReportRequestException;
import com.aivle.project.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
/** 관리자 로컬 명령. 원문을 고쳐 쓰지 않고 DB의 고정 입력으로 재구성한다. */
public final class QuarantineReplayCommand {
	private static final String LOCAL_URL="jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
	public static void main(String[] args){
		try{execute(args);}
		catch(ReportRequestException rejected){System.err.println(rejected.getMessage());System.exit(2);}
		catch(Exception failure){System.err.println("격리 재전달 실패: "+failure.getClass().getSimpleName());System.exit(1);}
	}
	static void execute(String[] args)throws Exception {
		if(args.length!=5||!"local".equals(System.getenv("SPRING_PROFILES_ACTIVE")))throw new IllegalArgumentException("local 명령 인수 확인 필요");
		if(System.getenv("SPRING_DATASOURCE_URL")!=null&&!LOCAL_URL.equals(System.getenv("SPRING_DATASOURCE_URL")))throw new IllegalArgumentException("전용 local DB만 지원");
		UUID quarantine=UUID.fromString(args[0]),command=UUID.fromString(args[1]),request=UUID.fromString(args[3]);long actor=Long.parseLong(args[2]);
		Path secret=Path.of(Objects.requireNonNull(System.getenv("PORTFOLIO_SECRET_DIR"))).resolve("mysql-app");
		var config=new HikariConfig();config.setJdbcUrl(LOCAL_URL);config.setUsername("sentinel_local");config.setPassword(Files.readString(secret));config.setMaximumPoolSize(2);config.setConnectionTimeout(5000);
		var mapper=new ObjectMapper().findAndRegisterModules();
		try(var dataSource=new HikariDataSource(config);var context=new AnnotationConfigApplicationContext()){
			context.registerBean(JdbcTemplate.class,()->new JdbcTemplate(dataSource));context.registerBean(ObjectMapper.class,()->mapper);
			context.registerBean(PlatformTransactionManager.class,()->new JdbcTransactionManager(dataSource));
			context.register(ReportRequestRepository.class,ReportJobRepository.class,ReportRecoveryRepository.class,OutboxWriter.class,QuarantineRepository.class,QuarantineReplayService.class);
			context.refresh();System.out.println(mapper.writeValueAsString(context.getBean(QuarantineReplayService.class).repairAndReplay(actor,quarantine,command,request,args[4])));
		}
	}
}
