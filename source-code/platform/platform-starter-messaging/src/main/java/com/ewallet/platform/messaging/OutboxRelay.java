package com.ewallet.platform.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Đọc outbox, gửi lên Kafka, đánh dấu đã gửi. Giao <b>ít nhất một lần</b>: nếu commit lỗi sau khi gửi, event bị gửi lại
 * và consumer chặn trùng bằng inbox. Nhiều instance chạy song song nhờ {@code FOR UPDATE SKIP LOCKED}; thứ tự giữa các
 * batch không được đảm bảo, nên consumer của bản sao dữ liệu dùng {@code aggregateversion}.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private record Row(UUID id, String topic, String key, String type, String payload, String headers,
            Instant createdAt) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final int batchSize;
    private final long sendTimeoutSeconds;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
            ObjectMapper json, int batchSize, long sendTimeoutSeconds) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.batchSize = batchSize;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${ewallet.outbox.poll-interval:PT0.5S}")
    public void poll() {
        try {
            Integer sent;
            do {
                sent = tx.execute(status -> sendBatch());
            } while (sent != null && sent == batchSize);
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed; will retry on next poll", e);
        }
    }

    private int sendBatch() {
        List<Row> rows = jdbc.sql("select id, topic, event_key, event_type, payload::text, headers::text, created_at "
                        + "from outbox where published_at is null order by created_at, id limit ? "
                        + "for update skip locked")
                .param(batchSize)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getObject(7, OffsetDateTime.class).toInstant()))
                .list();
        List<UUID> sent = new ArrayList<>();
        for (Row row : rows) {
            try {
                kafka.send(row.topic(), row.key(), envelope(row)).get(sendTimeoutSeconds, TimeUnit.SECONDS);
                sent.add(row.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Dừng batch: các event sau không được vượt lên event lỗi (cùng key cần giữ thứ tự).
                log.warn("Cannot publish outbox event {} to {}; stopping this batch", row.id(), row.topic(), e);
                break;
            }
        }
        if (!sent.isEmpty()) {
            jdbc.sql("update outbox set published_at = now() where id in (:ids)").param("ids", sent).update();
        }
        return sent.size();
    }

    private String envelope(Row row) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode headers = json.readTree(row.headers());
        ObjectNode event = json.createObjectNode();
        event.put("specversion", "1.0");
        event.put("id", row.id().toString());
        event.put("source", headers.path("source").asText());
        event.put("type", row.type());
        event.put("time", row.createdAt().toString());
        event.put("subject", row.key());
        event.put("aggregateversion", headers.path("aggregateversion").asLong());
        event.put("datacontenttype", "application/json");
        event.set("data", json.readTree(row.payload()));
        return json.writeValueAsString(event);
    }
}
