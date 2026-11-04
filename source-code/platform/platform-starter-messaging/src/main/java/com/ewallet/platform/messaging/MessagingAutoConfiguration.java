package com.ewallet.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;

@AutoConfiguration(after = { DataSourceAutoConfiguration.class, JdbcClientAutoConfiguration.class,
        TransactionAutoConfiguration.class, KafkaAutoConfiguration.class })
@EnableScheduling
public class MessagingAutoConfiguration {

    @Bean
    OutboxService outboxService(JdbcClient jdbc, ObjectMapper json,
            @Value("${spring.application.name:unknown}") String source) {
        return new OutboxService(jdbc, json, source);
    }

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, TransactionTemplate tx, KafkaTemplate<String, String> kafka,
            ObjectMapper json, @Value("${ewallet.outbox.batch-size:100}") int batchSize,
            @Value("${ewallet.outbox.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        return new OutboxRelay(jdbc, tx, kafka, json, batchSize, sendTimeoutSeconds);
    }

    @Bean
    OutboxCleanup outboxCleanup(JdbcClient jdbc, @Value("${ewallet.outbox.retention-days:7}") int outboxDays,
            @Value("${ewallet.inbox.retention-days:30}") int inboxDays) {
        return new OutboxCleanup(jdbc, outboxDays, inboxDays);
    }

    @Bean
    IdempotentEventProcessor idempotentEventProcessor(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper json) {
        return new IdempotentEventProcessor(jdbc, tx, json);
    }

    /**
     * Thử lại {@code retry-attempts} lần rồi đẩy sang {@code <topic>.dlt}. Event sai định dạng (IllegalArgumentException)
     * đi thẳng vào DLT. Spring Boot tự gắn bean {@link CommonErrorHandler} này vào mọi {@code @KafkaListener}.
     * Cần broker cho phép tạo topic {@code .dlt} (hoặc tạo sẵn).
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafka,
            @Value("${ewallet.messaging.retry-interval-ms:1000}") long intervalMs,
            @Value("${ewallet.messaging.retry-attempts:3}") long attempts) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, e) -> new TopicPartition(record.topic() + ".dlt", -1));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervalMs, attempts));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
