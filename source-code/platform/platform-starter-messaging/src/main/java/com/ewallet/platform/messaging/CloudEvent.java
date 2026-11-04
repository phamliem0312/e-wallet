package com.ewallet.platform.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

/** Envelope CloudEvents (structured mode) như API doc §6: giá trị của record Kafka là JSON này. */
public record CloudEvent(UUID id, String source, String type, String subject, long aggregateVersion, Instant time,
        JsonNode data) {

    /** Sai định dạng ném IllegalArgumentException: không thử lại có ích gì, record đi thẳng vào DLT. */
    public static CloudEvent parse(ObjectMapper json, String value) {
        try {
            JsonNode n = json.readTree(value);
            if (n == null || !n.isObject()) throw new IllegalArgumentException("CloudEvent must be a JSON object");
            return new CloudEvent(
                    UUID.fromString(required(n, "id").asText()),
                    required(n, "source").asText(),
                    required(n, "type").asText(),
                    required(n, "subject").asText(),
                    required(n, "aggregateversion").asLong(),
                    Instant.parse(required(n, "time").asText()),
                    required(n, "data"));
        } catch (JsonProcessingException | java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Malformed CloudEvent", e);
        }
    }

    private static JsonNode required(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) throw new IllegalArgumentException("CloudEvent is missing '" + field + "'");
        return v;
    }
}
