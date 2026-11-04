package com.ewallet.platform.web;

import java.util.UUID;

/**
 * Giá trị header {@code Idempotency-Key} đã kiểm tra. Khai báo làm tham số controller để bắt buộc có header;
 * việc tra khoá trong DB của service là bước đầu tiên của use case, trước mọi kiểm tra khác.
 */
public record IdempotencyKey(UUID value) {}
