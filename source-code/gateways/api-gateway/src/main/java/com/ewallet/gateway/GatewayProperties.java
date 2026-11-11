package com.ewallet.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình {@code ewallet.gateway.*}; số liệu mặc định trong application.yml là giả định (OQ-036). */
@ConfigurationProperties("ewallet.gateway")
public record GatewayProperties(String identityUri, String issuer, long maxRequestBytes, int maxTrustedIndex,
        RateLimit rateLimit) {

    public record RateLimit(int limit, Duration window) {}
}
