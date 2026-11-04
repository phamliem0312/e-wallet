package com.ewallet.platform.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Số tiền VND, số nguyên đơn vị đồng, không âm. Không có kiểu nào khác để biểu diễn tiền (không float/double).
 * JSON là một số nguyên trần, khớp {@code MoneyVnd} trong contracts/openapi/common.yaml.
 */
public record Money(@JsonValue long amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amount < 0) throw new IllegalArgumentException("Money must not be negative: " + amount);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Money of(long amount) {
        return new Money(amount);
    }

    /** Tràn long là lỗi lập trình, ném ArithmeticException thay vì cuộn vòng. */
    public Money plus(Money other) {
        return new Money(Math.addExact(amount, other.amount));
    }

    /** Kết quả âm ném IllegalArgumentException: số dư không được âm. */
    public Money minus(Money other) {
        return new Money(Math.subtractExact(amount, other.amount));
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(amount, other.amount);
    }
}
