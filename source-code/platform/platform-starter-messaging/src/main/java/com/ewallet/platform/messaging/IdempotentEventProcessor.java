package com.ewallet.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Xử lý event đúng một lần về mặt tác dụng: ghi inbox và chạy handler trong cùng một transaction.
 * Handler ném lỗi thì cả hai rollback, nên lần giao lại vẫn được xử lý; giao trùng thì bị bỏ qua.
 */
public class IdempotentEventProcessor {

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public IdempotentEventProcessor(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.json = json;
    }

    /**
     * @param consumer tên handler, duy nhất trong service (một event có thể có nhiều handler)
     * @return {@code true} nếu event được xử lý lần này, {@code false} nếu đã xử lý trước đó
     * @throws IllegalArgumentException nếu giá trị không phải CloudEvent hợp lệ (đi thẳng vào DLT, không thử lại)
     */
    public boolean process(String value, String consumer, Consumer<CloudEvent> handler) {
        CloudEvent event = CloudEvent.parse(json, value);
        return Boolean.TRUE.equals(tx.execute(status -> {
            if (!firstTime(event.id(), consumer)) return false;
            handler.accept(event);
            return true;
        }));
    }

    private boolean firstTime(UUID eventId, String consumer) {
        return jdbc.sql("insert into inbox (event_id, consumer) values (?, ?) on conflict do nothing")
                .params(List.of(eventId, consumer)).update() == 1;
    }
}
