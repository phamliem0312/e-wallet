package com.ewallet.platform.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dọn outbox đã publish quá 7 ngày và inbox quá 30 ngày (dài hơn retention của Kafka), theo data model.
 * Chạy nhiều instance cùng lúc vô hại (DELETE idempotent) nên không cần ShedLock.
 */
public class OutboxCleanup {

    private final JdbcClient jdbc;
    private final int outboxDays;
    private final int inboxDays;

    public OutboxCleanup(JdbcClient jdbc, int outboxDays, int inboxDays) {
        this.jdbc = jdbc;
        this.outboxDays = outboxDays;
        this.inboxDays = inboxDays;
    }

    @Scheduled(cron = "${ewallet.outbox.cleanup-cron:0 0 3 * * *}")
    public void cleanup() {
        jdbc.sql("delete from outbox where published_at < now() - make_interval(days => ?)")
                .param(outboxDays).update();
        jdbc.sql("delete from inbox where processed_at < now() - make_interval(days => ?)")
                .param(inboxDays).update();
    }
}
