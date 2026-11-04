package com.ewallet.platform.core.error;

/** Mã lỗi không gắn với nghiệp vụ nào; mọi service dùng chung. Phải có trong ErrorCode của common.yaml. */
public enum CommonErrorCode implements ErrorCode {
    VALIDATION_ERROR(400),
    IDEMPOTENCY_KEY_REQUIRED(400),
    IDEMPOTENCY_KEY_REUSED(409),
    UNAUTHENTICATED(401),
    TOKEN_EXPIRED(401),
    FORBIDDEN(403),
    NOT_FOUND(404),
    STATE_CONFLICT(409),
    RATE_LIMITED(429),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    CommonErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
