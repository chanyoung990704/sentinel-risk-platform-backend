package com.aivle.project.reliability;

import com.aivle.project.ProjectApplication;
import com.aivle.project.company.reportjob.service.ReportJobWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 배포 classpath에 없는 운영 시험 실행기. 제어 주소/자격은 private state에만 둔다. */
public final class PortfolioOperationsProcess {
    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !Set.of("operations","restore").contains(System.getenv("SENTINEL_RUNTIME_NAMESPACE")))
            throw new IllegalArgumentException("시험 소유 환경 확인 필요");
        Path state=Path.of(args[0]);Files.createDirectories(state);
        var app=new SpringApplication(ProjectApplication.class);
        app.setDefaultProperties(Map.of("spring.datasource.hikari.connection-timeout","2000"));
        var context=app.run("--spring.profiles.active=local,portfolio-perf","--server.port=18080","--server.address=127.0.0.1",
            "--spring.kafka.listener.auto-startup=false","--custom-report.messaging.enabled=true","--custom-report.messaging.auto-startup=true",
            "--custom-report.messaging.group-id=m05-operations","--custom-report.worker.enabled=true",
            "--custom-report.admission.user-limit=100","--custom-report.admission.global-limit=100",
            "--custom-report.worker.retry-jitter-ms=0","--custom-report.status-cache.enabled=false",
            "--logging.level.root=WARN","--spring.main.banner-mode=off");
        var worker=context.getBean(ReportJobWorker.class);if(Boolean.parseBoolean(args[1]))worker.stopClaiming();
        var jdbc=context.getBean(JdbcTemplate.class);var encoder=context.getBean(PasswordEncoder.class);var mapper=new ObjectMapper();
        Path fixture=state.resolve("fixture.json");
        if(!Files.exists(fixture)) {
            var users=new ArrayList<Map<String,Object>>();
            for(int i=0;i<20;i++) {
                String email="m05-"+UUID.randomUUID()+"@sentinel.invalid",password=UUID.randomUUID()+"aA1!";
                jdbc.update("INSERT INTO users(uuid,email,password,name,status,password_changed_at) VALUES(UUID_TO_BIN(UUID()),?,?,?,'ACTIVE',NOW())",email,encoder.encode(password),"운영복원시험");
                long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
                jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_USER'",id);
                users.add(Map.of("id",id,"email",email,"password",password));
            }
            long company=jdbc.queryForObject("SELECT b.company_id FROM snapshot_report_versions b JOIN active_data_snapshots a ON a.snapshot_id=b.snapshot_id WHERE a.family='FINANCIAL' ORDER BY b.company_id LIMIT 1",Long.class);
            Files.createFile(fixture,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(fixture,mapper.writeValueAsString(Map.of("users",users,"company",company)));
        }
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newFixedThreadPool(2);server.setExecutor(executor);
        server.createContext("/worker",exchange->{
            if(!"POST".equals(exchange.getRequestMethod()) || !args[2].equals(exchange.getRequestHeaders().getFirst("X-Owned-Nonce"))) {
                exchange.sendResponseHeaders(403,-1);exchange.close();return;
            }
            String action=new String(exchange.getRequestBody().readNBytes(32),StandardCharsets.US_ASCII);
            if(action.equals("pause"))worker.stopClaiming();else if(action.equals("resume"))worker.startAccepting();
            else {exchange.sendResponseHeaders(400,-1);exchange.close();return;}
            byte[] body=mapper.writeValueAsBytes(Map.of("accepting",worker.isAccepting()));exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(0);executor.shutdownNow();}));
        Path ready=state.resolve("app-ready.json");Files.deleteIfExists(ready);
        publishReady(ready,mapper.writeValueAsString(Map.of("pid",ProcessHandle.current().pid(),"controlPort",server.getAddress().getPort(),"nonce",args[2],"paused",!worker.isAccepting())),created->{});
    }
    static void publishReady(Path ready,String identity,java.util.function.Consumer<Path> afterCreate)throws Exception {
        if(Files.exists(ready))throw new FileAlreadyExistsException(ready.toString());
        Path temporary=Files.createTempFile(ready.getParent(),".ready-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        // 준비 파일을 관찰하는 관리 도구에는 완성된 JSON만 공개한다.
        try{afterCreate.accept(temporary);Files.writeString(temporary,identity);Files.move(temporary,ready,StandardCopyOption.ATOMIC_MOVE);}
        finally{Files.deleteIfExists(temporary);}
    }
}
