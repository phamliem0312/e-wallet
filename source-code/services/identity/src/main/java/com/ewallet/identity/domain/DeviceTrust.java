package com.ewallet.identity.domain;

import java.time.Duration;
import java.time.Instant;

public final class DeviceTrust {
    private DeviceTrust() {}

    /**
     * F-ACC-01.BR-04: thiết bị là "mới" khi chưa được tin cậy, đã bị thu hồi ({@code trustedAt} null),
     * hoặc quá {@code trustTtl} (null = không hết hạn).
     */
    public static boolean isNew(Instant trustedAt, Instant now, Duration trustTtl) {
        if (trustedAt == null) return true;
        return trustTtl != null && trustedAt.plus(trustTtl).isBefore(now);
    }
}
