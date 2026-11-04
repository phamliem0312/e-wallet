package com.ewallet.platform.core.error;

/**
 * Mã lỗi nghiệp vụ ổn định, trả cho client ở trường {@code code} của Problem
 * (xem {@code ErrorCode} trong contracts/openapi/common.yaml). Mỗi service khai báo enum của mình implement interface này.
 */
public interface ErrorCode {
    String code();

    int httpStatus();
}
