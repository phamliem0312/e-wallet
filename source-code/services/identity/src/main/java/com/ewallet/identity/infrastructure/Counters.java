package com.ewallet.identity.infrastructure;

import com.ewallet.identity.config.AuthProperties;
import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.platform.core.error.CommonErrorCode;
import com.ewallet.platform.core.error.PlatformException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Bộ đếm trên Redis: giới hạn tần suất (FR-43), khoá gửi lại và trần gửi OTP (FR-12, FR-13), challenge giả.
 * Đặt ở Redis chứ không ở DB để SĐT có và không có tài khoản bị giới hạn giống hệt nhau (FR-02, FR-32).
 * Redis khởi động lại thì mất bộ đếm (OQ-046). Khoá Redis chỉ chứa giá trị băm, không chứa SĐT.
 */
@Component
public class Counters {

    private final StringRedisTemplate redis;
    private final AuthProperties props;

    public Counters(StringRedisTemplate redis, AuthProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * Giới hạn tần suất theo cấu hình {@code auth.ratelimit.<name>}; không cấu hình thì không giới hạn.
     * ponytail: cửa sổ cố định (cho phép tối đa 2 lần ngưỡng quanh ranh giới cửa sổ); cần chặt hơn thì đổi sang
     * cửa sổ trượt.
     */
    public void checkRate(String name, String subject) {
        AuthProperties.RateLimit limit = props.ratelimit() == null ? null : props.ratelimit().get(name);
        if (limit == null) return;
        String key = "rl:" + name + ":" + digest(subject);
        long count = increment(key, limit.window().toSeconds());
        if (count > limit.limit()) throw rateLimited(ttlSeconds(key));
    }

    /** FR-13 rồi FR-12: chặn nếu vượt trần gửi hoặc chưa tới lúc được gửi lại; nếu được thì đặt khoá gửi lại. */
    public void acquireSend(String purpose, String phone, byte[] payloadHash, Instant now) {
        String sends = redis.opsForValue().get(sendsKey(purpose, phone));
        if (sends != null && Long.parseLong(sends) >= props.otp().maxSends()) {
            throw rateLimited(ttlSeconds(sendsKey(purpose, phone)));
        }
        String key = resendKey(purpose, phone, payloadHash);
        String until = redis.opsForValue().get(key);
        if (until != null && Long.parseLong(until) > now.toEpochMilli()) {
            throw rateLimited((Long.parseLong(until) - now.toEpochMilli() + 999) / 1000);
        }
        // Giá trị là thời điểm được gửi lại (so bằng đồng hồ của ứng dụng); TTL chỉ để Redis tự dọn.
        redis.opsForValue().set(key, String.valueOf(now.plus(props.otp().resendAfter()).toEpochMilli()),
                props.otp().resendAfter());
    }

    /** Gửi thất bại: không tính vào trần và không khoá gửi lại (FR-14). */
    public void releaseSend(String purpose, String phone, byte[] payloadHash) {
        redis.delete(resendKey(purpose, phone, payloadHash));
    }

    public void recordSend(String purpose, String phone) {
        increment(sendsKey(purpose, phone), props.otp().sendWindow().toSeconds());
    }

    public Instant resendAfter(Instant now) {
        return now.plus(props.otp().resendAfter());
    }

    /** Challenge giả (SĐT đã đăng ký khi đăng ký; SĐT không có tài khoản khi quên mật khẩu): đếm lượt như thật. */
    public void registerDecoy(UUID challengeId) {
        redis.opsForValue().set("decoy:" + challengeId, "0", props.otp().ttl());
    }

    /** @return số lần đã nhập vào challenge giả; rỗng nếu không phải challenge giả, đã hết hạn hoặc hết lượt */
    public OptionalInt decoyAttempt(UUID challengeId) {
        String key = "decoy:" + challengeId;
        if (!Boolean.TRUE.equals(redis.hasKey(key))) return OptionalInt.empty();
        Long attempts = redis.opsForValue().increment(key);
        return attempts == null || attempts > props.otp().maxAttempts()
                ? OptionalInt.empty() : OptionalInt.of(attempts.intValue());
    }

    private long increment(String key, long windowSeconds) {
        Long count = redis.opsForValue().increment(key);
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        // Đặt hạn khi tạo khoá; cũng sửa trường hợp khoá bị mất hạn (tiến trình chết giữa INCR và EXPIRE).
        if (ttl == null || ttl < 0) redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        return count == null ? 0 : count;
    }

    private long ttlSeconds(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 1 ? 1 : ttl;
    }

    private static PlatformException rateLimited(long retryAfterSeconds) {
        return new PlatformException(CommonErrorCode.RATE_LIMITED,
                Map.of("retryAfterSeconds", Math.max(1, retryAfterSeconds)));
    }

    private static String sendsKey(String purpose, String phone) {
        return "otp:sends:" + purpose + ":" + digest(phone);
    }

    private static String resendKey(String purpose, String phone, byte[] payloadHash) {
        return "otp:resend:" + purpose + ":" + digest(phone)
                + (payloadHash == null ? "" : ":" + HexFormat.of().formatHex(payloadHash, 0, 8));
    }

    private static String digest(String value) {
        return HexFormat.of().formatHex(OtpCodes.sha256(value), 0, 16);
    }
}
