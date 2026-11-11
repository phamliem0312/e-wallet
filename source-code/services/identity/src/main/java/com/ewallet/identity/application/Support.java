package com.ewallet.identity.application;

import com.ewallet.identity.domain.IdentityErrorCode;
import com.ewallet.identity.domain.PhoneNumbers;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import com.ewallet.platform.core.error.PlatformException.FieldError;
import com.ewallet.platform.messaging.OutboxService;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Kiểu và hàm dùng chung của tầng application. */
public final class Support {
    private Support() {}

    /** Thông tin của yêu cầu, cho audit, bản ghi đồng ý và giới hạn tần suất. */
    public record Client(String ip, String userAgent) {
        public Client {
            userAgent = userAgent == null ? "" : userAgent.substring(0, Math.min(256, userAgent.length()));
        }
    }

    /** Phiên trả cho BFF; khớp schema AuthSession của hợp đồng. */
    public record AuthSession(String accessToken, String refreshToken, Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt) {}

    static PlatformException validation(String field, String code, String message) {
        return new PlatformException(CommonErrorCode.VALIDATION_ERROR, Map.of(),
                List.of(new FieldError(field, code, message)));
    }

    static PlatformException error(IdentityErrorCode code) {
        return new PlatformException(code);
    }

    static String normalizePhone(String field, String phone) {
        return PhoneNumbers.normalize(phone)
                .orElseThrow(() -> validation(field, "INVALID_FORMAT", "must be a Vietnamese phone number"));
    }

    static void count(String name, String... tags) {
        Metrics.counter(name, tags).increment();
    }

    /**
     * Ghi audit qua outbox (EV-08), topic {@code audit.records} theo api doc §6. Tham gia transaction đang chạy,
     * hoặc tự mở transaction. {@code action} và {@code target} chưa có danh mục chuẩn (OQ-052).
     * Không đưa mật khẩu, OTP, token hay dấu vân tay vào {@code detail}.
     */
    @Component
    public static class Audit {
        private final OutboxService outbox;
        private final TransactionTemplate tx;

        Audit(OutboxService outbox, TransactionTemplate tx) {
            this.outbox = outbox;
            this.tx = tx;
        }

        public void record(String action, UUID customerId, String target, Client client, Map<String, ?> detail) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("actor", customerId == null ? "anonymous" : "customer:" + customerId);
            data.put("action", action);
            data.put("target", target);
            data.put("ip", client.ip());
            data.put("userAgent", client.userAgent());
            if (MDC.get("traceId") != null) data.put("traceId", MDC.get("traceId"));
            if (!detail.isEmpty()) data.put("after", detail);
            String key = customerId == null ? "anonymous" : customerId.toString();
            tx.executeWithoutResult(status -> outbox.publish("audit.records", key, "AuditRecorded", 0, data));
        }
    }
}
