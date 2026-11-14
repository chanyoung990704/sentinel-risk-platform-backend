package com.aivle.project.common.config;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** 로컬 복원 환경에서는 SMTP 연결과 실제 메일 전송을 하지 않는다. */
@Configuration
@Profile("local")
public class LocalMailConfig {
    @Bean
    JavaMailSender localMailSender() {
        return new DisabledMailSender();
    }

    public static final class DisabledMailSender extends JavaMailSenderImpl {
        @Override
        public void send(MimeMessage... messages) {
            // 로컬 전용: 네트워크 I/O 없이 전송을 생략한다.
        }

        @Override
        public void send(SimpleMailMessage... messages) {
            // 로컬 전용: 네트워크 I/O 없이 전송을 생략한다.
        }
    }
}
