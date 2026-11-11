package com.ewallet.identity.domain;

/** F-ACC-01.BR-02: chỉ kiểm độ dài (theo ký tự Unicode) và không bằng chính SĐT. Không trim, không quy tắc thành phần. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    /** @param phoneE164 null khi chưa biết SĐT của khách (challenge không tồn tại) */
    public static boolean accepts(String password, String phoneE164, int minLength, int maxLength) {
        if (password == null) return false;
        int length = password.codePointCount(0, password.length());
        if (length < minLength || length > maxLength) return false;
        return phoneE164 == null
                || !(password.equals(phoneE164) || password.equals(PhoneNumbers.local(phoneE164)));
    }
}
