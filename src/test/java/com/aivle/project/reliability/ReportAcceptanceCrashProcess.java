package com.aivle.project.reliability;
import com.aivle.project.reporting.internal.company.reportrequest.repository.ReportRequestRepository;
import com.aivle.project.reporting.internal.company.reportrequest.service.*;
import com.aivle.project.reporting.api.CustomReportCommand;
import com.aivle.project.reporting.internal.messaging.outbox.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
/** 실제 accept 트랜잭션의 Outbox INSERT 직후/commit 직후 별도 JVM을 종료한다. */
public final class ReportAcceptanceCrashProcess {
    public static void main(String[] args)throws Exception {
        if(args.length!=4 || !java.util.Set.of("before","after").contains(args[0]))throw new IllegalArgumentException("경계 인수 확인 필요");
        var config=new HikariConfig();config.setJdbcUrl("jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");config.setUsername("sentinel_local");
        config.setPassword(Files.readString(Path.of(System.getenv("PORTFOLIO_SECRET_DIR"),"mysql-app")));config.setMaximumPoolSize(2);
        try(var dataSource=new HikariDataSource(config)) {
            var jdbc=new JdbcTemplate(dataSource);var mapper=new ObjectMapper().findAndRegisterModules();
            var sources=com.aivle.project.finance.api.FinancialSourceReaders.jdbc(jdbc,mapper);
            var rows=new ReportRequestRepository(jdbc,com.aivle.project.identity.api.IdentityReaders.jdbc(jdbc),sources);
            var writer=new OutboxWriter(jdbc,mapper){@Override public void append(ReportEvent event){super.append(event);if(args[0].equals("before"))Runtime.getRuntime().halt(93);}};
            var service=new ReportRequestService(rows,new ReportAdmissionService(rows,1000,200),writer,mapper,new JdbcTransactionManager(dataSource));
            service.accept(Long.valueOf(args[1]),Long.valueOf(args[2]),args[3],new CustomReportCommand(null,"commit 경계 시험"));
            Runtime.getRuntime().halt(94);
        }
    }
}
