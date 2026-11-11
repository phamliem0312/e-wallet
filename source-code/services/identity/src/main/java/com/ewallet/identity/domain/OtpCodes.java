package com.ewallet.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** OTP 6 chữ số (F-ACC-01.FR-09) và các hàm băm dùng trong identity. */
public final class OtpCodes {
    private OtpCodes() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** HMAC có khoá và có id của challenge: mã chỉ có 10^6 giá trị nên băm không khoá dò ngược được ngay. */
    public static byte[] hash(byte[] hmacKey, UUID challengeId, String code) {
        return hmac(hmacKey, challengeId + ":" + code);
    }

    public static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** So sánh thời gian hằng định. */
    public static boolean matches(byte[] expected, byte[] actual) {
        return MessageDigest.isEqual(expected, actual);
    }
}
