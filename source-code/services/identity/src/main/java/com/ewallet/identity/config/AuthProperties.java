package com.ewallet.identity.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tham số {@code auth.*} của F-ACC-01 (SRS §3.3). Trừ {@code access.ttl} (design §10), các giá trị mặc định trong
 * application.yml là số minh hoạ của SRS, CHƯA được chốt (OQ-035, OQ-036).
 */
@ConfigurationProperties("auth")
public record AuthProperties(Otp otp, Login login, Access access, Refresh refresh, Session session,
        Password password, Terms terms, Device device, Jwt jwt, String hmacKey, Map<String, RateLimit> ratelimit) {

    public record Otp(Duration ttl, int maxAttempts, Duration resendAfter, int maxSends, Duration sendWindow) {}

    public record Login(int maxFailures, Duration lockDuration) {}

    public record Access(Duration ttl) {}

    /** {@code reuseGrace} = 0 là nghiêm ngặt (OQ-037). */
    public record Refresh(Duration ttl, Duration reuseGrace) {}

    public record Session(Duration idleTimeout) {}

    public record Password(int minLength, int maxLength) {}

    public record Terms(String currentVersion) {}

    /** {@code trustTtl} null = thiết bị tin cậy không hết hạn (OQ-032). */
    public record Device(Duration trustTtl) {}

    /** {@code privateJwk}: JWK (JSON) của khoá EC P-256, gồm phần riêng tư. Trống thì sinh khoá tạm (bị cấm ở prod). */
    public record Jwt(String issuer, String privateJwk) {}

    /** Khoá map dạng {@code <thao tác>-<phạm vi>}, ví dụ {@code login-ip}. Không khai báo = không giới hạn. */
    public record RateLimit(int limit, Duration window) {}
}
