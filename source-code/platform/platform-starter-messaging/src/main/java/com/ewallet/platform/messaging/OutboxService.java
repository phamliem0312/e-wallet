package com.ewallet.platform.messaging;

import com.ewallet.platform.core.Ids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Ghi event vào bảng outbox trong cùng DB transaction với thay đổi nghiệp vụ; {@link OutboxRelay} gửi lên Kafka. */
public class OutboxService {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final String source;

    public OutboxService(JdbcClient jdbc, ObjectMapper json, String source) {
        this.jdbc = jdbc;
        this.json = json;
        this.source = source;
    }

    /**
     * @param topic  {@code <service>.events}
     * @param key    id của aggregate; dùng làm key Kafka để các event của một aggregate nằm cùng partition
     * @param data   nội dung theo JSON Schema trong contracts/events; không chứa OTP, CCCD, số tài khoản dạng rõ
     * @return id của event (= CloudEvents id)
     * @throws IllegalStateException nếu không có transaction đang chạy: event mà không cùng transaction với thay đổi
     *                               nghiệp vụ là dual-write
     */
    public UUID publish(String topic, String key, String type, long aggregateVersion, Object data) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxService.publish must run inside the business transaction");
        }
        UUID id = Ids.uuidV7();
        jdbc.sql("insert into outbox (id, topic, event_key, event_type, payload, headers) "
                        + "values (?, ?, ?, ?, ?::jsonb, ?::jsonb)")
                .params(List.of(id, topic, key, type, write(data),
                        write(Map.of("source", source, "aggregateversion", aggregateVersion))))
                .update();
        return id;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize outbox payload", e);
        }
    }
}
