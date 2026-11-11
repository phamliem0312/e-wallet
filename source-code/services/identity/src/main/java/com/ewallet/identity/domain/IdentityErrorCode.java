package com.ewallet.identity.domain;

import com.ewallet.platform.core.error.ErrorCode;

/** Mã lỗi của F-ACC-01 (SRS §6); phải có trong ErrorCode của contracts/openapi/common.yaml. */
public enum IdentityErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(401),
    ACCOUNT_TEMP_LOCKED(403),
    ACCOUNT_LOCKED(403),
    KYC_REQUIRED(403),
    PHONE_ALREADY_REGISTERED(409),
    CHALLENGE_REQUIRED(422),
    CHALLENGE_INVALID(422),
    CONSENT_REQUIRED(422),
    OTP_SEND_FAILED(503);

    private final int httpStatus;

    IdentityErrorCode(int httpStatus) {
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
