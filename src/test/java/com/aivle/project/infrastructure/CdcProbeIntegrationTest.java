package com.aivle.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import com.aivle.project.support.CdcRecoveryGuard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Connection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("cdc")
class CdcProbeIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Properties kafka = new Properties();
    private final Set<String> committed = new LinkedHashSet<>();
    private final Set<String> rolledBack = new LinkedHashSet<>();
    private final Map<String, JsonNode> observed = new LinkedHashMap<>();
    private final List<Map<String, Object>> records = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final String runId = System.getenv().getOrDefault("PORTFOLIO_CDC_RUN_ID", "manual-" + UUID.randomUUID());
    private final String name = "sentinel-probe-" + UUID.randomUUID().toString().replace("-", "");
    private final String topic = name + ".sentinel_cdc_probe.outbox_probe";
    private final String history = name + "-history";
    private final Path output = Path.of(System.getenv().getOrDefault("PORTFOLIO_CDC_RUN_DIR", "build/reports/cdc-probe"));
    private JsonNode lastSource;

    @Test
    void mysqlPrerequisites() throws Exception {
        // given/when: 기존 전용 MySQL의 실제 서버 설정을 조회한다.
        String password = Files.readString(com.aivle.project.support.PortfolioSecretPaths.file("mysql-app"));
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/sentinel_portfolio?useSSL=false&allowPublicKeyRetrieval=true", "sentinel_local", password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT @@server_id, @@log_bin, @@binlog_format, @@binlog_row_image, @@binlog_expire_logs_seconds")) {
            assertThat(result.next()).isTrue();
            // then: Kafka 전달 시험 이전에 명시적으로 설정한 CDC 전제 조건을 확인한다.
            assertThat(result.getLong(1)).isEqualTo(223344);
            assertThat(result.getBoolean(2)).isTrue();
            assertThat(result.getString(3)).isEqualTo("ROW");
            assertThat(result.getString(4)).isEqualTo("FULL");
            assertThat(result.getLong(5)).isEqualTo(864000);
        }
    }

    @Test
    void committedEventsSurviveWorkerAndBrokerReplacement() throws Exception {
        kafka.put("bootstrap.servers", "127.0.0.1:19092");
        kafka.put("request.timeout.ms", "3000");
        kafka.put("default.api.timeout.ms", "10000");
        var consumerProperties = new Properties();
        consumerProperties.putAll(kafka);
        consumerProperties.put("key.deserializer", StringDeserializer.class.getName());
        consumerProperties.put("value.deserializer", StringDeserializer.class.getName());
        consumerProperties.put("group.id", name);
        consumerProperties.put("auto.offset.reset", "earliest");
        consumerProperties.put("enable.auto.commit", "false");
        Files.createDirectories(output);
        evidence.put("completed", false);
        evidence.put("connector", name);
        evidence.put("topic", topic);
        evidence.put("historyTopic", history);
        try (var admin = AdminClient.create(kafka);
             var consumer = new KafkaConsumer<String, String>(consumerProperties)) {
            admin.createTopics(List.of(
                new NewTopic(topic, 1, (short) 1),
                new NewTopic(history, 1, (short) 1).configs(Map.of("cleanup.policy", "delete", "retention.ms", "-1", "retention.bytes", "-1"))
            )).all().get(15, TimeUnit.SECONDS);
            consumer.subscribe(List.of(topic));
            // given: 초기 snapshot보다 먼저 커밋한 Outbox도 동일 eventId로 수신한다.
            write(1, true);
            String template = Files.readString(Path.of("infra/cdc/portfolio-probe.json"));
            request("/connectors/" + name + "/config", "PUT", JSON.readTree(template.replace("__PREFIX__", name).replace("__HISTORY__", history)));
            running();
            receive(consumer, committed, Duration.ofSeconds(90));
            assertThat(records).anyMatch(record -> record.get("op").equals("r"));
            evidence.put("snapshotReceived", true);

            // when: 개별 커밋 10건과 개별 롤백 10건을 만든다.
            write(10, true);
            write(10, false);
            receive(consumer, committed, Duration.ofSeconds(60));
            JsonNode checkpointBefore = checkpoint();
            Map<String, Object> historyBefore = topicEvidence(history);
            evidence.put("checkpointBefore", checkpointBefore);
            evidence.put("historyBefore", historyBefore);

            // worker의 파일 시스템을 재사용하지 않고 Kafka 내부 상태에서 재개한다.
            try (var recovery = new CdcRecoveryGuard(() -> fault("connect-recreate"))) {
                fault("connect-stop");
                write(5, true);
            }
            running();
            receive(consumer, committed, Duration.ofSeconds(90));
            JsonNode checkpointAfter = checkpoint();
            assertThat(checkpointAfter).isNotEqualTo(checkpointBefore);
            evidence.put("checkpointAfterWorker", checkpointAfter);
            evidence.put("connectorResume", true);
            evidence.put("offsetsPreserved", snapshotCount() == 1 && !checkpointBefore.path("offsets").isEmpty() && !checkpointAfter.path("offsets").isEmpty());

            // broker 컨테이너도 교체하며 같은 named volume을 보존한다.
            try (var recovery = new CdcRecoveryGuard(() -> fault("broker-start"))) {
                fault("broker-stop");
                write(5, true);
            }
            running();
            receive(consumer, committed, Duration.ofSeconds(90));
            // 마지막 커밋 이후에도 짧은 관찰 구간을 둬 지연된 추가 레코드를 집계한다.
            drain(consumer, Duration.ofSeconds(2));
            JsonNode checkpointAfterBroker = checkpoint();
            assertThat(checkpointAfterBroker).isNotEqualTo(checkpointAfter);
            evidence.put("checkpointAfterBroker", checkpointAfterBroker);
            var historyAfter = topicEvidence(history);
            evidence.put("historyAfter", historyAfter);
            evidence.put("brokerResume", true);
            evidence.put("historyPreserved", historyBefore.equals(historyAfter));

            List<String> internal = List.of("sentinel-connect-configs", "sentinel-connect-offsets", "sentinel-connect-status");
            Map<String, Object> configs = new LinkedHashMap<>();
            for (String internalTopic : internal) {
                var resource = new ConfigResource(ConfigResource.Type.TOPIC, internalTopic);
                var config = admin.describeConfigs(List.of(resource)).all().get(15, TimeUnit.SECONDS).get(resource);
                String cleanup = config.get("cleanup.policy").value();
                assertThat(cleanup).contains("compact");
                configs.put(internalTopic, Map.of("cleanupPolicy", cleanup, "recordEvidence", topicEvidence(internalTopic)));
            }
            evidence.put("internalTopics", configs);
            evidence.put("internalTopicsCompacted", true);
            evidence.put("eventIdsPreserved", true); // receive에서 key와 after.event_id를 매번 비교한다.
            assertThat(observed.keySet()).containsExactlyInAnyOrderElementsOf(committed).doesNotContainAnyElementsOf(rolledBack);
            assertThat(snapshotCount()).isEqualTo(1);
            assertThat(committed).hasSize(21);
            assertThat(rolledBack).hasSize(10);
            try (var connection = writer(); var query = connection.prepareStatement("SELECT COUNT(*) FROM outbox_probe WHERE run_id=?")) {
                query.setString(1, runId);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(21);
                    evidence.put("databaseCommittedRows", rows.getInt(1));
                }
            }
            evidence.put("completed", true);
        } finally {
            // 실패해도 전용 구성 요소를 복구하며 이 시험 소유 connector만 제거한다.
            try {
                request("/connectors/" + name, "DELETE", null);
            } finally {
                evidence.put("runId", runId);
                evidence.put("committedExpected", committed.size());
                evidence.put("committedObserved", committed.stream().filter(observed::containsKey).count());
                evidence.put("rollbackExpected", rolledBack.size());
                evidence.put("rollbackObserved", rolledBack.stream().filter(observed::containsKey).count());
                evidence.put("committedEventIds", committed);
                evidence.put("rolledBackEventIds", rolledBack);
                evidence.put("observedUniqueEventIds", observed.keySet());
                evidence.put("observedUniqueCount", observed.size());
                evidence.put("duplicateRecords", records.size() - observed.size());
                evidence.put("records", records);
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("observations.json").toFile(), evidence);
            }
        }
    }

    @Test
    void partialStopFailureRestoresActualWorkerAndBroker() throws Exception {
        Files.createDirectories(output);
        var recovered = new ArrayList<String>();
        for (var actions : List.of(List.of("connect-stop", "connect-recreate"), List.of("broker-stop", "broker-start"))) {
            // when: 실제 정지 완료 직후 제어 명령이 실패한 상황을 주입한다.
            Throwable failure = catchThrowable(() -> {
                try (var recovery = new CdcRecoveryGuard(() -> fault(actions.get(1)))) {
                    fault(actions.getFirst());
                    throw new IllegalStateException("injected failure after stop");
                }
            });
            // then: 원래 실패는 유지하되 복구는 성공하고 실제 구성 요소가 다시 응답한다.
            assertThat(failure).isInstanceOf(IllegalStateException.class).hasMessage("injected failure after stop");
            assertThat(failure.getSuppressed()).isEmpty();
            assertThat(request("/connector-plugins", "GET", null).isArray()).isTrue();
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", "127.0.0.1:19092", "request.timeout.ms", "3000", "default.api.timeout.ms", "10000"))) {
                assertThat(admin.describeCluster().clusterId().get(10, TimeUnit.SECONDS)).isNotBlank();
            }
            recovered.add(actions.getFirst());
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("partial-stop-recovery.json").toFile(), Map.of("recovered", recovered, "injectedFailures", 2));
    }

    private Connection writer() throws Exception {
        String password = Files.readString(com.aivle.project.support.PortfolioSecretPaths.file("mysql-probe-writer"));
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/sentinel_cdc_probe?useSSL=false&allowPublicKeyRetrieval=true", "sentinel_probe_writer", password);
    }

    private void write(int count, boolean commit) throws Exception {
        try (var connection = writer(); var insert = connection.prepareStatement("INSERT INTO outbox_probe(event_id,run_id,payload) VALUES(?,?,?)")) {
            connection.setAutoCommit(false);
            for (int i = 0; i < count; i++) {
                String id = UUID.randomUUID().toString();
                insert.setString(1, id);
                insert.setString(2, runId);
                insert.setString(3, "{\"kind\":\"probe\"}");
                insert.executeUpdate();
                if (commit) { connection.commit(); committed.add(id); }
                else { connection.rollback(); rolledBack.add(id); }
            }
        }
    }

    private void receive(KafkaConsumer<String, String> consumer, Set<String> expected, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!observed.keySet().containsAll(expected) && System.nanoTime() < deadline) collect(consumer);
        assertThat(observed.keySet()).containsAll(expected);
    }

    private void drain(KafkaConsumer<String, String> consumer, Duration duration) throws Exception {
        long deadline = System.nanoTime() + duration.toNanos();
        while (System.nanoTime() < deadline) collect(consumer);
    }

    private void collect(KafkaConsumer<String, String> consumer) throws Exception {
        for (var record : consumer.poll(Duration.ofMillis(200))) {
            if (record.value() == null) continue;
            JsonNode value = JSON.readTree(record.value());
            JsonNode after = value.path("after");
            if (!runId.equals(after.path("run_id").asText())) continue;
            String id = after.path("event_id").asText();
            assertThat(JSON.readTree(record.key()).path("event_id").asText()).isEqualTo(id);
            assertThat(id).isNotBlank();
            observed.put(id, value);
            lastSource = value.path("source");
            records.add(Map.of("eventId", id, "op", value.path("op").asText(), "offset", record.offset(),
                "partition", record.partition(), "binlogFile", lastSource.path("file").asText(), "binlogPosition", lastSource.path("pos").asLong()));
        }
    }

    private long snapshotCount() {
        return records.stream().filter(record -> record.get("op").equals("r")).count();
    }

    private JsonNode request(String path, String method, JsonNode data) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18083" + path)).timeout(Duration.ofSeconds(10));
        builder.header("Content-Type", "application/json").method(method,
            data == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(data.toString()));
        var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (method.equals("DELETE") && response.statusCode() == 404) return JSON.nullNode();
        if (method.equals("GET") && path.endsWith("/status") && Set.of(404, 409, 503).contains(response.statusCode())) return JSON.nullNode();
        assertThat(response.statusCode()).as("Connect %s %s", method, path).isBetween(200, 299);
        return response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
    }

    private void running() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode state = request("/connectors/" + name + "/status", "GET", null);
            if (state.path("connector").path("state").asText().equals("RUNNING") && state.path("tasks").size() == 1
                && state.path("tasks").get(0).path("state").asText().equals("RUNNING")) return;
            if (state.path("tasks").toString().contains("FAILED")) throw new IllegalStateException("Connector task failed; inspect private Connect log");
            Thread.sleep(300);
        }
        throw new IllegalStateException("Connector RUNNING timeout");
    }

    private JsonNode checkpoint() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        JsonNode previous = null;
        long stableSince = System.nanoTime();
        while (System.nanoTime() < deadline) {
            JsonNode offsets = request("/connectors/" + name + "/offsets", "GET", null);
            for (JsonNode offset : offsets.path("offsets")) {
                JsonNode position = offset.path("offset");
                String file = position.path("file").asText();
                // Connect offset은 트랜잭션 시작+event/row skip 좌표다. envelope source.pos와 직접 대소 비교하지 않는다.
                if (!file.isBlank() && position.path("pos").asLong() > 0 && !position.has("snapshot")) {
                    if (!offsets.equals(previous)) { previous = offsets; stableSince = System.nanoTime(); }
                    if (System.nanoTime() - stableSince >= Duration.ofMillis(1500).toNanos()) return offsets;
                }
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Durable source offset checkpoint timeout");
    }

    private Map<String, Object> topicEvidence(String name) throws Exception {
        var properties = new Properties();
        properties.putAll(kafka);
        properties.put("key.deserializer", ByteArrayDeserializer.class.getName());
        properties.put("value.deserializer", ByteArrayDeserializer.class.getName());
        properties.put("enable.auto.commit", "false");
        var partitions = new ArrayList<TopicPartition>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (var reader = new KafkaConsumer<byte[], byte[]>(properties)) {
            for (var partition : reader.partitionsFor(name, Duration.ofSeconds(10))) partitions.add(new TopicPartition(name, partition.partition()));
            assertThat(partitions).hasSize(1);
            reader.assign(partitions);
            reader.seekToBeginning(partitions);
            var end = reader.endOffsets(partitions);
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (reader.position(partitions.getFirst()) < end.get(partitions.getFirst()) && System.nanoTime() < deadline) {
                for (var record : reader.poll(Duration.ofMillis(100))) {
                    count++;
                    if (record.key() != null) digest.update(record.key());
                    if (record.value() != null) digest.update(record.value());
                }
            }
            assertThat(reader.position(partitions.getFirst())).isGreaterThanOrEqualTo(end.get(partitions.getFirst()));
        }
        assertThat(count).isPositive();
        return Map.of("records", count, "sha256", HexFormat.of().formatHex(digest.digest()));
    }

    private void fault(String action) throws Exception {
        var process = new ProcessBuilder("python3", "scripts/reliability/cdc_runtime.py", action)
            .redirectErrorStream(true).redirectOutput(output.resolve(action + "-" + System.nanoTime() + ".log").toFile()).start();
        if (!process.waitFor(180, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Fault helper timeout: " + action); }
        assertThat(process.exitValue()).as("Fault helper %s", action).isZero();
    }
}
