package com.ewallet.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewallet.identity.domain.DeviceTrust;
import com.ewallet.identity.domain.FullNames;
import com.ewallet.identity.domain.OtpCodes;
import com.ewallet.identity.domain.PasswordPolicy;
import com.ewallet.identity.domain.PhoneNumbers;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Rule thuần của F-ACC-01, không cần DB hay mạng. ID theo docs/test-cases/F-ACC-01-dang-ky-dang-nhap.md. */
class DomainRulesTest {

    @ParameterizedTest
    @DisplayName("TC-ACC-001 a,b: SĐT hợp lệ được chuẩn hoá về E.164")
    @ValueSource(strings = { "0900000001", "+84900000001" })
    void phoneIsNormalized(String input) {
        assertThat(PhoneNumbers.normalize(input)).contains("+84900000001");
    }

    @ParameterizedTest
    @DisplayName("TC-ACC-001 c-h: SĐT sai định dạng bị từ chối")
    @NullAndEmptySource
    @ValueSource(strings = { "090000001", "09000000011", "+8490000000a", "84900000001", "0900 000 001", "+84 900000001" })
    void malformedPhoneIsRejected(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("TC-ACC-002: chính sách mật khẩu (độ dài theo ký tự Unicode, khác SĐT)")
    @CsvSource(delimiter = '|', value = {
            "1234567|false",          // a: 7 ký tự
            "12345678|true",          // b: 8 ký tự
            "0900000001|false",       // e: bằng SĐT dạng 0
            "+84900000001|false",     // f: bằng SĐT dạng +84
            "mậtkhẩuă|true",          // g: 8 ký tự có dấu
            "'        '|true",        // h: 8 khoảng trắng, không trim
    })
    void passwordPolicy(String password, boolean accepted) {
        assertThat(PasswordPolicy.accepts(password, "+84900000001", 8, 128)).isEqualTo(accepted);
    }

    @Test
    @DisplayName("TC-ACC-002 c,d: biên trên 128/129 ký tự; null bị từ chối")
    void passwordUpperBound() {
        assertThat(PasswordPolicy.accepts("a".repeat(128), "+84900000001", 8, 128)).isTrue();
        assertThat(PasswordPolicy.accepts("a".repeat(129), "+84900000001", 8, 128)).isFalse();
        assertThat(PasswordPolicy.accepts(null, null, 8, 128)).isFalse();
        // chưa biết SĐT (challenge không tồn tại) thì chỉ kiểm độ dài
        assertThat(PasswordPolicy.accepts("0900000001", null, 8, 128)).isTrue();
    }

    @Test
    @DisplayName("TC-ACC-003: chuẩn hoá họ tên")
    void fullNameNormalization() {
        assertThat(FullNames.normalize("  Nguyễn   Văn A ")).contains("Nguyễn Văn A");
        assertThat(FullNames.normalize("")).isEmpty();
        assertThat(FullNames.normalize("   ")).isEmpty();
        assertThat(FullNames.normalize(null)).isEmpty();
        assertThat(FullNames.normalize("a".repeat(128))).isPresent();
        assertThat(FullNames.normalize("a".repeat(129))).isEmpty();
        assertThat(FullNames.normalize("A\nB")).isEmpty();
        assertThat(FullNames.normalize("A\tB")).isEmpty();
        assertThat(FullNames.normalize("A\u0007B")).isEmpty();
    }

    @Test
    @DisplayName("TC-ACC-004: che nơi nhận OTP và tên hiển thị (quy tắc che tên là giả định, OQ-009/047)")
    void masking() {
        assertThat(PhoneNumbers.mask("+84900000001")).isEqualTo("0900 *** 001");
        assertThat(FullNames.displayMasked("Nguyễn Văn A")).isEqualTo("NGUYEN V*** A");
        assertThat(FullNames.displayMasked("Lê Thị Thu Hà")).isEqualTo("LE T*** T*** HA");
        assertThat(FullNames.displayMasked("Trần Bình")).isEqualTo("TRAN B***").doesNotContain("BINH");
        assertThat(FullNames.displayMasked("Bình")).isEqualTo("B***");
        assertThat(FullNames.displayMasked("Đỗ Đức Đạt")).isEqualTo("DO D*** DAT");
    }

    @Test
    @DisplayName("TC-ACC-005: xác định thiết bị mới theo trusted_at và trust_ttl")
    void deviceTrust() {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        Duration ttl = Duration.ofDays(90);
        assertThat(DeviceTrust.isNew(null, now, null)).isTrue();                                  // b: đã thu hồi
        assertThat(DeviceTrust.isNew(now.minus(Duration.ofDays(400)), now, null)).isFalse();      // c: không TTL
        assertThat(DeviceTrust.isNew(now.minus(Duration.ofDays(89)), now, ttl)).isFalse();        // d
        assertThat(DeviceTrust.isNew(now.minus(Duration.ofDays(91)), now, ttl)).isTrue();         // e
    }

    @Test
    @DisplayName("TC-ACC-006: OTP luôn 6 chữ số; băm gắn với id của challenge")
    void otpCodes() {
        for (int i = 0; i < 10_000; i++) assertThat(OtpCodes.generate()).matches("[0-9]{6}");
        byte[] key = new byte[32];
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(OtpCodes.hash(key, first, "123456")).hasSize(32).isEqualTo(OtpCodes.hash(key, first, "123456"));
        assertThat(OtpCodes.hash(key, first, "123456")).isNotEqualTo(OtpCodes.hash(key, second, "123456"));
        assertThat(OtpCodes.hash(key, first, "123456")).isNotEqualTo(OtpCodes.hash(new byte[] { 1 }, first, "123456"));
        assertThat(OtpCodes.matches(OtpCodes.hash(key, first, "123456"), OtpCodes.hash(key, first, "123457"))).isFalse();
    }
}
