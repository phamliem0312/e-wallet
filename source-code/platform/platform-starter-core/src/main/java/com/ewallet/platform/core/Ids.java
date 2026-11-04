package com.ewallet.platform.core;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class Ids {
    private Ids() {}

    /** UUIDv7: 48 bit mili-giây đứng đầu nên sắp xếp được theo thời gian (tốt cho index B-tree). */
    public static UUID uuidV7() {
        var random = ThreadLocalRandom.current();
        long msb = (System.currentTimeMillis() << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
