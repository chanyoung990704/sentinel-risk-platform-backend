package com.aivle.project.company.reportjob.cli;
import com.aivle.project.company.reportjob.service.ReportReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
/** local DB/업로드 폴더를 읽기만 대조한다. 원문과 경로를 출력하지 않는다. */
public final class ReportReconciliationCommand {
	private static final String URL="jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
	public static void main(String[] args){
		try{
			if(args.length<1||args.length>1000||!"local".equals(System.getenv("SPRING_PROFILES_ACTIVE")))throw new IllegalArgumentException("local 대조 인수 확인 필요");
			if(System.getenv("SPRING_DATASOURCE_URL")!=null&&!URL.equals(System.getenv("SPRING_DATASOURCE_URL")))throw new IllegalArgumentException("전용 local DB만 지원");
			var ids=Arrays.stream(args).map(UUID::fromString).toList();var config=new HikariConfig();config.setJdbcUrl(URL);config.setUsername("sentinel_local");config.setPassword(Files.readString(Path.of(Objects.requireNonNull(System.getenv("PORTFOLIO_SECRET_DIR"))).resolve("mysql-app")));config.setMaximumPoolSize(1);config.setConnectionTimeout(5000);config.setReadOnly(true);
			try(var dataSource=new HikariDataSource(config)){var service=new ReportReconciliationService(new JdbcTemplate(dataSource),Path.of(Objects.requireNonNull(System.getenv("PORTFOLIO_DATA_DIR"))).resolve("uploads").toString());System.out.println(new ObjectMapper().writeValueAsString(service.reconcile(ids)));}
		}catch(Exception failure){System.err.println("요청 대조 실패: "+failure.getClass().getSimpleName());System.exit(1);}
	}
}
