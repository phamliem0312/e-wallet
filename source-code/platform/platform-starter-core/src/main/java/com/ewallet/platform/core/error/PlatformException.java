package com.ewallet.platform.core.error;

import java.util.List;
import java.util.Map;

/** Lỗi nghiệp vụ có mã ổn định; starter web đổi nó thành Problem (RFC 9457). */
public class PlatformException extends RuntimeException {

    public record FieldError(String field, String code, String message) {}

    private final ErrorCode errorCode;
    private final Map<String, Object> params;
    private final List<FieldError> errors;

    public PlatformException(ErrorCode errorCode) {
        this(errorCode, Map.of(), List.of());
    }

    /** {@code params}: dữ liệu để frontend dựng thông điệp (ví dụ {@code remaining}). Không đưa dữ liệu nhạy cảm vào. */
    public PlatformException(ErrorCode errorCode, Map<String, Object> params) {
        this(errorCode, params, List.of());
    }

    public PlatformException(ErrorCode errorCode, Map<String, Object> params, List<FieldError> errors) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.params = Map.copyOf(params);
        this.errors = List.copyOf(errors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> params() {
        return params;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
