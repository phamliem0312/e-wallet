package com.ewallet.platform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Postgres và Kafka thật (Testcontainers): outbox → Kafka → consumer có inbox, retry, DLT. */
@SpringBootTest(classes = OutboxFlowTest.App.class, properties = {
        "spring.application.name=test-service",
        "spring.flyway.locations=classpath:db/platform",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.properties.metadata.max.age.ms=1000",
        "ewallet.outbox.poll-interval=PT0.1S",
        "ewallet.messaging.retry-interval-ms=50",
})
@Testcontainers
class OutboxFlowTest {

    static final String TOPIC = "test.events";

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    static class Received {
        final List<CloudEvent> events = new CopyOnWriteArrayList<>();

        long count(UUID id) {
            return events.stream().filter(e -> e.id().equals(id)).count();
        }

        boolean has(String subject) {
            return events.stream().anyMatch(e -> e.subject().equals(subject));
        }
    }

    static class Handler {
        private final IdempotentEventProcessor processor;
        private final Received received;

        Handler(IdempotentEventProcessor processor, Received received) {
            this.processor = processor;
            this.received = received;
        }

        @KafkaListener(topics = TOPIC, groupId = "test-handler")
        void on(String value) {
            processor.process(value, "test-handler", event -> {
                if ("Fail".equals(event.type())) throw new IllegalStateException("handler failed");
                received.events.add(event);
            });
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
        @Bean
        Received received() {
            return new Received();
        }

        @Bean
        Handler handler(IdempotentEventProcessor processor, Received received) {
            return new Handler(processor, received);
        }
    }

    @Autowired OutboxService outbox;
    @Autowired OutboxCleanup cleanup;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired Received received;

    @Test
    void eventWrittenInTransactionReachesConsumerAsCloudEvent() {
        UUID id = tx.execute(s -> outbox.publish(TOPIC, "agg-1", "CustomerRegistered", 3, Map.of("customerId", "c1")));

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.count(id) > 0);
        CloudEvent event = received.events.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        assertThat(event.type()).isEqualTo("CustomerRegistered");
        assertThat(event.source()).isEqualTo("test-service");
        assertThat(event.subject()).isEqualTo("agg-1");
        assertThat(event.aggregateVersion()).isEqualTo(3);
        assertThat(event.data().get("customerId").asText()).isEqualTo("c1");
        await().atMost(10, TimeUnit.SECONDS).until(() -> count("select count(*) from outbox where id = ? "
                + "and published_at is not null", id) == 1);
    }

    @Test
    void publishOutsideTransactionIsRejected() {
        assertThatThrownBy(() -> outbox.publish(TOPIC, "agg-x", "X", 1, Map.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rolledBackTransactionPublishesNothing() {
        tx.execute(s -> {
            outbox.publish(TOPIC, "agg-rollback", "X", 1, Map.of());
            s.setRollbackOnly();
            return null;
        });
        assertThat(count("select count(*) from outbox where event_key = 'agg-rollback'")).isZero();

        // sentinel đi sau: khi nó đến nơi thì event của transaction rollback (nếu có) đã đến trước
        UUID sentinel = tx.execute(s -> outbox.publish(TOPIC, "agg-rollback", "Sentinel", 2, Map.of()));
        await().atMost(30, TimeUnit.SECONDS).until(() -> received.count(sentinel) > 0);
        assertThat(received.events).noneMatch(e -> e.type().equals("X") && e.subject().equals("agg-rollback"));
    }

    @Test
    void redeliveredEventIsHandledOnce() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        // cùng key => cùng partition => đúng thứ tự: bản trùng được xử lý trước sentinel
        kafka.send(TOPIC, "agg-dup", envelope(id, "Dup", "agg-dup", 1)).get();
        kafka.send(TOPIC, "agg-dup", envelope(id, "Dup", "agg-dup", 1)).get();
        kafka.send(TOPIC, "agg-dup", envelope(sentinel, "Sentinel", "agg-dup", 2)).get();

        await().atMost(30, TimeUnit.SECONDS).until(() -> received.count(sentinel) > 0);
        assertThat(received.count(id)).isEqualTo(1);
    }

    @Test
    void malformedEventGoesStraightToDlt() throws Exception {
        kafka.send(TOPIC, "poison", "not-json").get();

        ConsumerRecord<String, String> dead = awaitRecord(TOPIC + ".dlt", r -> "not-json".equals(r.value()));
        assertThat(dead.key()).isEqualTo("poison");
    }

    @Test
    void failingHandlerRetriesThenGoesToDltAndLeavesNoInboxRow() throws Exception {
        UUID id = UUID.randomUUID();
        kafka.send(TOPIC, "agg-fail", envelope(id, "Fail", "agg-fail", 1)).get();

        awaitRecord(TOPIC + ".dlt", r -> r.value().contains(id.toString()));
        assertThat(count("select count(*) from inbox where event_id = ?", id)).isZero();
    }

    @Test
    void cleanupRemovesOnlyRowsPastRetention() {
        UUID oldPublished = UUID.randomUUID();
        UUID recentPublished = UUID.randomUUID();
        UUID oldUnpublished = UUID.randomUUID();
        insertOutbox(oldPublished, "now() - interval '8 days'", "now() - interval '8 days'");
        insertOutbox(recentPublished, "now() - interval '1 day'", "now() - interval '1 day'");
        insertOutbox(oldUnpublished, "now() - interval '8 days'", "null");
        UUID oldInbox = UUID.randomUUID();
        UUID recentInbox = UUID.randomUUID();
        jdbc.sql("insert into inbox (event_id, consumer, processed_at) values (?, 'c', now() - interval '31 days')")
                .param(oldInbox).update();
        jdbc.sql("insert into inbox (event_id, consumer, processed_at) values (?, 'c', now() - interval '29 days')")
                .param(recentInbox).update();

        cleanup.cleanup();

        assertThat(count("select count(*) from outbox where id = ?", oldPublished)).isZero();
        assertThat(count("select count(*) from outbox where id = ?", recentPublished)).isEqualTo(1);
        assertThat(count("select count(*) from outbox where id = ?", oldUnpublished)).isEqualTo(1);
        assertThat(count("select count(*) from inbox where event_id = ?", oldInbox)).isZero();
        assertThat(count("select count(*) from inbox where event_id = ?", recentInbox)).isEqualTo(1);
    }

    // ---- helpers ----

    private void insertOutbox(UUID id, String createdAt, String publishedAt) {
        jdbc.sql("insert into outbox (id, topic, event_key, event_type, payload, headers, created_at, published_at) "
                        + "values (?, 'cleanup-test.events', 'k', 'T', '{}', '{}', " + createdAt + ", " + publishedAt + ")")
                .param(id).update();
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(List.of(params)).query(Long.class).single();
    }

    private String envelope(UUID id, String type, String subject, long version) throws Exception {
        return json.writeValueAsString(Map.of("specversion", "1.0", "id", id.toString(), "source", "test-service",
                "type", type, "subject", subject, "aggregateversion", version, "time", Instant.now().toString(),
                "data", Map.of("n", 1)));
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, Predicate<ConsumerRecord<String, String>> match) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of(topic));
            AtomicReference<ConsumerRecord<String, String>> found = new AtomicReference<>();
            await().atMost(30, TimeUnit.SECONDS).pollInSameThread().until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (match.test(record)) found.set(record);
                }
                return found.get() != null;
            });
            return found.get();
        }
    }
}
