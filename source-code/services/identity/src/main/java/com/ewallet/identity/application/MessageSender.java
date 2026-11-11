package com.ewallet.identity.application;

import com.ewallet.platform.core.Sensitive;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng gửi tin của identity. Adapter thật (gọi `notification`, hợp đồng contracts/openapi/notification/internal.yaml)
 * sẽ có ở mốc M5; hiện chỉ có bản giả trong bộ nhớ.
 */
public interface MessageSender {

    /**
     * Gửi OTP (EV-03). Idempotent theo {@code challengeId}.
     * @throws RuntimeException khi không bàn giao được: bên gọi coi là OTP_SEND_FAILED (FR-14)
     */
    void sendOtp(UUID challengeId, String phone, Sensitive<String> code, String purpose, Instant expiresAt);

    /**
     * Gửi thông báo bảo mật không chứa mã (EV-04..EV-07). Kênh và cách gửi chưa chốt (OQ-040, OQ-051).
     * {@code params} không được chứa mật khẩu, OTP hay dấu vân tay thiết bị (BR-09).
     */
    void sendNotice(String type, String phone, Map<String, String> params);
}
