package com.aivle.project.reliability;

import com.aivle.project.ProjectApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 실제 로그인용 소유 fixture를 private 파일에만 기록하는 Ubuntu 시험 앱. */
public final class PortfolioBrowserProcess {
    public static void main(String[] args) throws Exception {
        if(args.length != 2 || !Set.of("off","on").contains(args[1])) throw new IllegalArgumentException("시험 인수 확인 필요");
        Path state=Path.of(args[0]); Files.createDirectories(state);
        var app=new SpringApplication(ProjectApplication.class);
        var context=app.run("--spring.profiles.active=local,portfolio-perf", "--server.port=18080", "--server.address=127.0.0.1",
            "--spring.kafka.listener.auto-startup=false", "--custom-report.messaging.enabled=true", "--custom-report.messaging.auto-startup=true",
            "--custom-report.worker.enabled=true", "--custom-report.admission.user-limit=10000", "--custom-report.admission.global-limit=10000",
            "--custom-report.status-cache.enabled="+args[1].equals("on"), "--logging.level.root=WARN", "--spring.main.banner-mode=off");
        var jdbc=context.getBean(JdbcTemplate.class); var encoder=context.getBean(PasswordEncoder.class); var mapper=new ObjectMapper();
        Path fixture=state.resolve("browser-fixture.json");
        if(!Files.exists(fixture)) {
            var users=new ArrayList<Map<String,Object>>();
            for(int i=0;i<20;i++) {
                String email="r07-"+UUID.randomUUID()+"@sentinel.invalid", password=UUID.randomUUID()+"aA1!";
                jdbc.update("INSERT INTO users(uuid,email,password,name,status,password_changed_at) VALUES(UUID_TO_BIN(UUID()),?,?,?,'ACTIVE',NOW())",email,encoder.encode(password),"복원시험");
                long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
                jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ROLE_USER'",id);
                users.add(Map.of("id",id,"email",email,"password",password));
            }
            long company=jdbc.queryForObject("SELECT c.id FROM companies c JOIN snapshot_report_versions b ON b.company_id=c.id JOIN active_data_snapshots a ON a.snapshot_id=b.snapshot_id WHERE a.family='FINANCIAL' ORDER BY c.id LIMIT 1",Long.class);
            Files.createFile(fixture,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(fixture,mapper.writeValueAsString(Map.of("users",users,"company",company)));
        }
        Files.writeString(state.resolve("browser-ready.json"),mapper.writeValueAsString(Map.of("pid",ProcessHandle.current().pid(),"cache",args[1])),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
    }
}
