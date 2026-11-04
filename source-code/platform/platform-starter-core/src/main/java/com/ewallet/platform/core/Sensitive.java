package com.ewallet.platform.core;

/**
 * Bọc giá trị không được xuất hiện trong log (OTP, số CCCD, số tài khoản dạng rõ).
 * {@code toString()} luôn là {@code ***}; muốn lấy giá trị phải gọi {@link #value()} một cách tường minh.
 */
public record Sensitive<T>(T value) {
    @Override
    public String toString() {
        return "***";
    }
}
