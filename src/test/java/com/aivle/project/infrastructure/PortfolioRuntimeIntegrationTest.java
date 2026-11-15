package com.aivle.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.project.support.PortfolioIntegrationSupport;
import com.aivle.project.identity.internal.common.config.LocalMailConfig.DisabledMailSender;
import com.aivle.project.aiintegration.internal.company.client.AiServerClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Tag("realinfra")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "PORTFOLIO_SECRET_DIR=${user.home}/.local/share/sentinel/portfolio/secrets",
    "PORTFOLIO_DATA_DIR=${user.home}/.local/share/sentinel/portfolio",
    "spring.kafka.listener.auto-startup=false"
})
class PortfolioRuntimeIntegrationTest extends PortfolioIntegrationSupport {
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired JavaMailSender mailSender;
    @Autowired AiServerClient aiClient;

    @Test
    void schemaAndRuntime() throws Exception {
        // given/when: 실제 런타임 연결을 확인한다.
        assertThat(verifyRuntime()).containsEntry("mysql", "UP")
            .containsEntry("redis", "UP").containsEntry("kafka", "UP");
        // then: H2 대체 실행을 실제 MySQL 검증으로 세지 않는다.
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getCatalog()).isEqualTo("sentinel_portfolio");
        }
		// V31/V34는 미사용 예약이며 K02 격리 원장이 V35로 추가됐다.
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("35");
		assertThat(flyway.info().applied()).hasSize(33);
    }

    @Test
    void redisExpirationUsesActualRedis() throws Exception {
        // given/when: 실제 Redis에 TTL이 있는 키를 쓴다.
        String key = "portfolio:probe:" + UUID.randomUUID();
        runtimeRedis.opsForValue().set(key, "value", Duration.ofMillis(400));
        assertThat(runtimeRedis.opsForValue().get(key)).isEqualTo("value");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (Boolean.TRUE.equals(runtimeRedis.hasKey(key)) && System.nanoTime() < deadline) Thread.sleep(30);
        // then: 명시적 삭제 없이 Redis TTL로 사라져야 한다.
        assertThat(runtimeRedis.hasKey(key)).isFalse();
    }

    @Test
    void kafkaSendAndConsumeUsesActualBroker() throws Exception {
        String topic = "portfolio-probe-" + UUID.randomUUID();
        var properties = kafkaProperties();
        properties.put("key.serializer", StringSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        properties.put("group.id", "probe-" + UUID.randomUUID());
        properties.put("auto.offset.reset", "earliest");
        properties.put("enable.auto.commit", "false");
        properties.put("max.block.ms", "10000");
        try (var admin = AdminClient.create(properties)) {
            // given: 시험마다 별도 토픽을 사용한다.
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(10, TimeUnit.SECONDS);
            try (var producer = new KafkaProducer<String, String>(properties);
                 var consumer = new KafkaConsumer<String, String>(properties)) {
                // when: 실제 브로커에 전달된 레코드를 새 consumer로 읽는다.
                producer.send(new ProducerRecord<>(topic, "event-id", "probe-value")).get(10, TimeUnit.SECONDS);
                consumer.subscribe(List.of(topic));
                long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
                String observed = null;
                while (observed == null && System.nanoTime() < deadline) {
                    for (var record : consumer.poll(Duration.ofMillis(200))) {
                        assertThat(record.key()).isEqualTo("event-id");
                        observed = record.value();
                    }
                }
                assertThat(observed).isEqualTo("probe-value");
            } finally {
                admin.deleteTopics(List.of(topic)).all().get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void mockHttpPdfAndLocalMailDoNotUseExternalServices() throws Exception {
        // given: 외부 AI 대신 별도 HTTP fixture를 호출한다.
        var http = HttpClient.newHttpClient();
        var control = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/control"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"seed\":\"java-e02\"}")).build();
        assertThat(http.send(control, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        // when/then: 기존 애플리케이션 클라이언트가 실제 HTTP로 유효 PDF를 받는다.
        assertThat(new String(aiClient.getAnalysisReportPdf("005930"), java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF-1.4");
        assertThat(mailSender).isInstanceOf(DisabledMailSender.class);
        mailSender.send(new SimpleMailMessage());
        mailSender.send(mailSender.createMimeMessage());
    }
}
