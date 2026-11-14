package com.aivle.project.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class PortfolioIntegrationSupport {
    @Autowired protected DataSource runtimeDataSource;
    @Autowired protected StringRedisTemplate runtimeRedis;

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        // 독립 운영/복원 시험도 같은 애플리케이션 설정 경로를 사용한다.
        String secrets=System.getenv().getOrDefault("PORTFOLIO_SECRET_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio/secrets").toString());
        registry.add("PORTFOLIO_SECRET_DIR",()->secrets);
        registry.add("PORTFOLIO_DATA_DIR",()->System.getenv().getOrDefault("PORTFOLIO_DATA_DIR",Path.of(System.getProperty("user.home"),".local/share/sentinel/portfolio").toString()));
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "sentinel_local");
        registry.add("spring.datasource.password", () -> {
            try {
                return Files.readString(Path.of(secrets,"mysql-app"));
            } catch (Exception error) {
                throw new IllegalStateException("Run local_runtime.py up before real infrastructure tests", error);
            }
        });
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 16379);
        registry.add("spring.kafka.bootstrap-servers", () -> "127.0.0.1:19092");
        registry.add("ai.server.url", () -> "http://127.0.0.1:18081");
    }

    protected Properties kafkaProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", "127.0.0.1:19092");
        properties.put("request.timeout.ms", "3000");
        properties.put("default.api.timeout.ms", "10000");
        return properties;
    }

    protected Map<String, String> verifyRuntime() throws Exception {
        try (var connection = runtimeDataSource.getConnection();
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT 1")) {
            if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("MySQL query failed");
        }
        try (var connection = runtimeRedis.getConnectionFactory().getConnection()) {
            if (!"PONG".equals(connection.ping())) throw new IllegalStateException("Redis ping failed");
        }
        try (var admin = AdminClient.create(kafkaProperties())) {
            admin.describeCluster().clusterId().get(10, TimeUnit.SECONDS);
        }
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:18081/health"))
            .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Mock AI health failed");
        return Map.of("mysql", "UP", "redis", "UP", "kafka", "UP", "mockAi", "UP");
    }
}
