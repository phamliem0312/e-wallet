package com.ewallet.gateway;

import com.ewallet.platform.core.error.CommonErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Lỗi do chính gateway trả, cùng dạng Problem với các service (common.yaml: code, traceId). */
final class Problems {
    private Problems() {}

    static Mono<Void> write(ServerWebExchange exchange, ObjectMapper json, CommonErrorCode code, Long retryAfter) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) return Mono.empty();
        String kebab = code.code().toLowerCase(Locale.ROOT).replace('_', '-');
        String words = code.code().toLowerCase(Locale.ROOT).replace('_', ' ');
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ewallet/errors/" + kebab);
        body.put("title", Character.toUpperCase(words.charAt(0)) + words.substring(1));
        body.put("status", code.httpStatus());
        body.put("code", code.code());
        body.put("traceId", traceId(exchange));
        if (retryAfter != null) {
            body.put("params", Map.of("retryAfterSeconds", retryAfter));
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        response.setStatusCode(HttpStatusCode.valueOf(code.httpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        try {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(json.writeValueAsBytes(body))));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    private static String traceId(ServerWebExchange exchange) {
        String traceparent = exchange.getRequest().getHeaders().getFirst("traceparent");
        if (traceparent != null) {
            String[] parts = traceparent.split("-");
            if (parts.length == 4 && parts[1].length() == 32) return parts[1];
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
