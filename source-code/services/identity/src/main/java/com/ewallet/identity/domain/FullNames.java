package com.ewallet.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

public final class FullNames {
    private FullNames() {}

    /** F-ACC-01.BR-03: bỏ khoảng trắng đầu cuối, gộp khoảng trắng, 1–128 ký tự, không ký tự điều khiển. */
    public static Optional<String> normalize(String input) {
        if (input == null || input.chars().anyMatch(Character::isISOControl)) return Optional.empty();
        String name = input.strip().replaceAll("\\s+", " ");
        int length = name.codePointCount(0, name.length());
        return length >= 1 && length <= 128 ? Optional.of(name) : Optional.empty();
    }

    /**
     * Tên hiển thị đã che cho event (EV-01, EV-02). Quy tắc che CHƯA được chốt (OQ-009, OQ-047); đây là
     * [Giả định] suy từ ví dụ duy nhất của data model ("NGUYEN V*** A"): in hoa không dấu, giữ từ đầu,
     * các từ giữa còn chữ cái đầu. Tên một hoặc hai từ thì che từ cuối, để không bao giờ lộ đủ họ tên.
     * Đổi quy tắc thì chỉ sửa hàm này.
     */
    public static String displayMasked(String normalizedName) {
        String ascii = Normalizer.normalize(normalizedName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT);
        String[] words = ascii.split(" ");
        if (words.length == 1) return initial(words[0]);
        if (words.length == 2) return words[0] + " " + initial(words[1]);
        StringBuilder out = new StringBuilder(words[0]);
        for (int i = 1; i < words.length - 1; i++) out.append(' ').append(initial(words[i]));
        return out.append(' ').append(words[words.length - 1]).toString();
    }

    private static String initial(String word) {
        return word.substring(0, word.offsetByCodePoints(0, 1)) + "***";
    }
}
