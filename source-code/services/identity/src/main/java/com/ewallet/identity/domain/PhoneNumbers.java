package com.ewallet.identity.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/** F-ACC-01.BR-01: chỉ SĐT Việt Nam, chuẩn hoá về E.164. Khoảng trắng/dấu chấm chưa được nhận (OQ-016). */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    /** Cùng pattern với schema PhoneNumber của hợp đồng. */
    public static final String PATTERN = "^(0[0-9]{9}|\\+84[0-9]{9})$";
    private static final Pattern VALID = Pattern.compile(PATTERN);

    public static Optional<String> normalize(String input) {
        if (input == null || !VALID.matcher(input).matches()) return Optional.empty();
        return Optional.of(input.startsWith("0") ? "+84" + input.substring(1) : input);
    }

    /** "+84900000001" → "0900 *** 001" (dạng của ví dụ trong hợp đồng). */
    public static String mask(String e164) {
        String local = "0" + e164.substring(3);
        return local.substring(0, 4) + " *** " + local.substring(7);
    }

    /** Dạng "0xxxxxxxxx" của một SĐT E.164, để so với mật khẩu (BR-02). */
    public static String local(String e164) {
        return "0" + e164.substring(3);
    }
}
