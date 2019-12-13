package io.netty.channel.pool;

import io.netty.util.internal.ThreadLocalRandom;

final class ChannelPoolTestUtils {
    private static final String LOCAL_ADDR_ID = "test.id";

    private ChannelPoolTestUtils() {
    }

    static String getLocalAddrId() {
        return LOCAL_ADDR_ID + ThreadLocalRandom.current().nextInt();
    }
}
